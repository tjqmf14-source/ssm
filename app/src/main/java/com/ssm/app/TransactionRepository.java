package com.ssm.app;

import android.content.Context;
import java.util.UUID;

public final class TransactionRepository {
    private final Context context;
    public TransactionRepository(Context c) { this.context = c.getApplicationContext(); }

    public boolean addParsed(Transaction tx) {
        Transaction finalTx = tx;
        try (TransactionDb db = new TransactionDb(context)) {
            String rule = db.findMerchantRule(tx.merchant);
            if (rule != null) {
                finalTx = new Transaction(tx.id, tx.transactionId, tx.sourceKey, tx.sourcePackage,
                        tx.merchant, tx.amount, tx.type, rule, tx.paymentMethod,
                        tx.occurredAt, tx.rawText, tx.manual);
            }
            if (db.insertOrIgnore(finalTx) <= 0) return false;
        }
        SyncCoordinator.enqueueAllTargets(context, finalTx);
        return true;
    }

    public Transaction createManual(long amount, String type, String merchant, String category, String paymentMethod) {
        long now = System.currentTimeMillis();
        String key = "manual:" + UUID.randomUUID();
        String id = TransactionId.create("manual", key, amount, type, now, merchant);
        Transaction tx = new Transaction(0L, id, key, "manual", merchant, amount, type,
                category, paymentMethod, now, "직접 입력", true);
        addParsed(tx);
        return tx;
    }

    public boolean correct(String transactionId, long amount, String type, String merchant,
                           String category, String paymentMethod, long occurredAt) {
        if (amount <= 0 || merchant == null || merchant.trim().isEmpty()) return false;
        if (!Transaction.TYPE_EXPENSE.equals(type) && !Transaction.TYPE_INCOME.equals(type)) return false;
        try (TransactionDb db = new TransactionDb(context)) {
            Transaction old = db.find(transactionId);
            if (old == null) return false;
            String fixedMerchant = merchant.trim();
            Transaction fixed = new Transaction(old.id, old.transactionId, old.sourceKey, old.sourcePackage,
                    fixedMerchant, amount, type, category, paymentMethod,
                    occurredAt, old.rawText, old.manual);
            if (!db.update(fixed)) return false;

            // Learn both aliases. If the parser supplied a noisy merchant name and the user
            // corrected it, the next identical notification still receives the chosen category.
            db.saveMerchantRule(old.merchant, category);
            db.saveMerchantRule(fixedMerchant, category);

            // Keep transaction_id/remote_id stable so Calendar and Notion update in place.
            db.resetSyncTargets(transactionId);
        }
        SyncCoordinator.schedule(context);
        return true;
    }

    public boolean delete(String transactionId) {
        boolean deleted;
        try (TransactionDb db = new TransactionDb(context)) { deleted = db.delete(transactionId); }
        if (deleted) SyncCoordinator.schedule(context);
        return deleted;
    }
}

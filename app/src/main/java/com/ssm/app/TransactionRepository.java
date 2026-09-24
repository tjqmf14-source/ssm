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

    public boolean correct(String transactionId, String merchant, String category, String paymentMethod) {
        try (TransactionDb db = new TransactionDb(context)) {
            Transaction old = db.find(transactionId);
            if (old == null) return false;
            Transaction fixed = new Transaction(old.id, old.transactionId, old.sourceKey, old.sourcePackage,
                    merchant, old.amount, old.type, category, paymentMethod,
                    old.occurredAt, old.rawText, old.manual);
            if (!db.update(fixed)) return false;
            db.saveMerchantRule(merchant, category);
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

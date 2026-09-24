package com.ssm.app;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class TransactionFlowTest {
    private Context context;

    @Before public void clear() {
        context = ApplicationProvider.getApplicationContext();
        try (TransactionDb db = new TransactionDb(context)) {
            db.getWritableDatabase().delete("delete_queue", null, null);
            db.getWritableDatabase().delete("sync_targets", null, null);
            db.getWritableDatabase().delete("transactions", null, null);
            db.getWritableDatabase().delete("merchant_rules", null, null);
        }
    }

    @Test public void duplicateTransactionIdCannotCreateSecondRow() {
        Transaction tx = transaction("tx_duplicate", "원래 상점");
        try (TransactionDb db = new TransactionDb(context)) {
            assertTrue(db.insertOrIgnore(tx) > 0);
            assertEquals(-1, db.insertOrIgnore(tx));
            assertEquals(1, db.getRecent(10).size());
        }
    }

    @Test public void missingIntegrationsKeepLocalTransactionAndTargetsPending() {
        TransactionRepository repo = new TransactionRepository(context);
        assertTrue(repo.addParsed(transaction("tx_offline", "원래 상점")));
        try (TransactionDb db = new TransactionDb(context)) {
            assertNotNull(db.find("tx_offline"));
            assertEquals(3, db.pendingSyncCount());
        }
        assertEquals(0, SyncCoordinator.runDue(context, 10));
        try (TransactionDb db = new TransactionDb(context)) {
            assertEquals(3, db.pendingSyncCount());
        }
    }

    @Test public void failedSyncWaitsForRetryAndCorrectionResetsDelay() {
        try (TransactionDb db = new TransactionDb(context)) {
            db.insertOrIgnore(transaction("tx_retry", "원래 상점"));
            db.ensureSyncTarget("tx_retry", NotionSync.TARGET);
            db.markSyncFailure("tx_retry", NotionSync.TARGET, "offline");
            assertEquals(0, db.getDueSyncItems(10).size());
            assertEquals(1, db.pendingSyncCount());
            db.resetSyncTargets("tx_retry");
            assertEquals(1, db.getDueSyncItems(10).size());
        }
    }

    @Test public void deletingSyncedTransactionQueuesRemoteDeletion() {
        try (TransactionDb db = new TransactionDb(context)) {
            db.insertOrIgnore(transaction("tx_delete", "원래 상점"));
            db.ensureSyncTarget("tx_delete", NotionSync.TARGET);
            db.markSyncSuccess("tx_delete", NotionSync.TARGET, "notion-page-1");
            assertTrue(db.delete("tx_delete"));
            assertNull(db.find("tx_delete"));
            assertEquals(1, db.pendingSyncCount());
            List<TransactionDb.DeleteItem> items = db.getDueDeleteItems(10);
            assertEquals(1, items.size());
            assertEquals("notion-page-1", items.get(0).remoteId);
        }
    }

    @Test public void correctionLearnsOriginalNotificationMerchant() {
        assertTrue(new TransactionRepository(context).addParsed(transaction("tx_rule", "원래 상점")));
        try (TransactionDb db = new TransactionDb(context)) {
            db.markSyncSuccess("tx_rule", NotionSync.TARGET, "notion-page-1");
        }
        assertTrue(new TransactionRepository(context).correct("tx_rule", "수정한 상점", "식비", "카드"));
        try (TransactionDb db = new TransactionDb(context)) {
            assertEquals("식비", db.findMerchantRule("원래 상점"));
            assertEquals("수정한 상점", db.find("tx_rule").merchant);
            assertEquals(3, db.pendingSyncCount());
        }
    }

    @Test public void manualCorrectionUpdatesAmountTypeAndTimeWithoutChangingIdentity() {
        TransactionRepository repo = new TransactionRepository(context);
        Transaction tx = repo.createManual(1200, Transaction.TYPE_EXPENSE, "마트", "생활", "카드");
        assertTrue(repo.correct(tx.transactionId, 2500, Transaction.TYPE_INCOME,
                "마트", "기타", "현금", 1_700_000_000_000L));
        try (TransactionDb db = new TransactionDb(context)) {
            Transaction updated = db.find(tx.transactionId);
            assertNotNull(updated);
            assertEquals(2500, updated.amount);
            assertEquals(Transaction.TYPE_INCOME, updated.type);
            assertEquals(1_700_000_000_000L, updated.occurredAt);
            assertEquals(3, db.pendingSyncCount());
        }
    }

    private static Transaction transaction(String id, String merchant) {
        return new Transaction(0, id, "notification-key", "test.bank", merchant, 1200,
                Transaction.TYPE_EXPENSE, "기타", "카드", 1_700_000_000_000L, "알림 원문", false);
    }
}

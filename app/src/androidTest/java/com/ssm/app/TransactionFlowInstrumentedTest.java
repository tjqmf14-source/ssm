package com.ssm.app;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TransactionFlowInstrumentedTest {
    private Context context;

    @Before public void resetDb() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("ssm.db");
    }

    private Transaction sample(String id, String merchant) {
        return new Transaction(
                0L, id, "notification-key", "com.example.card", merchant,
                12_300L, Transaction.TYPE_EXPENSE, "기타", "카드",
                1_700_000_000_000L, "12,300원 승인 " + merchant, false);
    }

    @Test public void duplicateParsedTransactionIsIgnoredAndQueuedOnce() {
        TransactionRepository repository = new TransactionRepository(context);
        Transaction tx = sample("tx_duplicate", "테스트상점");

        assertTrue(repository.addParsed(tx));
        assertFalse(repository.addParsed(tx));

        try (TransactionDb db = new TransactionDb(context)) {
            assertEquals(1, db.getRecent(20).size());
            assertEquals(3, db.pendingSyncCount());
        }
    }

    @Test public void manualTransactionPersistsWithoutExternalConnectivity() {
        TransactionRepository repository = new TransactionRepository(context);
        Transaction created = repository.createManual(
                9_900L, Transaction.TYPE_EXPENSE, "오프라인상점", "식비", "현금");

        try (TransactionDb db = new TransactionDb(context)) {
            Transaction saved = db.find(created.transactionId);
            assertNotNull(saved);
            assertTrue(saved.manual);
            assertEquals(9_900L, saved.amount);
            assertEquals(3, db.pendingSyncCount());
        }
    }

    @Test public void correctionUpdatesAllEditableFieldsLearnsAliasesAndResetsSync() {
        TransactionRepository repository = new TransactionRepository(context);
        Transaction original = sample("tx_edit", "STARBUCKS 부산");
        assertTrue(repository.addParsed(original));

        try (TransactionDb db = new TransactionDb(context)) {
            db.markSyncSuccess(original.transactionId, CalendarSync.TARGET_GOOGLE, "101");
            db.markSyncSuccess(original.transactionId, CalendarSync.TARGET_SAMSUNG, "202");
            db.markSyncSuccess(original.transactionId, NotionSync.TARGET, "page-303");
            assertEquals(0, db.pendingSyncCount());
        }

        long changedTime = original.occurredAt + 86_400_000L;
        assertTrue(repository.correct(
                original.transactionId,
                55_000L,
                Transaction.TYPE_INCOME,
                "스타벅스 부산대점",
                "카페",
                "삼성페이",
                changedTime));

        try (TransactionDb db = new TransactionDb(context)) {
            Transaction changed = db.find(original.transactionId);
            assertNotNull(changed);
            assertEquals(original.transactionId, changed.transactionId);
            assertEquals(55_000L, changed.amount);
            assertEquals(Transaction.TYPE_INCOME, changed.type);
            assertEquals("스타벅스 부산대점", changed.merchant);
            assertEquals("카페", changed.category);
            assertEquals("삼성페이", changed.paymentMethod);
            assertEquals(changedTime, changed.occurredAt);
            assertEquals("카페", db.findMerchantRule("STARBUCKS 부산"));
            assertEquals("카페", db.findMerchantRule("스타벅스 부산대점"));

            List<TransactionDb.SyncItem> due = db.getDueSyncItems(10);
            assertEquals(3, due.size());
            Map<String,String> remote = new HashMap<>();
            for (TransactionDb.SyncItem item : due) remote.put(item.target, item.remoteId);
            assertEquals("101", remote.get(CalendarSync.TARGET_GOOGLE));
            assertEquals("202", remote.get(CalendarSync.TARGET_SAMSUNG));
            assertEquals("page-303", remote.get(NotionSync.TARGET));
        }
    }

    @Test public void failedTargetRemainsPendingButIsBackedOff() {
        TransactionRepository repository = new TransactionRepository(context);
        Transaction tx = sample("tx_retry", "재시도상점");
        assertTrue(repository.addParsed(tx));

        try (TransactionDb db = new TransactionDb(context)) {
            assertEquals(3, db.getDueSyncItems(10).size());
            db.markSyncFailure(tx.transactionId, NotionSync.TARGET, "offline");

            List<TransactionDb.SyncItem> immediatelyDue = db.getDueSyncItems(10);
            assertEquals(2, immediatelyDue.size());
            for (TransactionDb.SyncItem item : immediatelyDue) {
                assertNotEquals(NotionSync.TARGET, item.target);
            }
            assertEquals(3, db.pendingSyncCount());
        }
    }

    @Test public void deleteQueuesEveryPreviouslySyncedRemoteItem() {
        TransactionRepository repository = new TransactionRepository(context);
        Transaction tx = sample("tx_delete", "삭제상점");
        assertTrue(repository.addParsed(tx));

        try (TransactionDb db = new TransactionDb(context)) {
            db.markSyncSuccess(tx.transactionId, CalendarSync.TARGET_GOOGLE, "11");
            db.markSyncSuccess(tx.transactionId, CalendarSync.TARGET_SAMSUNG, "22");
            db.markSyncSuccess(tx.transactionId, NotionSync.TARGET, "page-33");
        }

        assertTrue(repository.delete(tx.transactionId));

        try (TransactionDb db = new TransactionDb(context)) {
            assertNull(db.find(tx.transactionId));
            List<TransactionDb.DeleteItem> queued = db.getDueDeleteItems(10);
            assertEquals(3, queued.size());
            Map<String,String> remote = new HashMap<>();
            for (TransactionDb.DeleteItem item : queued) remote.put(item.target, item.remoteId);
            assertEquals("11", remote.get(CalendarSync.TARGET_GOOGLE));
            assertEquals("22", remote.get(CalendarSync.TARGET_SAMSUNG));
            assertEquals("page-33", remote.get(NotionSync.TARGET));
            assertEquals(3, db.pendingSyncCount());
        }
    }
}

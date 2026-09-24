package com.ssm.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TransactionDb extends SQLiteOpenHelper implements AutoCloseable {
    private static final String DB_NAME = "ssm.db";
    private static final int DB_VERSION = 3;

    public TransactionDb(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) { createSchema(db); }

    private static void createSchema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS transactions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "transaction_id TEXT NOT NULL UNIQUE," +
                "source_key TEXT NOT NULL," +
                "source_package TEXT NOT NULL," +
                "merchant TEXT NOT NULL," +
                "amount INTEGER NOT NULL," +
                "type TEXT NOT NULL," +
                "category TEXT NOT NULL," +
                "payment_method TEXT NOT NULL," +
                "occurred_at INTEGER NOT NULL," +
                "raw_text TEXT NOT NULL," +
                "manual INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_time ON transactions(occurred_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_type_time ON transactions(type, occurred_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS sync_targets (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "transaction_id TEXT NOT NULL," +
                "target TEXT NOT NULL," +
                "status TEXT NOT NULL DEFAULT 'pending'," +
                "remote_id TEXT," +
                "attempts INTEGER NOT NULL DEFAULT 0," +
                "last_error TEXT," +
                "next_retry_at INTEGER NOT NULL DEFAULT 0," +
                "updated_at INTEGER NOT NULL," +
                "UNIQUE(transaction_id, target))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_pending ON sync_targets(status, next_retry_at)");

        db.execSQL("CREATE TABLE IF NOT EXISTS delete_queue (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "transaction_id TEXT NOT NULL," +
                "target TEXT NOT NULL," +
                "remote_id TEXT NOT NULL," +
                "attempts INTEGER NOT NULL DEFAULT 0," +
                "last_error TEXT," +
                "next_retry_at INTEGER NOT NULL DEFAULT 0," +
                "updated_at INTEGER NOT NULL," +
                "UNIQUE(transaction_id, target))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_delete_pending ON delete_queue(next_retry_at)");

        db.execSQL("CREATE TABLE IF NOT EXISTS merchant_rules (" +
                "merchant_key TEXT PRIMARY KEY," +
                "category TEXT NOT NULL," +
                "updated_at INTEGER NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.beginTransaction();
            try {
                db.execSQL("ALTER TABLE transactions RENAME TO transactions_v1");
                createSchema(db);
                db.execSQL("INSERT OR IGNORE INTO transactions(" +
                        "transaction_id,source_key,source_package,merchant,amount,type,category,payment_method,occurred_at,raw_text,manual,created_at,updated_at) " +
                        "SELECT 'legacy_' || id,source_key,source_package,merchant,amount,type,'기타','기타',occurred_at,raw_text,0,created_at,created_at FROM transactions_v1");
                db.execSQL("DROP TABLE transactions_v1");
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        }
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS delete_queue (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "transaction_id TEXT NOT NULL," +
                    "target TEXT NOT NULL," +
                    "remote_id TEXT NOT NULL," +
                    "attempts INTEGER NOT NULL DEFAULT 0," +
                    "last_error TEXT," +
                    "next_retry_at INTEGER NOT NULL DEFAULT 0," +
                    "updated_at INTEGER NOT NULL," +
                    "UNIQUE(transaction_id, target))");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_delete_pending ON delete_queue(next_retry_at)");
        }
    }

    public long insertOrIgnore(Transaction tx) {
        long now = System.currentTimeMillis();
        ContentValues v = values(tx);
        v.put("created_at", now);
        v.put("updated_at", now);
        return getWritableDatabase().insertWithOnConflict("transactions", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public boolean update(Transaction tx) {
        ContentValues v = values(tx);
        v.put("updated_at", System.currentTimeMillis());
        return getWritableDatabase().update("transactions", v, "transaction_id=?", new String[]{tx.transactionId}) > 0;
    }

    public boolean delete(String transactionId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            try (Cursor c = db.query("sync_targets", new String[]{"target","remote_id"},
                    "transaction_id=? AND remote_id IS NOT NULL", new String[]{transactionId}, null, null, null)) {
                while (c.moveToNext()) {
                    String target = c.getString(0);
                    String remoteId = c.getString(1);
                    if (remoteId == null || remoteId.isBlank()) continue;
                    ContentValues q = new ContentValues();
                    q.put("transaction_id", transactionId);
                    q.put("target", target);
                    q.put("remote_id", remoteId);
                    q.put("attempts", 0);
                    q.put("next_retry_at", 0);
                    q.put("updated_at", System.currentTimeMillis());
                    db.insertWithOnConflict("delete_queue", null, q, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            db.delete("sync_targets", "transaction_id=?", new String[]{transactionId});
            int count = db.delete("transactions", "transaction_id=?", new String[]{transactionId});
            db.setTransactionSuccessful();
            return count > 0;
        } finally { db.endTransaction(); }
    }

    public void ensureSyncTarget(String transactionId, String target) {
        ContentValues v = new ContentValues();
        v.put("transaction_id", transactionId);
        v.put("target", target);
        v.put("status", "pending");
        v.put("attempts", 0);
        v.put("next_retry_at", 0);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("sync_targets", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void resetSyncTargets(String transactionId) {
        ContentValues v = new ContentValues();
        v.put("status", "pending");
        v.put("attempts", 0);
        v.put("next_retry_at", 0);
        v.putNull("last_error");
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("sync_targets", v, "transaction_id=?", new String[]{transactionId});
    }

    public void markSyncSuccess(String transactionId, String target, String remoteId) {
        ContentValues v = new ContentValues();
        v.put("status", "synced");
        v.put("remote_id", remoteId);
        v.putNull("last_error");
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("sync_targets", v, "transaction_id=? AND target=?", new String[]{transactionId, target});
    }

    public void markSyncFailure(String transactionId, String target, String error) {
        int attempts = getAttempts("sync_targets", transactionId, target) + 1;
        ContentValues v = failureValues(attempts, error);
        v.put("status", "pending");
        getWritableDatabase().update("sync_targets", v, "transaction_id=? AND target=?", new String[]{transactionId, target});
    }

    public void markDeleteSuccess(String transactionId, String target) {
        getWritableDatabase().delete("delete_queue", "transaction_id=? AND target=?", new String[]{transactionId, target});
    }

    public void markDeleteFailure(String transactionId, String target, String error) {
        int attempts = getAttempts("delete_queue", transactionId, target) + 1;
        ContentValues v = failureValues(attempts, error);
        getWritableDatabase().update("delete_queue", v, "transaction_id=? AND target=?", new String[]{transactionId, target});
    }

    public int pendingSyncCount() {
        int upserts;
        int deletes;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM sync_targets WHERE status!='synced'", null)) {
            upserts = c.moveToFirst() ? c.getInt(0) : 0;
        }
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM delete_queue", null)) {
            deletes = c.moveToFirst() ? c.getInt(0) : 0;
        }
        return upserts + deletes;
    }

    public long nextRetryAt() {
        long upserts;
        long deletes;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(MIN(next_retry_at),0) FROM sync_targets WHERE status!='synced'", null)) {
            upserts = c.moveToFirst() ? c.getLong(0) : 0L;
        }
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(MIN(next_retry_at),0) FROM delete_queue", null)) {
            deletes = c.moveToFirst() ? c.getLong(0) : 0L;
        }
        if (upserts <= 0) return deletes;
        if (deletes <= 0) return upserts;
        return Math.min(upserts, deletes);
    }

    public int pendingTargetCount(String target) {
        int upserts;
        int deletes;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM sync_targets WHERE status!='synced' AND target=?", new String[]{target})) {
            upserts = c.moveToFirst() ? c.getInt(0) : 0;
        }
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM delete_queue WHERE target=?", new String[]{target})) {
            deletes = c.moveToFirst() ? c.getInt(0) : 0;
        }
        return upserts + deletes;
    }

    public int pendingCalendarCount() {
        int upserts;
        int deletes;
        String[] args = {CalendarSync.TARGET_GOOGLE, CalendarSync.TARGET_SAMSUNG};
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM sync_targets WHERE status!='synced' AND target IN (?,?)", args)) {
            upserts = c.moveToFirst() ? c.getInt(0) : 0;
        }
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM delete_queue WHERE target IN (?,?)", args)) {
            deletes = c.moveToFirst() ? c.getInt(0) : 0;
        }
        return upserts + deletes;
    }

    public List<SyncItem> getDueSyncItems(int limit) {
        List<SyncItem> out = new ArrayList<>();
        String sql = "SELECT s.transaction_id,s.target,s.attempts,s.remote_id," +
                "t.id,t.source_key,t.source_package,t.merchant,t.amount,t.type,t.category,t.payment_method,t.occurred_at,t.raw_text,t.manual " +
                "FROM sync_targets s JOIN transactions t ON t.transaction_id=s.transaction_id " +
                "WHERE s.status!='synced' AND s.next_retry_at<=? ORDER BY s.updated_at ASC LIMIT ?";
        try (Cursor c = getReadableDatabase().rawQuery(sql,
                new String[]{String.valueOf(System.currentTimeMillis()), String.valueOf(Math.max(1, Math.min(limit, 100))) })) {
            while (c.moveToNext()) {
                Transaction tx = new Transaction(c.getLong(4), c.getString(0), c.getString(5), c.getString(6), c.getString(7),
                        c.getLong(8), c.getString(9), c.getString(10), c.getString(11), c.getLong(12), c.getString(13), c.getInt(14)==1);
                out.add(new SyncItem(tx, c.getString(1), c.getInt(2), c.isNull(3) ? null : c.getString(3)));
            }
        }
        return out;
    }

    public List<DeleteItem> getDueDeleteItems(int limit) {
        List<DeleteItem> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT transaction_id,target,remote_id,attempts FROM delete_queue WHERE next_retry_at<=? ORDER BY updated_at ASC LIMIT ?",
                new String[]{String.valueOf(System.currentTimeMillis()), String.valueOf(Math.max(1, Math.min(limit, 100))) })) {
            while (c.moveToNext()) out.add(new DeleteItem(c.getString(0), c.getString(1), c.getString(2), c.getInt(3)));
        }
        return out;
    }

    public Transaction find(String transactionId) {
        try (Cursor c = getReadableDatabase().query("transactions",
                new String[]{"id","transaction_id","source_key","source_package","merchant","amount","type","category","payment_method","occurred_at","raw_text","manual"},
                "transaction_id=?", new String[]{transactionId}, null, null, null, "1")) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    public List<Transaction> getRecent(int limit) {
        List<Transaction> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("transactions",
                new String[]{"id","transaction_id","source_key","source_package","merchant","amount","type","category","payment_method","occurred_at","raw_text","manual"},
                null, null, null, null, "occurred_at DESC", String.valueOf(Math.max(1, Math.min(limit, 200))))) {
            while (c.moveToNext()) out.add(fromCursor(c));
        }
        return out;
    }

    public long sumByTypeBetween(String type, long start, long end) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type=? AND occurred_at>=? AND occurred_at<?",
                new String[]{type,String.valueOf(start),String.valueOf(end)})) {
            return c.moveToFirst() ? c.getLong(0) : 0L;
        }
    }

    public int countBetween(long start, long end) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM transactions WHERE occurred_at>=? AND occurred_at<?",
                new String[]{String.valueOf(start),String.valueOf(end)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public void saveMerchantRule(String merchant, String category) {
        String key = merchant == null ? "" : merchant.trim().toLowerCase(Locale.KOREA);
        if (key.isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("merchant_key", key);
        v.put("category", category);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("merchant_rules", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String findMerchantRule(String merchant) {
        String key = merchant == null ? "" : merchant.trim().toLowerCase(Locale.KOREA);
        try (Cursor c = getReadableDatabase().query("merchant_rules", new String[]{"category"},
                "merchant_key=?", new String[]{key}, null, null, null, "1")) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    private int getAttempts(String table, String tx, String target) {
        try (Cursor c = getReadableDatabase().query(table, new String[]{"attempts"},
                "transaction_id=? AND target=?", new String[]{tx,target}, null, null, null, "1")) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    private static ContentValues failureValues(int attempts, String error) {
        ContentValues v = new ContentValues();
        v.put("attempts", attempts);
        v.put("last_error", trim(error, 300));
        v.put("next_retry_at", System.currentTimeMillis() + RetryPolicy.delayMillis(attempts));
        v.put("updated_at", System.currentTimeMillis());
        return v;
    }

    private static ContentValues values(Transaction tx) {
        ContentValues v = new ContentValues();
        v.put("transaction_id", tx.transactionId); v.put("source_key", tx.sourceKey); v.put("source_package", tx.sourcePackage);
        v.put("merchant", tx.merchant); v.put("amount", tx.amount); v.put("type", tx.type); v.put("category", tx.category);
        v.put("payment_method", tx.paymentMethod); v.put("occurred_at", tx.occurredAt); v.put("raw_text", tx.rawText); v.put("manual", tx.manual ? 1 : 0);
        return v;
    }

    private static Transaction fromCursor(Cursor c) {
        return new Transaction(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getLong(5),
                c.getString(6),c.getString(7),c.getString(8),c.getLong(9),c.getString(10),c.getInt(11)==1);
    }

    private static String trim(String s, int max) { if (s == null) return null; return s.length() <= max ? s : s.substring(0,max); }

    public static final class SyncItem {
        public final Transaction transaction; public final String target; public final int attempts; public final String remoteId;
        SyncItem(Transaction transaction, String target, int attempts, String remoteId) {
            this.transaction = transaction; this.target = target; this.attempts = attempts; this.remoteId = remoteId;
        }
    }

    public static final class DeleteItem {
        public final String transactionId; public final String target; public final String remoteId; public final int attempts;
        DeleteItem(String transactionId, String target, String remoteId, int attempts) {
            this.transactionId = transactionId; this.target = target; this.remoteId = remoteId; this.attempts = attempts;
        }
    }
}

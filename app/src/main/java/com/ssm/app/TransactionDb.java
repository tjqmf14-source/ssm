package com.ssm.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public final class TransactionDb extends SQLiteOpenHelper {
    public static final class CategoryTotal {
        public final String category;
        public final long amount;

        CategoryTotal(String category, long amount) {
            this.category = category;
            this.amount = amount;
        }
    }

    public static final class CalendarSyncRecord {
        public final long calendarId;
        public final Long eventId;
        public final String state;

        CalendarSyncRecord(long calendarId, Long eventId, String state) {
            this.calendarId = calendarId;
            this.eventId = eventId;
            this.state = state;
        }
    }
    private static final String DB_NAME = "ssm.db";
    private static final int DB_VERSION = 2;

    public static final String SYNC_PENDING = "PENDING";
    public static final String SYNC_SUCCESS = "SUCCESS";
    public static final String SYNC_RETRY = "RETRY";
    public static final String SYNC_AUTH_REQUIRED = "AUTH_REQUIRED";
    public static final String SYNC_WAITING_PERMISSION = "WAITING_PERMISSION";

    public TransactionDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE transactions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "transaction_id TEXT UNIQUE," +
                        "source_key TEXT NOT NULL UNIQUE," +
                        "source_package TEXT NOT NULL," +
                        "merchant TEXT NOT NULL," +
                        "normalized_merchant TEXT NOT NULL," +
                        "amount INTEGER NOT NULL," +
                        "type TEXT NOT NULL," +
                        "occurred_at INTEGER NOT NULL," +
                        "raw_text TEXT NOT NULL," +
                        "payment_method TEXT NOT NULL," +
                        "category TEXT NOT NULL," +
                        "status TEXT NOT NULL DEFAULT 'normal'," +
                        "confidence REAL NOT NULL DEFAULT 1.0," +
                        "calendar_event_id INTEGER," +
                        "notion_page_id TEXT," +
                        "notion_sync_state TEXT NOT NULL DEFAULT 'PENDING'," +
                        "notion_sync_attempts INTEGER NOT NULL DEFAULT 0," +
                        "created_at INTEGER NOT NULL," +
                        "updated_at INTEGER NOT NULL" +
                        ")"
        );
        db.execSQL("CREATE INDEX idx_transactions_occurred_at ON transactions(occurred_at DESC)");
        db.execSQL("CREATE INDEX idx_transactions_type_time ON transactions(type, occurred_at DESC)");
        db.execSQL("CREATE INDEX idx_transactions_dedup ON transactions(amount, type, occurred_at, normalized_merchant)");

        db.execSQL(
                "CREATE TABLE calendar_sync (" +
                        "transaction_id TEXT NOT NULL," +
                        "calendar_id INTEGER NOT NULL," +
                        "event_id INTEGER," +
                        "state TEXT NOT NULL," +
                        "updated_at INTEGER NOT NULL," +
                        "PRIMARY KEY(transaction_id, calendar_id)" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE merchant_rules (" +
                        "normalized_merchant TEXT PRIMARY KEY," +
                        "category TEXT NOT NULL," +
                        "updated_at INTEGER NOT NULL" +
                        ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN transaction_id TEXT");
            db.execSQL("ALTER TABLE transactions ADD COLUMN normalized_merchant TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE transactions ADD COLUMN payment_method TEXT NOT NULL DEFAULT '기타'");
            db.execSQL("ALTER TABLE transactions ADD COLUMN category TEXT NOT NULL DEFAULT '기타'");
            db.execSQL("ALTER TABLE transactions ADD COLUMN status TEXT NOT NULL DEFAULT 'normal'");
            db.execSQL("ALTER TABLE transactions ADD COLUMN confidence REAL NOT NULL DEFAULT 1.0");
            db.execSQL("ALTER TABLE transactions ADD COLUMN notion_page_id TEXT");
            db.execSQL("ALTER TABLE transactions ADD COLUMN notion_sync_state TEXT NOT NULL DEFAULT 'PENDING'");
            db.execSQL("ALTER TABLE transactions ADD COLUMN notion_sync_attempts INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE transactions ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0");

            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_transactions_transaction_id ON transactions(transaction_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_dedup ON transactions(amount, type, occurred_at, normalized_merchant)");

            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS calendar_sync (" +
                            "transaction_id TEXT NOT NULL," +
                            "calendar_id INTEGER NOT NULL," +
                            "event_id INTEGER," +
                            "state TEXT NOT NULL," +
                            "updated_at INTEGER NOT NULL," +
                            "PRIMARY KEY(transaction_id, calendar_id)" +
                            ")"
            );
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS merchant_rules (" +
                            "normalized_merchant TEXT PRIMARY KEY," +
                            "category TEXT NOT NULL," +
                            "updated_at INTEGER NOT NULL" +
                            ")"
            );
        }
    }

    public long insertOrIgnore(Transaction transaction) {
        if (transaction == null) return -1L;

        if (Transaction.STATUS_CANCELLED.equals(transaction.status)) {
            return applyCancellation(transaction);
        }

        long duplicateId = findLikelyDuplicate(transaction);
        if (duplicateId > 0L) return -duplicateId;

        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("transaction_id", transaction.transactionId);
        values.put("source_key", transaction.sourceKey);
        values.put("source_package", transaction.sourcePackage);
        values.put("merchant", transaction.merchant);
        values.put("normalized_merchant", transaction.normalizedMerchant);
        values.put("amount", transaction.amount);
        values.put("type", transaction.type);
        values.put("occurred_at", transaction.occurredAt);
        values.put("raw_text", transaction.rawText);
        values.put("payment_method", transaction.paymentMethod);
        values.put("category", applyMerchantRule(transaction.normalizedMerchant, transaction.category));
        values.put("status", transaction.status);
        values.put("confidence", transaction.confidence);
        values.put("notion_sync_state", SYNC_PENDING);
        values.put("created_at", now);
        values.put("updated_at", now);

        return getWritableDatabase().insertWithOnConflict(
                "transactions",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );
    }

    private long findLikelyDuplicate(Transaction transaction) {
        long window = 90_000L;
        try (Cursor cursor = getReadableDatabase().query(
                "transactions",
                new String[]{"id", "normalized_merchant"},
                "amount = ? AND type = ? AND status = ? AND occurred_at BETWEEN ? AND ?",
                new String[]{
                        String.valueOf(transaction.amount),
                        transaction.type,
                        Transaction.STATUS_NORMAL,
                        String.valueOf(transaction.occurredAt - window),
                        String.valueOf(transaction.occurredAt + window)
                },
                null,
                null,
                "occurred_at DESC",
                "8"
        )) {
            while (cursor.moveToNext()) {
                if (MerchantNormalizer.similar(cursor.getString(1), transaction.normalizedMerchant)) {
                    return cursor.getLong(0);
                }
            }
        }
        return -1L;
    }

    private long applyCancellation(Transaction cancellation) {
        long thirtyDays = 30L * 24L * 60L * 60L * 1000L;
        try (Cursor cursor = getReadableDatabase().query(
                "transactions",
                new String[]{"id", "transaction_id", "normalized_merchant"},
                "amount = ? AND type = ? AND status = ? AND occurred_at BETWEEN ? AND ?",
                new String[]{
                        String.valueOf(cancellation.amount),
                        cancellation.type,
                        Transaction.STATUS_NORMAL,
                        String.valueOf(cancellation.occurredAt - thirtyDays),
                        String.valueOf(cancellation.occurredAt + 60_000L)
                },
                null,
                null,
                "occurred_at DESC",
                "20"
        )) {
            while (cursor.moveToNext()) {
                String existingMerchant = cursor.getString(2);
                if (!MerchantNormalizer.similar(existingMerchant, cancellation.normalizedMerchant)) continue;

                long rowId = cursor.getLong(0);
                ContentValues values = new ContentValues();
                values.put("status", Transaction.STATUS_CANCELLED);
                values.put("updated_at", System.currentTimeMillis());
                values.put("notion_sync_state", SYNC_PENDING);
                getWritableDatabase().update("transactions", values, "id = ?", new String[]{String.valueOf(rowId)});
                return rowId;
            }
        }
        return -1L;
    }

    public Transaction getByRowId(long rowId) {
        try (Cursor cursor = getReadableDatabase().query(
                "transactions",
                transactionColumns(),
                "id = ?",
                new String[]{String.valueOf(rowId)},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public Transaction getByTransactionId(String transactionId) {
        if (transactionId == null) return null;
        try (Cursor cursor = getReadableDatabase().query(
                "transactions",
                transactionColumns(),
                "transaction_id = ?",
                new String[]{transactionId},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public String getTransactionId(long rowId) {
        try (Cursor cursor = getReadableDatabase().query(
                "transactions",
                new String[]{"transaction_id"},
                "id = ?",
                new String[]{String.valueOf(rowId)},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    public void markCalendarEvent(long rowId, long calendarEventId) {
        ContentValues values = new ContentValues();
        values.put("calendar_event_id", calendarEventId);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("transactions", values, "id = ?", new String[]{String.valueOf(rowId)});
    }

    public boolean hasCalendarSync(String transactionId, long calendarId) {
        return getCalendarEventId(transactionId, calendarId) != null;
    }

    public Long getCalendarEventId(String transactionId, long calendarId) {
        try (Cursor cursor = getReadableDatabase().query(
                "calendar_sync",
                new String[]{"event_id"},
                "transaction_id = ? AND calendar_id = ?",
                new String[]{transactionId, String.valueOf(calendarId)},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst() && !cursor.isNull(0) ? cursor.getLong(0) : null;
        }
    }

    public List<CalendarSyncRecord> getCalendarSyncRecords(String transactionId) {
        List<CalendarSyncRecord> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "calendar_sync",
                new String[]{"calendar_id", "event_id", "state"},
                "transaction_id = ?",
                new String[]{transactionId},
                null,
                null,
                "calendar_id ASC"
        )) {
            while (cursor.moveToNext()) {
                result.add(new CalendarSyncRecord(
                        cursor.getLong(0),
                        cursor.isNull(1) ? null : cursor.getLong(1),
                        cursor.getString(2)
                ));
            }
        }
        return result;
    }

    public void markCalendarSync(String transactionId, long calendarId, Long eventId, String state) {
        ContentValues values = new ContentValues();
        values.put("transaction_id", transactionId);
        values.put("calendar_id", calendarId);
        if (eventId == null) values.putNull("event_id");
        else values.put("event_id", eventId);
        values.put("state", state);
        values.put("updated_at", System.currentTimeMillis());

        getWritableDatabase().insertWithOnConflict(
                "calendar_sync",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    public void markNotionState(String transactionId, String state, String notionPageId, boolean incrementAttempt) {
        ContentValues values = new ContentValues();
        values.put("notion_sync_state", state);
        if (notionPageId != null) values.put("notion_page_id", notionPageId);
        if (incrementAttempt) {
            getWritableDatabase().execSQL(
                    "UPDATE transactions SET notion_sync_attempts = notion_sync_attempts + 1, notion_sync_state = ?, updated_at = ? WHERE transaction_id = ?",
                    new Object[]{state, System.currentTimeMillis(), transactionId}
            );
            if (notionPageId != null) {
                ContentValues page = new ContentValues();
                page.put("notion_page_id", notionPageId);
                getWritableDatabase().update("transactions", page, "transaction_id = ?", new String[]{transactionId});
            }
            return;
        }
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("transactions", values, "transaction_id = ?", new String[]{transactionId});
    }

    public void saveMerchantRule(String merchant, String category) {
        String normalized = MerchantNormalizer.normalize(merchant);
        if (normalized.isEmpty() || category == null || category.trim().isEmpty()) return;

        ContentValues values = new ContentValues();
        values.put("normalized_merchant", normalized);
        values.put("category", category);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict(
                "merchant_rules",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    private String applyMerchantRule(String normalizedMerchant, String fallback) {
        if (normalizedMerchant != null && !normalizedMerchant.isEmpty()) {
            try (Cursor cursor = getReadableDatabase().query(
                    "merchant_rules",
                    new String[]{"category"},
                    "normalized_merchant = ?",
                    new String[]{normalizedMerchant},
                    null,
                    null,
                    null,
                    "1"
            )) {
                if (cursor.moveToFirst()) return cursor.getString(0);
            }
        }
        return fallback == null ? "기타" : fallback;
    }

    public boolean updateTransaction(long rowId, String merchant, long amount, String type, String category, String paymentMethod) {
        if (rowId <= 0L || amount <= 0L) return false;
        ContentValues values = new ContentValues();
        values.put("merchant", merchant);
        values.put("normalized_merchant", MerchantNormalizer.normalize(merchant));
        values.put("amount", amount);
        values.put("type", type);
        values.put("category", category);
        values.put("payment_method", paymentMethod);
        values.put("updated_at", System.currentTimeMillis());
        values.put("notion_sync_state", SYNC_PENDING);
        int changed = getWritableDatabase().update("transactions", values, "id = ?", new String[]{String.valueOf(rowId)});
        if (changed > 0) saveMerchantRule(merchant, category);
        return changed > 0;
    }

    public boolean deleteTransaction(long rowId) {
        Transaction tx = getByRowId(rowId);
        if (tx == null) return false;

        ContentValues values = new ContentValues();
        values.put("status", Transaction.STATUS_DELETED);
        values.put("notion_sync_state", SYNC_PENDING);
        values.put("updated_at", System.currentTimeMillis());
        return getWritableDatabase().update(
                "transactions",
                values,
                "id = ?",
                new String[]{String.valueOf(rowId)}
        ) > 0;
    }

    public int getPendingNotionCount() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM transactions WHERE status != ? AND notion_sync_state != ?",
                new String[]{Transaction.STATUS_DELETED, SYNC_SUCCESS}
        )) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public int getNeedsReviewCount() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM transactions WHERE status = ? AND (confidence < 0.9 OR category = '기타')",
                new String[]{Transaction.STATUS_NORMAL}
        )) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public List<CategoryTotal> getCategoryTotalsBetween(long startInclusive, long endExclusive) {
        List<CategoryTotal> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT category, COALESCE(SUM(amount), 0) FROM transactions " +
                        "WHERE type = ? AND status = ? AND occurred_at >= ? AND occurred_at < ? " +
                        "GROUP BY category ORDER BY SUM(amount) DESC",
                new String[]{
                        Transaction.TYPE_EXPENSE,
                        Transaction.STATUS_NORMAL,
                        String.valueOf(startInclusive),
                        String.valueOf(endExclusive)
                }
        )) {
            while (cursor.moveToNext()) {
                result.add(new CategoryTotal(cursor.getString(0), cursor.getLong(1)));
            }
        }
        return result;
    }

    public long sumByTypeBetween(String type, long startInclusive, long endExclusive) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions " +
                        "WHERE type = ? AND status = ? AND occurred_at >= ? AND occurred_at < ?",
                new String[]{type, Transaction.STATUS_NORMAL, String.valueOf(startInclusive), String.valueOf(endExclusive)}
        )) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    public int countBetween(long startInclusive, long endExclusive) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM transactions WHERE status = ? AND occurred_at >= ? AND occurred_at < ?",
                new String[]{Transaction.STATUS_NORMAL, String.valueOf(startInclusive), String.valueOf(endExclusive)}
        )) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public List<Transaction> getRecent(int limit) {
        List<Transaction> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "transactions",
                transactionColumns(),
                "status != ?",
                new String[]{Transaction.STATUS_DELETED},
                null,
                null,
                "occurred_at DESC",
                String.valueOf(Math.max(1, Math.min(limit, 100)))
        )) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    private static String[] transactionColumns() {
        return new String[]{
                "id", "transaction_id", "source_key", "source_package", "merchant",
                "normalized_merchant", "amount", "type", "occurred_at", "raw_text",
                "payment_method", "category", "status", "confidence", "calendar_event_id", "notion_page_id"
        };
    }

    private static Transaction fromCursor(Cursor cursor) {
        return new Transaction(
                cursor.getLong(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getString(3),
                cursor.getString(4),
                cursor.getString(5),
                cursor.getLong(6),
                cursor.getString(7),
                cursor.getLong(8),
                cursor.getString(9),
                cursor.getString(10),
                cursor.getString(11),
                cursor.getString(12),
                cursor.getDouble(13),
                cursor.isNull(14) ? null : cursor.getLong(14),
                cursor.isNull(15) ? null : cursor.getString(15)
        );
    }
}

package com.ssm.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public final class TransactionDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "ssm.db";
    private static final int DB_VERSION = 1;

    public TransactionDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE transactions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "source_key TEXT NOT NULL UNIQUE," +
                        "source_package TEXT NOT NULL," +
                        "merchant TEXT NOT NULL," +
                        "amount INTEGER NOT NULL," +
                        "type TEXT NOT NULL," +
                        "occurred_at INTEGER NOT NULL," +
                        "raw_text TEXT NOT NULL," +
                        "calendar_event_id INTEGER," +
                        "created_at INTEGER NOT NULL" +
                        ")"
        );
        db.execSQL("CREATE INDEX idx_transactions_occurred_at ON transactions(occurred_at DESC)");
        db.execSQL("CREATE INDEX idx_transactions_type_time ON transactions(type, occurred_at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v1 only.
    }

    public long insertOrIgnore(Transaction transaction) {
        ContentValues values = new ContentValues();
        values.put("source_key", transaction.sourceKey);
        values.put("source_package", transaction.sourcePackage);
        values.put("merchant", transaction.merchant);
        values.put("amount", transaction.amount);
        values.put("type", transaction.type);
        values.put("occurred_at", transaction.occurredAt);
        values.put("raw_text", transaction.rawText);
        values.put("created_at", System.currentTimeMillis());

        return getWritableDatabase().insertWithOnConflict(
                "transactions",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );
    }

    public void markCalendarEvent(long rowId, long calendarEventId) {
        ContentValues values = new ContentValues();
        values.put("calendar_event_id", calendarEventId);
        getWritableDatabase().update(
                "transactions",
                values,
                "id = ?",
                new String[]{String.valueOf(rowId)}
        );
    }

    public long sumByTypeBetween(String type, long startInclusive, long endExclusive) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions " +
                        "WHERE type = ? AND occurred_at >= ? AND occurred_at < ?",
                new String[]{type, String.valueOf(startInclusive), String.valueOf(endExclusive)}
        )) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    public int countBetween(long startInclusive, long endExclusive) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM transactions WHERE occurred_at >= ? AND occurred_at < ?",
                new String[]{String.valueOf(startInclusive), String.valueOf(endExclusive)}
        )) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public List<Transaction> getRecent(int limit) {
        List<Transaction> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "transactions",
                new String[]{
                        "id", "source_key", "source_package", "merchant", "amount",
                        "type", "occurred_at", "raw_text", "calendar_event_id"
                },
                null,
                null,
                null,
                null,
                "occurred_at DESC",
                String.valueOf(Math.max(1, Math.min(limit, 100)))
        )) {
            while (cursor.moveToNext()) {
                result.add(new Transaction(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getLong(4),
                        cursor.getString(5),
                        cursor.getLong(6),
                        cursor.getString(7),
                        cursor.isNull(8) ? null : cursor.getLong(8)
                ));
            }
        }
        return result;
    }
}

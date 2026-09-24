package com.ssm.app;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

public final class CalendarSync {
    private static final String PREFS = "ssm_prefs";
    private static final String KEY_CALENDAR_ID = "calendar_id";
    private static final String KEY_CALENDAR_NAME = "calendar_name";
    private static final String KEY_CALENDAR_IDS = "calendar_ids";
    private static final String KEY_CALENDAR_NAMES = "calendar_names";

    public static final class CalendarItem {
        public final long id;
        public final String displayName;
        public final String accountName;

        CalendarItem(long id, String displayName, String accountName) {
            this.id = id;
            this.displayName = displayName;
            this.accountName = accountName;
        }

        @Override
        public String toString() {
            if (accountName == null || accountName.trim().isEmpty()) return displayName;
            return displayName + " · " + accountName;
        }
    }

    private CalendarSync() {}

    public static boolean hasPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    public static List<CalendarItem> listWritableCalendars(Context context) {
        List<CalendarItem> result = new ArrayList<>();
        if (!hasPermission(context)) return result;

        String[] projection = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME
        };

        String selection = CalendarContract.Calendars.VISIBLE + " = 1 AND "
                + CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= ?";
        String[] args = {String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)};

        try (Cursor cursor = context.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                args,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " COLLATE NOCASE ASC"
        )) {
            if (cursor == null) return result;
            while (cursor.moveToNext()) {
                result.add(new CalendarItem(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2)
                ));
            }
        }
        return result;
    }

    // Legacy single-calendar entry point kept until the 2.0 settings UI replaces it.
    public static void saveCalendar(Context context, CalendarItem item) {
        saveCalendars(context, Collections.singletonList(item));
    }

    public static void saveCalendars(Context context, List<CalendarItem> items) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();

        StringBuilder ids = new StringBuilder();
        StringBuilder names = new StringBuilder();
        for (CalendarItem item : items) {
            if (item == null) continue;
            if (ids.length() > 0) ids.append(",");
            if (names.length() > 0) names.append(" • ");
            ids.append(item.id);
            names.append(item.toString());
        }

        editor.putString(KEY_CALENDAR_IDS, ids.toString());
        editor.putString(KEY_CALENDAR_NAMES, names.toString());

        if (!items.isEmpty() && items.get(0) != null) {
            editor.putLong(KEY_CALENDAR_ID, items.get(0).id);
            editor.putString(KEY_CALENDAR_NAME, items.get(0).toString());
        } else {
            editor.remove(KEY_CALENDAR_ID);
            editor.remove(KEY_CALENDAR_NAME);
        }
        editor.apply();
    }

    public static String getSelectedCalendarName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String names = prefs.getString(KEY_CALENDAR_NAMES, "");
        if (names != null && !names.trim().isEmpty()) return names;
        return prefs.getString(KEY_CALENDAR_NAME, "선택 안 됨");
    }

    public static Long getSelectedCalendarId(Context context) {
        List<Long> ids = getSelectedCalendarIds(context);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public static List<Long> getSelectedCalendarIds(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = prefs.getString(KEY_CALENDAR_IDS, "");
        List<Long> result = new ArrayList<>();

        if (stored != null && !stored.trim().isEmpty()) {
            for (String part : stored.split(",")) {
                try {
                    long id = Long.parseLong(part.trim());
                    if (id >= 0L && !result.contains(id)) result.add(id);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (result.isEmpty() && prefs.contains(KEY_CALENDAR_ID)) {
            long legacy = prefs.getLong(KEY_CALENDAR_ID, -1L);
            if (legacy >= 0L) result.add(legacy);
        }

        if (result.isEmpty()) {
            autoSelectNamedCalendar(context, "씀 가계부");
            prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (prefs.contains(KEY_CALENDAR_ID)) {
                long selected = prefs.getLong(KEY_CALENDAR_ID, -1L);
                if (selected >= 0L) result.add(selected);
            }
        }

        return result;
    }

    private static void autoSelectNamedCalendar(Context context, String targetName) {
        if (!hasPermission(context)) return;
        for (CalendarItem item : listWritableCalendars(context)) {
            if (targetName.equals(item.displayName)) {
                saveCalendar(context, item);
                return;
            }
        }
    }

    public static Long addTransactionEvent(Context context, Transaction transaction) {
        Long calendarId = getSelectedCalendarId(context);
        if (calendarId == null) return null;
        return addTransactionEvent(context, transaction, calendarId);
    }

    public static Long addTransactionEvent(Context context, Transaction transaction, long calendarId) {
        if (!hasPermission(context) || calendarId < 0L) return null;

        ContentValues values = eventValues(transaction);
        values.put(CalendarContract.Events.CALENDAR_ID, calendarId);

        ContentResolver resolver = context.getContentResolver();
        Uri uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values);
        if (uri == null) return null;
        return ContentUris.parseId(uri);
    }

    public static boolean updateTransactionEvent(Context context, Transaction transaction, long eventId) {
        if (!hasPermission(context) || eventId < 0L) return false;
        Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
        return context.getContentResolver().update(uri, eventValues(transaction), null, null) > 0;
    }

    public static boolean deleteTransactionEvent(Context context, long eventId) {
        if (!hasPermission(context) || eventId < 0L) return false;
        Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
        return context.getContentResolver().delete(uri, null, null) > 0;
    }

    private static ContentValues eventValues(Transaction transaction) {
        String prefix = transaction.isCancelled()
                ? "취소"
                : (transaction.isExpense() ? "지출" : "입금");

        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.TITLE,
                prefix + " " + formatWon(transaction.amount) + " · " + transaction.merchant);
        values.put(CalendarContract.Events.DESCRIPTION,
                "씀 가계부 2.0 자동 기록\ntransaction_id=" + transaction.transactionId + "\n" + transaction.rawText);
        values.put(CalendarContract.Events.DTSTART, transaction.occurredAt);
        values.put(CalendarContract.Events.DTEND, transaction.occurredAt + 5 * 60 * 1000L);
        values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        return values;
    }

    private static String formatWon(long amount) {
        return String.format("%,d원", amount);
    }
}

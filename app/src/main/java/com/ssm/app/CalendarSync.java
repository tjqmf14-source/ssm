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
import java.util.List;
import java.util.TimeZone;

public final class CalendarSync {
    private static final String PREFS = "ssm_prefs";
    private static final String KEY_CALENDAR_ID = "calendar_id";
    private static final String KEY_CALENDAR_NAME = "calendar_name";

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
            if (accountName == null || accountName.isBlank()) {
                return displayName;
            }
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
        if (!hasPermission(context)) {
            return result;
        }

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

    public static void saveCalendar(Context context, CalendarItem item) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_CALENDAR_ID, item.id)
                .putString(KEY_CALENDAR_NAME, item.toString())
                .apply();
    }

    public static String getSelectedCalendarName(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CALENDAR_NAME, "선택 안 됨");
    }

    public static Long getSelectedCalendarId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_CALENDAR_ID)) {
            autoSelectNamedCalendar(context, "씀 가계부");
        }
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.contains(KEY_CALENDAR_ID) ? prefs.getLong(KEY_CALENDAR_ID, -1L) : null;
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
        if (!hasPermission(context)) {
            return null;
        }

        Long calendarId = getSelectedCalendarId(context);
        if (calendarId == null || calendarId < 0L) {
            return null;
        }

        String prefix = transaction.isExpense() ? "지출" : "입금";
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        values.put(CalendarContract.Events.TITLE,
                prefix + " " + formatWon(transaction.amount) + " · " + transaction.merchant);
        values.put(CalendarContract.Events.DESCRIPTION,
                "씀 가계부 자동 기록\n" + transaction.rawText);
        values.put(CalendarContract.Events.DTSTART, transaction.occurredAt);
        values.put(CalendarContract.Events.DTEND, transaction.occurredAt + 5 * 60 * 1000L);
        values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());

        ContentResolver resolver = context.getContentResolver();
        Uri uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values);
        if (uri == null) {
            return null;
        }
        return ContentUris.parseId(uri);
    }

    private static String formatWon(long amount) {
        return String.format("%,d원", amount);
    }
}

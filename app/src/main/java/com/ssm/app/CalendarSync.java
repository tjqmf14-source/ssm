package com.ssm.app;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class CalendarSync {
    public static final String TARGET_GOOGLE = "calendar_google";
    public static final String TARGET_SAMSUNG = "calendar_samsung";

    private CalendarSync() {}

    public static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && c.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    public static List<CalendarItem> listWritableCalendars(Context c) {
        List<CalendarItem> out = new ArrayList<>();
        if (!hasPermission(c)) return out;
        String[] projection = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE
        };
        String selection = CalendarContract.Calendars.VISIBLE + "=1 AND "
                + CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + ">=?";
        try (Cursor cursor = c.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                new String[]{String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)},
                CalendarContract.Calendars.IS_PRIMARY + " DESC, " + CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " COLLATE NOCASE ASC")) {
            if (cursor == null) return out;
            while (cursor.moveToNext()) {
                out.add(new CalendarItem(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3)));
            }
        }
        return out;
    }

    public static CalendarItem findBest(Context c, String target) {
        for (CalendarItem item : listWritableCalendars(c)) {
            String hay = (item.accountType + " " + item.accountName + " " + item.displayName).toLowerCase(Locale.ROOT);
            if (TARGET_GOOGLE.equals(target)) {
                if ("com.google".equalsIgnoreCase(item.accountType) || hay.contains("google") || hay.contains("gmail")) return item;
            } else if (TARGET_SAMSUNG.equals(target)) {
                if (hay.contains("samsung") || hay.contains("scloud") || hay.contains("com.osp")) return item;
            }
        }
        return null;
    }

    public static String upsertTransactionEvent(Context context, Transaction tx, String target, String remoteId) throws Exception {
        if (!hasPermission(context)) throw new IllegalStateException("calendar permission missing");
        CalendarItem cal = findBest(context, target);
        if (cal == null && TARGET_SAMSUNG.equals(target)) {
            // Samsung Calendar can display/sync a Google-account calendar on Galaxy devices.
            // Reuse the Google calendar instead of creating a duplicate local event.
            cal = findBest(context, TARGET_GOOGLE);
        }
        if (cal == null) throw new IllegalStateException(target + " writable calendar not found");

        ContentValues v = new ContentValues();
        v.put(CalendarContract.Events.CALENDAR_ID, cal.id);
        v.put(CalendarContract.Events.TITLE,
                (tx.isExpense() ? "지출 " : "입금 ") + String.format(Locale.KOREA, "%,d원", tx.amount) + " · " + tx.merchant);
        v.put(CalendarContract.Events.DESCRIPTION,
                "씀 가계부 2.0\ntransaction_id=" + tx.transactionId + "\n카테고리=" + tx.category + "\n결제수단=" + tx.paymentMethod + "\n" + tx.rawText);
        v.put(CalendarContract.Events.DTSTART, tx.occurredAt);
        v.put(CalendarContract.Events.DTEND, tx.occurredAt + 5 * 60 * 1000L);
        v.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());

        ContentResolver resolver = context.getContentResolver();
        if (remoteId == null || remoteId.isBlank()) {
            Long existingId = findExistingEventId(context, cal.id, tx.transactionId);
            if (existingId != null) remoteId = String.valueOf(existingId);
        }
        if (remoteId != null && !remoteId.isBlank()) {
            try {
                Uri existing = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, Long.parseLong(remoteId));
                int updated = resolver.update(existing, v, null, null);
                if (updated > 0) return remoteId;
            } catch (NumberFormatException ignored) {}
        }

        Uri uri = resolver.insert(CalendarContract.Events.CONTENT_URI, v);
        if (uri == null) throw new IllegalStateException("calendar insert returned null");
        return String.valueOf(ContentUris.parseId(uri));
    }


    public static void deleteTransactionEvent(Context context, String target, String remoteId) throws Exception {
        if (remoteId == null || remoteId.isBlank()) return;
        if (!hasPermission(context)) throw new IllegalStateException("calendar permission missing");
        try {
            long eventId = Long.parseLong(remoteId);
            Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
            context.getContentResolver().delete(uri, null, null);
        } catch (NumberFormatException error) {
            throw new IllegalStateException("invalid calendar event id");
        }
    }

    private static Long findExistingEventId(Context context, long calendarId, String transactionId) {
        if (!hasPermission(context)) return null;
        String[] projection = { CalendarContract.Events._ID };
        String selection = CalendarContract.Events.CALENDAR_ID + "=? AND " + CalendarContract.Events.DESCRIPTION + " LIKE ?";
        String[] args = { String.valueOf(calendarId), "%transaction_id=" + transactionId + "%" };
        try (Cursor cursor = context.getContentResolver().query(
                CalendarContract.Events.CONTENT_URI, projection, selection, args, null)) {
            return cursor != null && cursor.moveToFirst() ? cursor.getLong(0) : null;
        }
    }

    public static final class CalendarItem {
        public final long id;
        public final String displayName;
        public final String accountName;
        public final String accountType;

        CalendarItem(long id, String displayName, String accountName, String accountType) {
            this.id = id;
            this.displayName = displayName == null ? "" : displayName;
            this.accountName = accountName == null ? "" : accountName;
            this.accountType = accountType == null ? "" : accountType;
        }
    }
}

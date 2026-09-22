package com.ssm.app;

import android.app.Notification;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public final class PaymentNotificationListener extends NotificationListenerService {
    public static final String ACTION_TRANSACTION_CHANGED =
            "com.ssm.app.TRANSACTION_CHANGED";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) {
            return;
        }

        Notification notification = sbn.getNotification();
        if (notification == null) {
            return;
        }

        CharSequence titleCs = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textCs = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bigTextCs = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);

        Transaction transaction = NotificationParser.parse(
                sbn.getKey(),
                sbn.getPackageName(),
                titleCs == null ? "" : titleCs.toString(),
                textCs == null ? "" : textCs.toString(),
                bigTextCs == null ? "" : bigTextCs.toString(),
                sbn.getPostTime()
        );

        if (transaction == null) {
            return;
        }

        TransactionDb db = new TransactionDb(getApplicationContext());
        long rowId = db.insertOrIgnore(transaction);
        if (rowId <= 0L) {
            db.close();
            return;
        }

        Long eventId = CalendarSync.addTransactionEvent(getApplicationContext(), transaction);
        if (eventId != null) {
            db.markCalendarEvent(rowId, eventId);
        }
        db.close();

        Intent changed = new Intent(ACTION_TRANSACTION_CHANGED);
        changed.setPackage(getPackageName());
        sendBroadcast(changed);
    }
}

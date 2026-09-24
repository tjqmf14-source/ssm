package com.ssm.app;

import android.content.Context;

import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

public final class CalendarSyncWorker extends Worker {
    public CalendarSyncWorker(Context context, WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        String transactionId = getInputData().getString(SyncScheduler.KEY_TRANSACTION_ID);
        if (transactionId == null) return Result.failure();

        TransactionDb db = new TransactionDb(getApplicationContext());
        try {
            Transaction transaction = db.getByTransactionId(transactionId);
            if (transaction == null) return Result.failure();

            if (!CalendarSync.hasPermission(getApplicationContext())) {
                for (Long calendarId : CalendarSync.getSelectedCalendarIds(getApplicationContext())) {
                    db.markCalendarSync(transactionId, calendarId, null, TransactionDb.SYNC_WAITING_PERMISSION);
                }
                return Result.success();
            }

            if (transaction.isDeleted()) {
                for (TransactionDb.CalendarSyncRecord record : db.getCalendarSyncRecords(transactionId)) {
                    if (record.eventId != null) {
                        CalendarSync.deleteTransactionEvent(getApplicationContext(), record.eventId);
                    }
                    db.markCalendarSync(transactionId, record.calendarId, null, "DELETED");
                }
                return Result.success();
            }

            List<Long> calendarIds = CalendarSync.getSelectedCalendarIds(getApplicationContext());
            if (calendarIds.isEmpty()) return Result.success();

            for (Long calendarId : calendarIds) {
                if (calendarId == null || calendarId < 0L) continue;

                Long existingEventId = db.getCalendarEventId(transactionId, calendarId);
                if (existingEventId != null) {
                    if (!CalendarSync.updateTransactionEvent(getApplicationContext(), transaction, existingEventId)) {
                        db.markCalendarSync(transactionId, calendarId, existingEventId, TransactionDb.SYNC_RETRY);
                        return Result.retry();
                    }
                    db.markCalendarSync(transactionId, calendarId, existingEventId, TransactionDb.SYNC_SUCCESS);
                    continue;
                }

                Long eventId = CalendarSync.addTransactionEvent(getApplicationContext(), transaction, calendarId);
                if (eventId == null) {
                    db.markCalendarSync(transactionId, calendarId, null, TransactionDb.SYNC_RETRY);
                    return Result.retry();
                }
                db.markCalendarSync(transactionId, calendarId, eventId, TransactionDb.SYNC_SUCCESS);
                db.markCalendarEvent(transaction.id, eventId);
            }
            return Result.success();
        } finally {
            db.close();
        }
    }
}

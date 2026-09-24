package com.ssm.app;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.util.List;

public final class SyncCoordinator {
    private static final int JOB_ID = 22002;
    private SyncCoordinator() {}

    public static void enqueueAllTargets(Context context, Transaction tx) {
        try (TransactionDb db = new TransactionDb(context)) {
            db.ensureSyncTarget(tx.transactionId, CalendarSync.TARGET_GOOGLE);
            db.ensureSyncTarget(tx.transactionId, CalendarSync.TARGET_SAMSUNG);
            db.ensureSyncTarget(tx.transactionId, NotionSync.TARGET);
        }
        schedule(context);
    }

    public static int runDue(Context context, int limit) {
        int processed = 0;
        boolean calendarReady = CalendarSync.hasPermission(context);
        boolean notionReady = SecretStore.hasNotionToken(context) && hasNetwork(context);

        try (TransactionDb db = new TransactionDb(context)) {
            List<TransactionDb.DeleteItem> deletions = db.getDueDeleteItems(limit);
            for (TransactionDb.DeleteItem item : deletions) {
                if (NotionSync.TARGET.equals(item.target) && !notionReady) continue;
                if (!NotionSync.TARGET.equals(item.target) && !calendarReady) continue;
                processed++;
                try {
                    if (NotionSync.TARGET.equals(item.target)) {
                        NotionSync.trash(context, item.remoteId);
                    } else {
                        CalendarSync.deleteTransactionEvent(context, item.target, item.remoteId);
                    }
                    db.markDeleteSuccess(item.transactionId, item.target);
                } catch (Exception e) {
                    db.markDeleteFailure(item.transactionId, item.target,
                            e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }

            List<TransactionDb.SyncItem> items = db.getDueSyncItems(limit);
            for (TransactionDb.SyncItem item : items) {
                if (NotionSync.TARGET.equals(item.target) && !notionReady) continue;
                if (!NotionSync.TARGET.equals(item.target) && !calendarReady) continue;
                processed++;
                try {
                    String id = NotionSync.TARGET.equals(item.target)
                            ? NotionSync.upsert(context, item.transaction, item.remoteId)
                            : CalendarSync.upsertTransactionEvent(context, item.transaction, item.target, item.remoteId);
                    db.markSyncSuccess(item.transaction.transactionId, item.target, id);
                } catch (Exception e) {
                    db.markSyncFailure(item.transaction.transactionId, item.target,
                            e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }
        return processed;
    }

    public static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;

        int pending;
        int pendingCalendars;
        int pendingNotion;
        long nextRetry;
        try (TransactionDb db = new TransactionDb(context)) {
            pending = db.pendingSyncCount();
            pendingCalendars = db.pendingCalendarCount();
            pendingNotion = db.pendingTargetCount(NotionSync.TARGET);
            nextRetry = db.nextRetryAt();
        }
        if (pending <= 0) {
            scheduler.cancel(JOB_ID);
            return;
        }

        boolean calendarsActionable = pendingCalendars > 0 && CalendarSync.hasPermission(context);
        boolean notionActionable = pendingNotion > 0 && SecretStore.hasNotionToken(context);
        if (!calendarsActionable && !notionActionable) {
            scheduler.cancel(JOB_ID);
            return;
        }

        long now = System.currentTimeMillis();
        long delay = Math.max(1_000L, nextRetry <= 0 ? 1_000L : nextRetry - now);
        delay = Math.min(delay, 6L * 60L * 60L * 1000L);
        int networkType = calendarsActionable ? JobInfo.NETWORK_TYPE_NONE : JobInfo.NETWORK_TYPE_ANY;
        JobInfo info = new JobInfo.Builder(JOB_ID, new ComponentName(context, SyncJobService.class))
                .setRequiredNetworkType(networkType)
                .setPersisted(true)
                .setMinimumLatency(delay)
                .setOverrideDeadline(delay + 15 * 60_000L)
                .build();
        scheduler.schedule(info);
    }

    static boolean hasNetwork(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }
}

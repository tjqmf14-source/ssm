package com.ssm.app;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class SyncScheduler {
    public static final String KEY_TRANSACTION_ID = "transaction_id";

    private SyncScheduler() {}

    public static void enqueue(Context context, String transactionId) {
        if (transactionId == null || transactionId.trim().isEmpty()) return;

        Data input = new Data.Builder()
                .putString(KEY_TRANSACTION_ID, transactionId)
                .build();

        OneTimeWorkRequest calendar = new OneTimeWorkRequest.Builder(CalendarSyncWorker.class)
                .setInputData(input)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build();

        Constraints notionConstraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest notion = new OneTimeWorkRequest.Builder(NotionSyncWorker.class)
                .setInputData(input)
                .setConstraints(notionConstraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();

        WorkManager manager = WorkManager.getInstance(context.getApplicationContext());
        manager.enqueueUniqueWork(
                "ssm-calendar-" + transactionId,
                ExistingWorkPolicy.KEEP,
                calendar
        );
        manager.enqueueUniqueWork(
                "ssm-notion-" + transactionId,
                ExistingWorkPolicy.KEEP,
                notion
        );
    }
}

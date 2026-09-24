package com.ssm.app;

import android.content.Context;

import androidx.work.Worker;
import androidx.work.WorkerParameters;

public final class NotionSyncWorker extends Worker {
    public NotionSyncWorker(Context context, WorkerParameters params) {
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

            String token = SecureTokenStore.loadNotionToken(getApplicationContext());
            if (token == null || token.trim().isEmpty()) {
                db.markNotionState(transactionId, TransactionDb.SYNC_AUTH_REQUIRED, null, false);
                return Result.success();
            }

            NotionSyncClient.Result sync = NotionSyncClient.upsert(token, transaction);
            if (sync.success) {
                db.markNotionState(transactionId, TransactionDb.SYNC_SUCCESS, sync.pageId, true);
                return Result.success();
            }
            if (sync.authRequired) {
                db.markNotionState(transactionId, TransactionDb.SYNC_AUTH_REQUIRED, null, true);
                return Result.success();
            }
            if (sync.retryable) {
                db.markNotionState(transactionId, TransactionDb.SYNC_RETRY, null, true);
                return Result.retry();
            }

            db.markNotionState(transactionId, "FAILED", null, true);
            return Result.failure();
        } finally {
            db.close();
        }
    }
}

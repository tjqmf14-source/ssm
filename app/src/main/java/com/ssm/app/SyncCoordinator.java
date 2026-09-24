package com.ssm.app;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import java.util.List;

public final class SyncCoordinator {
    private static final int JOB_ID=22002;
    private SyncCoordinator(){}
    public static void enqueueAllTargets(Context context,Transaction tx){try(TransactionDb db=new TransactionDb(context)){db.ensureSyncTarget(tx.transactionId,CalendarSync.TARGET_GOOGLE);db.ensureSyncTarget(tx.transactionId,CalendarSync.TARGET_SAMSUNG);db.ensureSyncTarget(tx.transactionId,NotionSync.TARGET);}runDue(context,3);schedule(context);}
    public static int runDue(Context context,int limit){int processed=0;try(TransactionDb db=new TransactionDb(context)){List<TransactionDb.SyncItem> items=db.getDueSyncItems(limit);for(TransactionDb.SyncItem item:items){processed++;try{String id;if(NotionSync.TARGET.equals(item.target))id=NotionSync.upsert(context,item.transaction,item.remoteId);else id=CalendarSync.upsertTransactionEvent(context,item.transaction,item.target,item.remoteId);db.markSyncSuccess(item.transaction.transactionId,item.target,id);}catch(Exception e){db.markSyncFailure(item.transaction.transactionId,item.target,e.getClass().getSimpleName()+": "+e.getMessage());}}}return processed;}
    public static void schedule(Context context){JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);if(scheduler==null)return;JobInfo info=new JobInfo.Builder(JOB_ID,new ComponentName(context,SyncJobService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(false).setMinimumLatency(30_000L).setOverrideDeadline(15*60_000L).build();scheduler.schedule(info);}
}

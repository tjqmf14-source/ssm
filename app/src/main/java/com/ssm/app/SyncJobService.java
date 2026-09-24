package com.ssm.app;
import android.app.job.JobParameters;
import android.app.job.JobService;
public final class SyncJobService extends JobService {
    @Override public boolean onStartJob(JobParameters params){new Thread(() -> {SyncCoordinator.runDue(getApplicationContext(),50);jobFinished(params,false);}).start();return true;}
    @Override public boolean onStopJob(JobParameters params){return true;}
}

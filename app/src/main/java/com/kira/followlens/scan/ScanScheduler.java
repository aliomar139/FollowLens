package com.kira.followlens.scan;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Registers the recurring scan and the manual one. */
public final class ScanScheduler {

    private static final String PERIODIC_NAME = "followlens-periodic-scan";
    private static final String MANUAL_NAME = "followlens-manual-scan";

    /** 15 minutes is PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS. */
    private static final long INTERVAL_MINUTES = 15;

    private ScanScheduler() {
    }

    public static void schedulePeriodic(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                ScanWorker.class, INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(networkRequired())
                .addTag(ScanWorker.TAG)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    /**
     * A user-initiated refresh, which skips the cooldown.
     *
     * Expedited because someone is watching the button. Plain background work
     * gets deferred by the scheduler, and a failed attempt then lands in
     * exponential backoff — which is why Refresh could look completely dead for
     * many minutes. The worker also declines to retry a forced scan, so a failure
     * is reported at once instead of being re-queued invisibly.
     */
    public static void requestOneOff(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ScanWorker.class)
                .setInputData(new Data.Builder()
                        .putBoolean(ScanWorker.KEY_FORCE, true)
                        .build())
                .setConstraints(networkRequired())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                .addTag(ScanWorker.TAG)
                .build();

        // REPLACE so hammering Refresh cannot pile up a queue of scans, and so a
        // previous attempt sitting in backoff is discarded rather than waited on.
        WorkManager.getInstance(context).enqueueUniqueWork(
                MANUAL_NAME, ExistingWorkPolicy.REPLACE, request);
    }

    /**
     * Every scan, periodic or manual. Used to show progress.
     *
     * Note the periodic request sits in ENQUEUED between runs forever, so only
     * RUNNING is meaningful here — treating ENQUEUED as "queued" would leave the
     * dashboard permanently claiming a scan was pending.
     */
    public static LiveData<List<WorkInfo>> allScans(Context context) {
        return WorkManager.getInstance(context).getWorkInfosByTagLiveData(ScanWorker.TAG);
    }

    /** Just the manual refresh, where ENQUEUED genuinely means "waiting to start". */
    public static LiveData<List<WorkInfo>> manualScan(Context context) {
        return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(MANUAL_NAME);
    }

    private static Constraints networkRequired() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }
}

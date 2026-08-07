package com.kira.followlens.scan;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Registers the recurring scan and the manual one. */
public final class ScanScheduler {

    private static final String PERIODIC_NAME = "followlens-periodic-scan";

    /** 15 minutes is PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS. */
    private static final long INTERVAL_MINUTES = 15;

    private ScanScheduler() {
    }

    public static void schedulePeriodic(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                ScanWorker.class, INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    /** A user-initiated refresh, which skips the cooldown. */
    public static void requestOneOff(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ScanWorker.class)
                .setInputData(new Data.Builder()
                        .putBoolean(ScanWorker.KEY_FORCE, true)
                        .build())
                .build();
        WorkManager.getInstance(context).enqueue(request);
    }
}

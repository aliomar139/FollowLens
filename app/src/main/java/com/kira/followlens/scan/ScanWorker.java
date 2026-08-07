package com.kira.followlens.scan;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.kira.followlens.auth.SessionStore;
import com.kira.followlens.data.FollowLensDatabase;
import com.kira.followlens.data.ScanRepository;
import com.kira.followlens.net.IgWebClient;
import com.kira.followlens.net.Sleeper;
import com.kira.followlens.notify.ScanNotifier;

import okhttp3.HttpUrl;

/**
 * Runs a scan off the main thread. Worker.doWork() is already called on a
 * background thread, so no coroutines or futures are involved.
 */
public class ScanWorker extends Worker {

    public static final String KEY_FORCE = "force";

    private static final HttpUrl INSTAGRAM = HttpUrl.get("https://www.instagram.com/");

    public ScanWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SessionStore sessionStore = new SessionStore(context);

        ScanService service = new ScanService(
                new ScanRepository(FollowLensDatabase.get(context)),
                ScanPrefs.of(context),
                Clock.SYSTEM,
                sessionId -> new IgWebClient(INSTAGRAM, sessionId, Sleeper.REAL,
                        ScanService.DELAY_SECONDS, ScanService.JITTER_SECONDS));

        ScanOutcome outcome;
        try {
            outcome = service.run(sessionStore.sessionId(),
                    getInputData().getBoolean(KEY_FORCE, false));
        } catch (RuntimeException e) {
            // A crashed worker is worse than a retried one: WorkManager marks the
            // periodic chain FAILURE and stops scanning until the app is opened
            // again. Nothing is written unless a scan commits, so retrying is safe.
            return Result.retry();
        }

        new ScanNotifier(context).notifyScan(outcome);

        if (outcome.ok()) {
            return Result.success();
        }
        return outcome.retryable() ? Result.retry() : Result.success();
    }
}

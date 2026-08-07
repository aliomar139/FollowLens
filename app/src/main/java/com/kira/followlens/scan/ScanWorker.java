package com.kira.followlens.scan;

import android.content.Context;
import android.util.Log;

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

    /** Tag used to observe scan progress from the UI. */
    public static final String TAG = "followlens-scan";

    private static final String LOG_TAG = "FollowLensScan";

    private static final HttpUrl INSTAGRAM = HttpUrl.get("https://www.instagram.com/");

    public ScanWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SessionStore sessionStore = new SessionStore(context);
        ScanStatusStore status = new ScanStatusStore(context);

        ScanService service = new ScanService(
                new ScanRepository(FollowLensDatabase.get(context)),
                ScanPrefs.of(context),
                Clock.SYSTEM,
                sessionId -> new IgWebClient(INSTAGRAM, sessionId,
                        sessionStore.csrfToken(), Sleeper.REAL,
                        ScanService.DELAY_SECONDS, ScanService.JITTER_SECONDS));

        boolean userInitiated = getInputData().getBoolean(KEY_FORCE, false);

        ScanOutcome outcome;
        try {
            outcome = service.run(sessionStore.sessionId(), userInitiated);
        } catch (RuntimeException e) {
            // A crashed worker is worse than a retried one: WorkManager marks the
            // periodic chain FAILURE and stops scanning until the app is opened
            // again. Nothing is written unless a scan commits, so retrying is safe.
            Log.w(LOG_TAG, "scan threw " + e.getClass().getSimpleName(), e);
            status.setLastError(e.getClass().getSimpleName() + ": " + e.getMessage());
            return userInitiated ? Result.failure() : Result.retry();
        }

        if (outcome.ok()) {
            Log.i(LOG_TAG, "scan committed: baseline=" + outcome.baseline()
                    + " added=" + outcome.addedFollowers()
                    + " removed=" + outcome.removedFollowers());
            status.setLastError(null);
        } else {
            // The message is the only thing that explains an idle-looking app.
            Log.w(LOG_TAG, "scan not committed: " + outcome.error());
            status.setLastError(outcome.error());
        }

        new ScanNotifier(context).notifyScan(outcome);

        if (outcome.ok()) {
            return Result.success();
        }
        // A forced scan never retries: the person who pressed Refresh gets the
        // answer now, from the stored error, rather than a silent re-queue that
        // exponential backoff can push minutes into the future.
        if (userInitiated) {
            return Result.failure();
        }
        return outcome.retryable() ? Result.retry() : Result.success();
    }
}

package com.kira.followlens.scan;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.kira.followlens.R;
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

    /**
     * Progress fields published to whoever is observing this work.
     *
     * The same numbers already drive the notification; publishing them here lets
     * the open app show the identical count instead of a second, differently
     * worded guess at the same thing.
     */
    public static final String KEY_STAGE = "stage";
    public static final String KEY_COLLECTED = "collected";

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
        ScanNotifier notifier = new ScanNotifier(context);

        ScanOutcome outcome;
        try {
            outcome = service.run(sessionStore.sessionId(), userInitiated, (stage, collected) -> {
                // A stopped worker keeps running: the sleep between pages swallows
                // the interrupt, and OkHttp does not answer to one either. Tapping
                // Refresh mid-scan REPLACEs this work, so a cancelled worker can
                // still be walking pages while its replacement runs — and posting
                // from here would put the notification back up after the live scan
                // had already taken it down.
                if (isStopped()) {
                    return;
                }
                String text = stageText(context, stage);
                notifier.showProgress(text, collected);
                setProgressAsync(new Data.Builder()
                        .putString(KEY_STAGE, text)
                        .putInt(KEY_COLLECTED, collected)
                        .build());
            });
        } catch (RuntimeException e) {
            // A crashed worker is worse than a retried one: WorkManager marks the
            // periodic chain FAILURE and stops scanning until the app is opened
            // again. Nothing is written unless a scan commits, so retrying is safe.
            Log.w(LOG_TAG, "scan threw " + e.getClass().getSimpleName(), e);
            status.setLastError(e.getClass().getSimpleName() + ": " + e.getMessage());
            return userInitiated ? Result.failure() : Result.retry();
        } finally {
            // Every exit clears it, including the throw above. An ongoing
            // notification left behind cannot be swiped away, so a crashed scan
            // would otherwise leave the app claiming to be working forever.
            notifier.clearProgress();
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

        notifier.notifyScan(outcome);

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

    /**
     * Called when WorkManager stops this worker: cancellation, a lost network
     * constraint, or the ten-minute execution limit.
     *
     * doWork()'s finally block cannot be relied on here. It runs on the worker
     * thread, which may be somewhere inside a page fetch, and the process is
     * killable from the moment the worker is stopped — so the cleanup can simply
     * never happen. onStopped runs immediately, on the main thread, at the one
     * moment the framework guarantees to tell us the work is over.
     */
    @Override
    public void onStopped() {
        super.onStopped();
        new ScanNotifier(getApplicationContext()).clearProgress();
    }

    /** The stage, in the words the notification shows. */
    private static String stageText(Context context, ScanProgress.Stage stage) {
        switch (stage) {
            case FOLLOWING:
                return context.getString(R.string.notify_stage_following);
            case FOLLOWERS:
                return context.getString(R.string.notify_stage_followers);
            case SAVING:
            default:
                return context.getString(R.string.notify_stage_saving);
        }
    }
}

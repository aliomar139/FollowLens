package com.kira.followlens.notify;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.kira.followlens.R;
import com.kira.followlens.scan.ScanOutcome;
import com.kira.followlens.ui.ChangesActivity;
import com.kira.followlens.ui.DashboardActivity;

import java.util.concurrent.TimeUnit;

/** Posts one summary notification per scan. Never one per changed account. */
public class ScanNotifier {

    private static final String CHANNEL_ID = "scan-results";
    private static final String PROGRESS_CHANNEL_ID = "scan-progress";
    private static final int NOTIFICATION_ID = 1;
    private static final int PROGRESS_NOTIFICATION_ID = 2;

    private final Context context;

    public ScanNotifier(Context context) {
        this.context = context;
    }

    public void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);

        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_scan_results),
                NotificationManager.IMPORTANCE_DEFAULT));

        // Progress is on its own channel at LOW importance, and the split is the
        // point: it updates every few seconds, so it must never make a sound or
        // push into view, and the user must be able to silence it without also
        // silencing the result they actually want.
        manager.createNotificationChannel(new NotificationChannel(
                PROGRESS_CHANNEL_ID,
                context.getString(R.string.channel_scan_progress),
                NotificationManager.IMPORTANCE_LOW));
    }

    /**
     * Updates the ongoing "scanning" notification.
     *
     * The bar is indeterminate because the total is genuinely unknown until the
     * last page arrives: the endpoint reports no count, only whether there is
     * another page. A fake percentage would be a lie that runs backwards when it
     * turns out to be wrong, so the running account total carries the progress
     * instead — it only ever goes up, and it is a number the user can check.
     */
    public void showProgress(String stageText, int collected) {
        if (!canPost()) {
            return;
        }
        ensureChannel();

        PendingIntent tap = PendingIntent.getActivity(
                context,
                1,
                new Intent(context, DashboardActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                // Tints the icon and the app name in the shade. The platform
                // sync glyph that was here belonged to Android, not to this app.
                .setColor(ContextCompat.getColor(context, R.color.accent))
                .setContentTitle(context.getString(R.string.notify_scanning))
                .setContentText(context.getString(R.string.notify_scan_progress,
                        stageText, collected))
                .setProgress(0, 0, true)
                .setContentIntent(tap)
                .setOngoing(true)
                // An ongoing notification cannot be swiped away, so if this one
                // is ever left behind the user has no way to remove it short of
                // force-stopping the app — which is exactly what happened. Its
                // removal must not depend only on this app's code running to
                // completion, because a worker is not guaranteed to get there:
                // WorkManager stops one after ten minutes and the process is
                // killable from that moment on, taking any pending cleanup with
                // it. The timeout is the floor under all of that — slightly
                // above the ten-minute ceiling, so a live scan is never cut off
                // by it, and a stranded one clears itself.
                .setTimeoutAfter(TimeUnit.MINUTES.toMillis(11))
                .setSilent(true)
                // Without this every update re-alerts, and a scan would buzz a
                // dozen times on its way through the followers list.
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        NotificationManagerCompat.from(context)
                .notify(PROGRESS_NOTIFICATION_ID, notification);
    }

    /**
     * Takes the progress notification down. Called however the scan ends —
     * success, failure or a thrown exception — because an ongoing notification
     * that outlives its work cannot be dismissed by the user.
     */
    public void clearProgress() {
        NotificationManagerCompat.from(context).cancel(PROGRESS_NOTIFICATION_ID);
    }

    /**
     * POST_NOTIFICATIONS only exists from API 33. Checking it on 26 to 32 would
     * be meaningless, so this only gates where the permission is real.
     */
    private boolean canPost() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context,
                        Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
    }

    public void notifyScan(ScanOutcome outcome) {
        if (!outcome.ok() || outcome.baseline()) {
            return;
        }
        String text = ScanSummary.textFor(outcome.addedFollowers(), outcome.removedFollowers());
        if (text == null) {
            return;
        }
        if (!canPost()) {
            return;
        }

        ensureChannel();

        PendingIntent tap = PendingIntent.getActivity(
                context,
                0,
                new Intent(context, ChangesActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                // Tints the icon and the app name in the shade. The platform
                // sync glyph that was here belonged to Android, not to this app.
                .setColor(ContextCompat.getColor(context, R.color.accent))
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build();

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification);
    }
}

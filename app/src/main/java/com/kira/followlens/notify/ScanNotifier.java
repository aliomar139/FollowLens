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

/** Posts one summary notification per scan. Never one per changed account. */
public class ScanNotifier {

    private static final String CHANNEL_ID = "scan-results";
    private static final int NOTIFICATION_ID = 1;

    private final Context context;

    public ScanNotifier(Context context) {
        this.context = context;
    }

    public void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_scan_results),
                NotificationManager.IMPORTANCE_DEFAULT);
        context.getSystemService(NotificationManager.class)
                .createNotificationChannel(channel);
    }

    public void notifyScan(ScanOutcome outcome) {
        if (!outcome.ok() || outcome.baseline()) {
            return;
        }
        String text = ScanSummary.textFor(outcome.addedFollowers(), outcome.removedFollowers());
        if (text == null) {
            return;
        }
        // POST_NOTIFICATIONS only exists from API 33. Checking it on 26 to 32
        // would be meaningless, so only gate where the permission is real.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context,
                        Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
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
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build();

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification);
    }
}

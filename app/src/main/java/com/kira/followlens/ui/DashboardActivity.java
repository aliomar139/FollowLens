package com.kira.followlens.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kira.followlens.R;
import com.kira.followlens.auth.LoginActivity;
import com.kira.followlens.auth.SessionStore;
import com.kira.followlens.data.FollowLensDao;
import com.kira.followlens.data.FollowLensDatabase;
import com.kira.followlens.notify.ScanNotifier;
import com.kira.followlens.scan.ScanScheduler;

import java.text.DateFormat;
import java.util.Date;

public class DashboardActivity extends AppCompatActivity {

    private SessionStore sessionStore;
    private boolean permissionAsked;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        sessionStore = new SessionStore(this);
        if (!sessionStore.hasSession()) {
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }

        new ScanNotifier(this).ensureChannel();
        ScanScheduler.schedulePeriodic(this);

        String accountId = sessionStore.userId();
        FollowLensDao dao = FollowLensDatabase.get(this).dao();

        TextView counts = findViewById(R.id.counts);
        TextView lastScan = findViewById(R.id.last_scan);

        dao.latestScan(accountId).observe(this, scan -> {
            if (scan == null) {
                counts.setText(R.string.no_scan_yet);
                lastScan.setText("");
                return;
            }
            counts.setText(getString(R.string.counts_format,
                    scan.followersCount, scan.followingCount));
            lastScan.setText(getString(R.string.last_scan_format,
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(new Date(scan.finishedAt))));
            requestNotificationPermissionOnce();
        });

        EdgeAdapter adapter = new EdgeAdapter();
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        dao.notFollowingBack(accountId).observe(this, adapter::submit);

        findViewById(R.id.view_changes).setOnClickListener(v ->
                startActivity(new Intent(this, ChangesActivity.class)));

        Button refresh = findViewById(R.id.refresh);
        refresh.setOnClickListener(v -> ScanScheduler.requestOneOff(this));
    }

    /**
     * Asked only after a scan exists, so the prompt arrives with visible context
     * rather than on first launch. The flag matters because the LiveData
     * observer fires on every scan and this must not re-prompt each time.
     */
    private void requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || permissionAsked) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        permissionAsked = true;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
    }
}

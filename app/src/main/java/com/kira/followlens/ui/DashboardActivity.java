package com.kira.followlens.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.WorkInfo;

import com.kira.followlens.R;
import com.kira.followlens.auth.SessionActivity;
import com.kira.followlens.auth.SessionStore;
import com.kira.followlens.data.EdgeEntity;
import com.kira.followlens.data.FollowLensDao;
import com.kira.followlens.data.FollowLensDatabase;
import com.kira.followlens.data.ScanEntity;
import com.kira.followlens.notify.ScanNotifier;
import com.kira.followlens.scan.ScanScheduler;
import com.kira.followlens.scan.ScanState;
import com.kira.followlens.scan.ScanStatus;
import com.kira.followlens.scan.ScanStatusStore;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class DashboardActivity extends AppCompatActivity {

    private SessionStore sessionStore;
    private ScanStatusStore statusStore;
    private boolean permissionAsked;

    private TextView status;
    private TextView statFollowers;
    private TextView statFollowing;
    private TextView statMutuals;
    private TextView listCount;
    private TextView listEmpty;
    private ProgressBar progress;
    private Button refresh;

    // Latest known values, combined into one status line by render().
    private boolean scanRunning;
    private boolean scanQueued;
    private ScanEntity latestScan;
    private boolean hasSecondScan;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        sessionStore = new SessionStore(this);
        if (!sessionStore.hasSession()) {
            startActivity(new Intent(this, SessionActivity.class));
            finish();
            return;
        }
        statusStore = new ScanStatusStore(this);

        new ScanNotifier(this).ensureChannel();
        ScanScheduler.schedulePeriodic(this);

        status = findViewById(R.id.status);
        statFollowers = findViewById(R.id.stat_followers);
        statFollowing = findViewById(R.id.stat_following);
        statMutuals = findViewById(R.id.stat_mutuals);
        listCount = findViewById(R.id.list_count);
        listEmpty = findViewById(R.id.list_empty);
        progress = findViewById(R.id.scan_progress);
        refresh = findViewById(R.id.refresh);

        String accountId = sessionStore.userId();
        FollowLensDao dao = FollowLensDatabase.get(this).dao();

        setUpStats(dao, accountId);
        setUpList(dao, accountId);
        setUpActions();
        observeScanProgress();

        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The error message lives in preferences, which are not observable, so
        // re-read it whenever the screen comes back to the foreground.
        if (statusStore != null) {
            render();
        }
    }

    private void setUpStats(FollowLensDao dao, String accountId) {
        dao.latestScan(accountId).observe(this, scan -> {
            latestScan = scan;
            if (scan == null) {
                statFollowers.setText(R.string.dash_placeholder);
                statFollowing.setText(R.string.dash_placeholder);
            } else {
                statFollowers.setText(String.valueOf(scan.followersCount));
                statFollowing.setText(String.valueOf(scan.followingCount));
                requestNotificationPermissionOnce();
            }
            render();
        });

        dao.mutuals(accountId).observe(this, mutuals ->
                statMutuals.setText(mutuals == null
                        ? getString(R.string.dash_placeholder)
                        : String.valueOf(mutuals.size())));

        dao.scanCountLive(accountId).observe(this, count -> {
            hasSecondScan = count != null && count > 1;
            render();
        });
    }

    private void setUpList(FollowLensDao dao, String accountId) {
        EdgeAdapter adapter = new EdgeAdapter();
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        ListView[] views = ListView.values();
        String[] labels = new String[views.length];
        for (int i = 0; i < views.length; i++) {
            labels[i] = getString(views[i].labelRes());
        }

        Spinner selector = findViewById(R.id.list_selector);
        selector.setAdapter(new ArrayAdapter<>(this, R.layout.item_spinner, labels));
        selector.setSelection(ListView.NOT_FOLLOWING_BACK.ordinal());

        selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private LiveData<List<EdgeEntity>> current;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Detach the previous query or every switch leaves an observer
                // behind and the list starts flickering between datasets.
                if (current != null) {
                    current.removeObservers(DashboardActivity.this);
                }
                current = views[position].query(dao, accountId);
                current.observe(DashboardActivity.this, edges -> {
                    adapter.submit(edges);
                    showListState(edges == null ? 0 : edges.size());
                });
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void showListState(int count) {
        listCount.setText(getString(R.string.list_count_format, count));
        if (count > 0) {
            listEmpty.setVisibility(View.GONE);
            return;
        }
        listEmpty.setText(latestScan == null
                ? R.string.list_empty_no_scan
                : R.string.list_empty_none);
        listEmpty.setVisibility(View.VISIBLE);
    }

    private void setUpActions() {
        refresh.setOnClickListener(v -> {
            ScanScheduler.requestOneOff(this);
            // Reflect the tap immediately; WorkInfo takes a moment to report back.
            scanQueued = true;
            render();
        });

        findViewById(R.id.view_changes).setOnClickListener(v ->
                startActivity(new Intent(this, ChangesActivity.class)));
    }

    private void observeScanProgress() {
        ScanScheduler.allScans(this).observe(this, infos -> {
            scanRunning = false;
            if (infos != null) {
                for (WorkInfo info : infos) {
                    if (info.getState() == WorkInfo.State.RUNNING) {
                        scanRunning = true;
                        break;
                    }
                }
            }
            render();
        });

        ScanScheduler.manualScan(this).observe(this, infos -> {
            scanQueued = false;
            if (infos != null) {
                for (WorkInfo info : infos) {
                    if (info.getState() == WorkInfo.State.ENQUEUED) {
                        scanQueued = true;
                        break;
                    }
                }
            }
            render();
        });
    }

    /** Collapses every input into the one status line and the progress bar. */
    private void render() {
        if (status == null || statusStore == null) {
            return;
        }

        String lastError = statusStore.lastError();
        ScanState state = ScanStatus.of(scanRunning, scanQueued, latestScan != null,
                !hasSecondScan, lastError);

        boolean busy = state == ScanState.SCANNING || state == ScanState.QUEUED;
        progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        refresh.setText(busy ? R.string.refresh_running : R.string.refresh);

        // Disabled only while a scan is genuinely RUNNING. Disabling on QUEUED
        // deadlocks the screen: a manual scan left enqueued in backoff by an
        // earlier session keeps the state "queued" forever, and tapping Refresh
        // is the only thing that would REPLACE it.
        refresh.setEnabled(state != ScanState.SCANNING);

        int colour = R.color.text_secondary;
        String text;
        switch (state) {
            case SCANNING:
                text = getString(R.string.status_scanning);
                break;
            case QUEUED:
                text = getString(R.string.status_queued);
                break;
            case NO_SCAN:
                text = getString(R.string.status_no_scan);
                break;
            case FAILED:
                text = getString(R.string.status_failed, lastError);
                colour = R.color.warning;
                break;
            case BASELINE:
                text = getString(R.string.status_baseline, when(latestScan));
                break;
            case OK:
            default:
                text = getString(R.string.status_last_scan, when(latestScan));
                break;
        }
        status.setText(text);
        status.setTextColor(ContextCompat.getColor(this, colour));
    }

    private String when(ScanEntity scan) {
        if (scan == null) {
            return "";
        }
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(scan.finishedAt));
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

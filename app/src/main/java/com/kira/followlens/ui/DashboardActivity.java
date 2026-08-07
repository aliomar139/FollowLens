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
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kira.followlens.R;
import com.kira.followlens.auth.SessionActivity;
import com.kira.followlens.auth.SessionStore;
import com.kira.followlens.data.EdgeEntity;
import com.kira.followlens.data.FollowLensDao;
import com.kira.followlens.data.FollowLensDatabase;
import com.kira.followlens.notify.ScanNotifier;
import com.kira.followlens.scan.ScanScheduler;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class DashboardActivity extends AppCompatActivity {

    private SessionStore sessionStore;
    private boolean permissionAsked;

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

        ListView[] views = ListView.values();
        String[] labels = new String[views.length];
        for (int i = 0; i < views.length; i++) {
            labels[i] = getString(views[i].labelRes());
        }

        Spinner selector = findViewById(R.id.list_selector);
        selector.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
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
                current.observe(DashboardActivity.this, adapter::submit);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

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

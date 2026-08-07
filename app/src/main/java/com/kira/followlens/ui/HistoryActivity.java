package com.kira.followlens.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kira.followlens.R;
import com.kira.followlens.auth.SessionStore;
import com.kira.followlens.data.FollowLensDatabase;
import com.kira.followlens.data.ScanEntity;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Follower count over time: a stat tile with a trend, then every scan. */
public class HistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        String accountId = new SessionStore(this).userId();
        if (accountId == null) {
            finish();
            return;
        }

        TextView heroValue = findViewById(R.id.hero_value);
        TextView heroDelta = findViewById(R.id.hero_delta);
        TextView range = findViewById(R.id.sparkline_range);
        SparklineView sparkline = findViewById(R.id.sparkline);
        View empty = findViewById(R.id.scans_empty);

        ScanAdapter adapter = new ScanAdapter();
        RecyclerView scans = findViewById(R.id.scans);
        scans.setLayoutManager(new LinearLayoutManager(this));
        scans.setAdapter(adapter);

        FollowLensDatabase.get(this).dao().scanHistory(accountId).observe(this, history -> {
            adapter.submit(history);

            boolean none = history == null || history.isEmpty();
            empty.setVisibility(none ? View.VISIBLE : View.GONE);
            if (none) {
                heroValue.setText(R.string.dash_placeholder);
                heroDelta.setText("");
                range.setText("");
                sparkline.setValues(new int[0]);
                return;
            }

            // The query returns newest first; a trend line has to read oldest to
            // newest, so reverse it for the chart.
            int[] chronological = new int[history.size()];
            for (int i = 0; i < history.size(); i++) {
                chronological[i] = history.get(history.size() - 1 - i).followersCount;
            }
            sparkline.setValues(chronological);

            ScanEntity newest = history.get(0);
            heroValue.setText(String.valueOf(newest.followersCount));

            if (history.size() > 1) {
                int delta = newest.followersCount - history.get(1).followersCount;
                heroDelta.setText(ScanAdapter.deltaLabel(newest.followersCount,
                        history.get(1).followersCount));
                heroDelta.setTextColor(ContextCompat.getColor(this, delta > 0
                        ? R.color.positive : delta < 0 ? R.color.negative
                        : R.color.text_disabled));
            } else {
                heroDelta.setText("");
            }

            ScanEntity oldest = history.get(history.size() - 1);
            DateFormat when = DateFormat.getDateInstance(DateFormat.MEDIUM);
            range.setText(history.size() == 1
                    ? getString(R.string.history_single_scan)
                    : getString(R.string.history_range_format, history.size(),
                            when.format(new Date(oldest.finishedAt)),
                            when.format(new Date(newest.finishedAt))));
        });
    }
}

package com.kira.followlens.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.WorkInfo;

import com.kira.followlens.R;
import com.kira.followlens.auth.SessionActivity;
import com.kira.followlens.auth.SessionStore;
import com.kira.followlens.data.EdgeRow;
import com.kira.followlens.data.FollowLensDao;
import com.kira.followlens.data.FollowLensDatabase;
import com.kira.followlens.data.ScanEntity;
import com.kira.followlens.notify.ScanNotifier;
import com.kira.followlens.scan.ScanScheduler;
import com.kira.followlens.scan.ScanState;
import com.kira.followlens.scan.ScanStatus;
import com.kira.followlens.scan.ScanStatusStore;
import com.kira.followlens.scan.ScanWorker;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class DashboardActivity extends AppCompatActivity {

    private SessionStore sessionStore;
    private ScanStatusStore statusStore;
    private boolean permissionAsked;

    private TextView status;
    private StatCard statFollowers;
    private StatCard statFollowing;
    private StatCard statMutuals;
    private TextView listCount;
    private TextView listEmpty;
    private ProgressBar progress;
    private View refresh;
    private TextView refreshLabel;
    private View refreshIcon;
    private LinearLayout segments;
    private RecyclerView list;
    private View bottomBar;
    private View scrollFade;
    private EditText search;
    private ImageButton searchClear;
    private EdgeAdapter listAdapter;
    private List<EdgeRow> loadedRows;

    /**
     * The query feeding the list right now. Held so switching lists can detach
     * it: leaving the previous observer attached makes the list flicker between
     * two datasets.
     */
    private LiveData<List<EdgeRow>> currentQuery;

    // Latest known values, combined into one status line by render().
    private boolean scanRunning;
    private boolean scanQueued;
    private ScanEntity latestScan;
    private boolean hasSecondScan;

    /** How far the running scan has got, as published by the worker. */
    private int scanCollected;

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
        refreshLabel = findViewById(R.id.refresh_label);
        refreshIcon = findViewById(R.id.refresh_icon);
        bottomBar = findViewById(R.id.bottom_bar);
        scrollFade = findViewById(R.id.scroll_fade);

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
        dao.latestTwoScans(accountId).observe(this, scans -> {
            latestScan = scans == null || scans.isEmpty() ? null : scans.get(0);
            // The second row is the scan before, when there is one. Both tiles
            // read from the same pair so their deltas always describe the same
            // step, never one from this scan and one from the last.
            ScanEntity previous = scans != null && scans.size() > 1 ? scans.get(1) : null;

            statFollowers.setValue(
                    latestScan == null ? null : latestScan.followersCount,
                    previous == null ? null : previous.followersCount);
            statFollowing.setValue(
                    latestScan == null ? null : latestScan.followingCount,
                    previous == null ? null : previous.followingCount);

            if (latestScan != null) {
                requestNotificationPermissionOnce();
            }
            render();
        });

        // Mutuals are derived from the current graph rather than stored per
        // scan, so there is no previous value to compare against and the tile
        // carries no delta. Inventing one from change events would be a guess.
        dao.mutuals(accountId).observe(this, mutuals ->
                statMutuals.setValue(mutuals == null ? null : mutuals.size(), null));

        dao.scanCountLive(accountId).observe(this, count -> {
            hasSecondScan = count != null && count > 1;
            render();
        });
    }

    private void setUpList(FollowLensDao dao, String accountId) {
        EdgeAdapter adapter = new EdgeAdapter();
        list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        // Rows arrive rather than appearing, which makes a list switch legible
        // as a change of content instead of a flash of new text.
        if (!Motion.reduced(this)) {
            list.setLayoutAnimation(
                    AnimationUtils.loadLayoutAnimation(this, R.anim.layout_rows));
        }
        watchScroll();

        ListView[] views = ListView.values();
        CharSequence[] labels = new CharSequence[views.length];
        for (int i = 0; i < views.length; i++) {
            labels[i] = getString(views[i].labelRes());
        }

        segments = findViewById(R.id.list_segments);
        Segmented.install(segments, labels, ListView.NOT_FOLLOWING_BACK.ordinal(), false,
                index -> showList(dao, accountId, views[index]));
        showList(dao, accountId, ListView.NOT_FOLLOWING_BACK);

        // The tiles select the list they count. Reading "812 followers" and
        // then hunting for the Followers segment is a step the interface can
        // take for you.
        statFollowers.setOnCardClickListener(v -> selectList(dao, accountId, ListView.FOLLOWERS));
        statFollowing.setOnCardClickListener(v -> selectList(dao, accountId, ListView.FOLLOWING));
        statMutuals.setOnCardClickListener(v -> selectList(dao, accountId, ListView.MUTUALS));

        listAdapter = adapter;
        search = findViewById(R.id.search);
        searchClear = findViewById(R.id.search_clear);
        searchClear.setOnClickListener(v -> search.setText(""));
        // Filtering is live, so the keyboard has nothing left to submit by the
        // time the user reaches for Search. Dismissing it hands the screen back.
        search.setOnEditorActionListener((v, actionId, event) -> {
            search.clearFocus();
            getSystemService(InputMethodManager.class)
                    .hideSoftInputFromWindow(search.getWindowToken(), 0);
            return true;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    /**
     * Moves the segmented control and the list together.
     *
     * Selecting a segment programmatically does not fire its listener — that
     * would make a user tap and a tile tap take different paths through this
     * class — so both steps are named here.
     */
    private void selectList(FollowLensDao dao, String accountId, ListView view) {
        Segmented.select(segments, view.ordinal());
        showList(dao, accountId, view);
        list.scrollToPosition(0);
        // The chrome may have been scrolled away; a new list starts at the top,
        // so the actions come back with it.
        showBottomBar();
    }

    /** Points the list at one query, dropping whichever one it was showing. */
    private void showList(FollowLensDao dao, String accountId, ListView view) {
        if (currentQuery != null) {
            currentQuery.removeObservers(this);
        }
        currentQuery = view.query(dao, accountId);
        currentQuery.observe(this, rows -> {
            loadedRows = rows;
            applyFilter();
        });
    }

    /**
     * The actions get out of the way while the list is being read, and come
     * back the moment the user reaches back up.
     *
     * Direction, not position, decides it: scrolling down means "I am reading",
     * scrolling up means "I am looking for something", and the bar belongs to
     * the second of those. The threshold keeps a jittery finger from flickering
     * it, and the fade only exists while there is content behind the bar.
     */
    private void watchScroll() {
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private static final int THRESHOLD_DP = 6;

            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                int threshold = Math.round(THRESHOLD_DP
                        * getResources().getDisplayMetrics().density);
                if (dy > threshold && view.canScrollVertically(1)) {
                    hideBottomBar();
                } else if (dy < -threshold) {
                    showBottomBar();
                }
                // No content underneath means nothing to soften the edge of.
                scrollFade.setVisibility(view.canScrollVertically(1)
                        ? View.VISIBLE : View.INVISIBLE);
            }
        });
    }

    private void hideBottomBar() {
        moveBottomBar(bottomBar.getHeight());
    }

    private void showBottomBar() {
        moveBottomBar(0f);
    }

    /** The fade travels with the bar; it exists to soften that bar's edge. */
    private void moveBottomBar(float translation) {
        Motion.springTo(bottomBar, DynamicAnimation.TRANSLATION_Y, translation,
                Motion.DAMPING_SMOOTH, Motion.RESPONSE_STANDARD);
        Motion.springTo(scrollFade, DynamicAnimation.TRANSLATION_Y, translation,
                Motion.DAMPING_SMOOTH, Motion.RESPONSE_STANDARD);
    }

    /** Re-applies the search box to whichever list is currently loaded. */
    private void applyFilter() {
        if (listAdapter == null) {
            return;
        }
        String query = search == null ? "" : search.getText().toString();
        // The clear affordance only exists while there is something to clear;
        // a permanently visible one is a control that does nothing most of the time.
        if (searchClear != null) {
            searchClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
        }
        List<EdgeRow> visible = AccountFilter.matching(loadedRows, query);
        listAdapter.submit(visible);
        showListState(visible.size(), query);
    }

    private void showListState(int count, String query) {
        listCount.setText(getString(R.string.list_count_format, count));
        if (count > 0) {
            listEmpty.setVisibility(View.GONE);
            return;
        }
        // A search that matched nothing is a different situation from a list that
        // is genuinely empty, and saying so avoids "the app lost my followers".
        if (query != null && !query.trim().isEmpty()) {
            listEmpty.setText(getString(R.string.list_empty_search, query.trim()));
            listEmpty.setVisibility(View.VISIBLE);
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
            // A scan is a commit: it goes out to the network and writes history.
            // One tick marks the moment it was accepted.
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            // Reflect the tap immediately; WorkInfo takes a moment to report back.
            scanQueued = true;
            scanCollected = 0;
            render();
        });

        View changes = findViewById(R.id.view_changes);
        View history = findViewById(R.id.view_history);
        View account = findViewById(R.id.account);

        changes.setOnClickListener(v ->
                startActivity(new Intent(this, ChangesActivity.class)));

        history.setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));

        account.setOnClickListener(v -> {
            startActivity(new Intent(this, AccountActivity.class));
            // The popover animates itself out of this button; the push
            // animation would slide the whole dashboard away underneath it.
            overridePendingTransition(0, 0);
        });

        Press.applyTo(refresh, changes, history, account);
    }

    private void observeScanProgress() {
        ScanScheduler.allScans(this).observe(this, infos -> {
            boolean wasRunning = scanRunning;
            scanRunning = false;
            if (infos != null) {
                for (WorkInfo info : infos) {
                    if (info.getState() == WorkInfo.State.RUNNING) {
                        scanRunning = true;
                        // The worker publishes the same running total the
                        // notification shows, so the two never disagree.
                        scanCollected = info.getProgress().getInt(ScanWorker.KEY_COLLECTED, 0);
                        break;
                    }
                }
            }
            if (wasRunning && !scanRunning) {
                // The finish is the meaningful moment, not the start: this is
                // when the numbers on screen are worth looking at again.
                refresh.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
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

        // The button says what it is doing and how far it has got. Until the
        // first page lands there is no count to show, so it stays on the word.
        if (busy && scanCollected > 0) {
            refreshLabel.setText(getString(R.string.refresh_counting, scanCollected));
        } else {
            refreshLabel.setText(busy ? R.string.refresh_running : R.string.refresh);
        }
        spinIcon(busy);

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

    /**
     * Turns the scan glyph while a scan runs.
     *
     * One slow revolution, linear, because a rotation that eases would read as
     * stopping and starting — and this is the one animation in the app that is
     * genuinely constant motion rather than a transition between two states.
     */
    private void spinIcon(boolean spinning) {
        if (spinning == (refreshIcon.getAnimation() != null)) {
            return;
        }
        if (!spinning || Motion.reduced(this)) {
            refreshIcon.clearAnimation();
            return;
        }
        RotateAnimation rotate = new RotateAnimation(0f, 360f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(2000);
        rotate.setRepeatCount(Animation.INFINITE);
        rotate.setInterpolator(new LinearInterpolator());
        refreshIcon.startAnimation(rotate);
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

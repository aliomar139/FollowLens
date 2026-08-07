package com.kira.followlens.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kira.followlens.R;
import com.kira.followlens.auth.SessionStore;
import com.kira.followlens.data.ChangeEventEntity;
import com.kira.followlens.data.FollowLensDatabase;

import java.util.List;

public class ChangesActivity extends AppCompatActivity {

    private ChangeAdapter adapter;
    private View empty;
    private TextView emptyTitle;
    private ChangeFilter.Mode mode = ChangeFilter.Mode.ALL;
    private List<ChangeEventEntity> loaded;

    private Button filterAll;
    private Button filterNew;
    private Button filterLost;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_changes);

        String accountId = new SessionStore(this).userId();
        if (accountId == null) {
            finish();
            return;
        }

        adapter = new ChangeAdapter();
        RecyclerView list = findViewById(R.id.changes);
        empty = findViewById(R.id.changes_empty);
        emptyTitle = findViewById(R.id.changes_empty_title);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        filterAll = findViewById(R.id.filter_all);
        filterNew = findViewById(R.id.filter_new);
        filterLost = findViewById(R.id.filter_lost);
        filterAll.setOnClickListener(v -> select(ChangeFilter.Mode.ALL));
        filterNew.setOnClickListener(v -> select(ChangeFilter.Mode.ADDED_ONLY));
        filterLost.setOnClickListener(v -> select(ChangeFilter.Mode.REMOVED_ONLY));
        markSelected();

        FollowLensDatabase.get(this).dao().changeFeed(accountId).observe(this, events -> {
            loaded = events;
            render();
        });
    }

    private void select(ChangeFilter.Mode next) {
        mode = next;
        markSelected();
        render();
    }

    /**
     * The active filter is shown by enabling state rather than colour alone, so
     * which one is selected does not depend on seeing the accent.
     */
    private void markSelected() {
        filterAll.setEnabled(mode != ChangeFilter.Mode.ALL);
        filterNew.setEnabled(mode != ChangeFilter.Mode.ADDED_ONLY);
        filterLost.setEnabled(mode != ChangeFilter.Mode.REMOVED_ONLY);
    }

    private void render() {
        List<ChangeEventEntity> visible = ChangeFilter.matching(loaded, mode);
        adapter.submit(visible);

        if (!visible.isEmpty()) {
            empty.setVisibility(View.GONE);
            return;
        }
        // Distinguish "no changes at all" from "no changes of this kind", so a
        // filter that hides everything does not look like data loss.
        boolean feedHasAnything = loaded != null && !loaded.isEmpty();
        emptyTitle.setText(feedHasAnything
                ? R.string.changes_empty_filtered
                : R.string.changes_empty_title);
        empty.setVisibility(View.VISIBLE);
    }
}

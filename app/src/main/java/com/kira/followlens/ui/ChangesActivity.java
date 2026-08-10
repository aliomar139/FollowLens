package com.kira.followlens.ui;

import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
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

    /** Segment order, so an index maps back to a filter without a switch. */
    private static final ChangeFilter.Mode[] MODES = {
            ChangeFilter.Mode.ALL,
            ChangeFilter.Mode.ADDED_ONLY,
            ChangeFilter.Mode.REMOVED_ONLY,
    };

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

        if (!Motion.reduced(this)) {
            list.setLayoutAnimation(
                    AnimationUtils.loadLayoutAnimation(this, R.anim.layout_rows));
        }

        CharSequence[] labels = {
                getString(R.string.filter_all),
                getString(R.string.filter_new),
                getString(R.string.filter_lost),
        };
        Segmented.install(findViewById(R.id.filter_segments), labels, 0, true,
                index -> select(MODES[index]));

        View back = findViewById(R.id.back);
        back.setOnClickListener(v -> finish());
        Press.applyTo(back);

        FollowLensDatabase.get(this).dao().changeFeed(accountId).observe(this, events -> {
            loaded = events;
            render();
        });
    }

    private void select(ChangeFilter.Mode next) {
        mode = next;
        render();
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

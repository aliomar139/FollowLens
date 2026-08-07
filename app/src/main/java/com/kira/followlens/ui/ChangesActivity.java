package com.kira.followlens.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kira.followlens.R;
import com.kira.followlens.auth.SessionStore;
import com.kira.followlens.data.FollowLensDatabase;

public class ChangesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_changes);

        String accountId = new SessionStore(this).userId();
        if (accountId == null) {
            finish();
            return;
        }

        ChangeAdapter adapter = new ChangeAdapter();
        RecyclerView list = findViewById(R.id.changes);
        View empty = findViewById(R.id.changes_empty);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        FollowLensDatabase.get(this).dao().changeFeed(accountId).observe(this, events -> {
            adapter.submit(events);
            // A baseline scan legitimately has no changes. Saying so is the
            // difference between "nothing happened yet" and "this screen is broken".
            boolean isEmpty = events == null || events.isEmpty();
            empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        });
    }
}

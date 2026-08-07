package com.kira.followlens.ui;

import android.os.Bundle;

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
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        FollowLensDatabase.get(this).dao().changeFeed(accountId)
                .observe(this, adapter::submit);
    }
}

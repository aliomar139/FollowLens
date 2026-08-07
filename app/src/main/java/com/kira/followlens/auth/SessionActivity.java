package com.kira.followlens.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.kira.followlens.R;
import com.kira.followlens.ui.DashboardActivity;

/**
 * Takes a session key pasted from a browser where the user is already logged in.
 *
 * This deliberately replaces an in-app login form. Signing in from a new device
 * is what triggers Instagram's suspicious-login checkpoint; a session created in
 * a browser the account already trusts does not. No password is ever typed into
 * this app, and the app never sees the account's credentials.
 */
public class SessionActivity extends AppCompatActivity {

    private SessionStore sessionStore;
    private EditText input;
    private TextView error;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session);

        sessionStore = new SessionStore(this);
        input = findViewById(R.id.session_input);
        error = findViewById(R.id.session_error);

        Button save = findViewById(R.id.session_save);
        save.setOnClickListener(v -> save());
    }

    private void save() {
        String normalized = SessionInput.normalize(input.getText().toString());
        if (normalized == null) {
            showError(getString(R.string.session_invalid));
            return;
        }

        try {
            sessionStore.save(normalized);
        } catch (IllegalArgumentException e) {
            // Defensive: normalize() already rejects this shape.
            showError(getString(R.string.session_invalid));
            return;
        }

        // Clear the field so the key is not left sitting on screen.
        input.setText("");
        error.setVisibility(View.GONE);
        Toast.makeText(this, R.string.session_saved, Toast.LENGTH_SHORT).show();

        startActivity(new Intent(this, DashboardActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    private void showError(String message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
    }
}

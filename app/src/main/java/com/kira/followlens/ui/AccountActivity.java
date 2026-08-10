package com.kira.followlens.ui;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;

import com.kira.followlens.R;
import com.kira.followlens.auth.SessionActivity;
import com.kira.followlens.auth.SessionStore;
import com.kira.followlens.data.FollowLensDao;
import com.kira.followlens.data.FollowLensDatabase;
import com.kira.followlens.data.ScanEntity;

import java.text.DateFormat;
import java.util.Date;

/**
 * Who is signed in, what this app knows about them, and the two ways out:
 * swap the session for another one, or sign out entirely.
 *
 * It is a popover anchored to the button that opens it rather than a screen of
 * its own. Everything here is a glance or a single decision, and pushing a
 * whole screen for that loses the place the user was standing.
 */
public class AccountActivity extends AppCompatActivity {

    /** Where the panel grows from. Matches the trigger in the dashboard header. */
    private static final float ORIGIN_SCALE = 0.9f;

    /** Tints for the avatar disc, one per Monogram.TINT_COUNT slot. */
    private static final int[] TINTS = {
            R.color.tint_1, R.color.tint_2, R.color.tint_3,
            R.color.tint_4, R.color.tint_5, R.color.tint_6,
    };

    private View scrim;
    private View sheet;
    private boolean dismissing;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        SessionStore sessionStore = new SessionStore(this);
        String accountId = sessionStore.userId();
        if (accountId == null) {
            finish();
            return;
        }

        scrim = findViewById(R.id.scrim);
        sheet = findViewById(R.id.sheet);
        scrim.setOnClickListener(v -> dismiss());

        bindIdentity(accountId);
        bindFacts(accountId, sessionStore.csrfToken() != null);
        bindActions();
        animateIn();
    }

    private void bindIdentity(String accountId) {
        ImageView avatar = findViewById(R.id.account_avatar);
        avatar.getBackground().mutate().setColorFilter(
                ContextCompat.getColor(this, TINTS[Monogram.tintIndexOf(accountId)]),
                PorterDuff.Mode.SRC_IN);

        ((TextView) findViewById(R.id.account_name))
                .setText(getString(R.string.account_id_format, accountId));
        // The key itself is a live credential: only its leading user id, which
        // is already shown above, is ever rendered.
        ((TextView) findViewById(R.id.account_session))
                .setText(getString(R.string.account_session_masked, accountId));
    }

    private void bindFacts(String accountId, boolean hasCsrf) {
        LinearLayout facts = findViewById(R.id.account_facts);
        FollowLensDao dao = FollowLensDatabase.get(this).dao();

        TextView scans = addFact(facts, getString(R.string.account_fact_scans));
        TextView lastScan = addFact(facts, getString(R.string.account_fact_last_scan));
        TextView followers = addFact(facts, getString(R.string.account_fact_followers));
        TextView following = addFact(facts, getString(R.string.account_fact_following));
        TextView csrf = addFact(facts, getString(R.string.account_fact_csrf));

        csrf.setText(hasCsrf
                ? R.string.account_fact_csrf_present
                : R.string.account_fact_csrf_missing);
        if (!hasCsrf) {
            // A missing token is the usual cause of a scan being throttled, so
            // it is stated in the warning colour rather than left as a fact.
            csrf.setTextColor(ContextCompat.getColor(this, R.color.warning));
        }

        dao.scanCountLive(accountId).observe(this, count ->
                scans.setText(count == null ? getString(R.string.account_fact_none)
                        : String.valueOf(count)));

        dao.latestScan(accountId).observe(this, scan -> {
            if (scan == null) {
                lastScan.setText(R.string.account_fact_none);
                followers.setText(R.string.account_fact_none);
                following.setText(R.string.account_fact_none);
                return;
            }
            lastScan.setText(when(scan));
            followers.setText(String.valueOf(scan.followersCount));
            following.setText(String.valueOf(scan.followingCount));
        });
    }

    /** Adds one label/value row and returns the value view to fill in later. */
    private TextView addFact(LinearLayout container, String label) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_fact, container, false);
        ((TextView) row.findViewById(R.id.fact_label)).setText(label);
        container.addView(row);
        return row.findViewById(R.id.fact_value);
    }

    private void bindActions() {
        View switchSession = findViewById(R.id.switch_session);
        View signOut = findViewById(R.id.sign_out);

        switchSession.setOnClickListener(v -> {
            // The session screen overwrites whatever is stored, so switching is
            // the same act as pasting the first one. No sign-out step in between.
            startActivity(new Intent(this, SessionActivity.class));
            finish();
        });

        signOut.setOnClickListener(v -> confirmSignOut());

        Press.applyTo(switchSession, signOut);
    }

    /**
     * Signing out deletes a credential that cannot be recovered from inside the
     * app — the user has to go back to a browser for a new one — so it asks
     * first. Scan history is untouched, and the dialog says so, because "will
     * this erase my data" is the actual question being asked at that moment.
     */
    private void confirmSignOut() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.account_sign_out_title)
                .setMessage(R.string.account_sign_out_body)
                .setNegativeButton(R.string.account_sign_out_cancel, null)
                .setPositiveButton(R.string.account_sign_out_confirm, (dialog, which) -> signOut())
                .show();
    }

    private void signOut() {
        new SessionStore(this).clear();
        startActivity(new Intent(this, SessionActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    private String when(ScanEntity scan) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(scan.finishedAt));
    }

    /**
     * The panel scales up out of its top-right corner, which is where the button
     * that opened it sits. A window animation could not do this: it has no
     * pivot, and without the pivot the panel appears from nowhere in particular.
     */
    private void animateIn() {
        if (Motion.reduced(this)) {
            return;
        }
        scrim.setAlpha(0f);
        sheet.setAlpha(0f);
        sheet.setScaleX(ORIGIN_SCALE);
        sheet.setScaleY(ORIGIN_SCALE);

        sheet.post(() -> {
            sheet.setPivotX(sheet.getWidth());
            sheet.setPivotY(0f);
            scrim.animate().alpha(1f).setDuration(180).start();
            sheet.animate().alpha(1f).setDuration(140).start();
            // A little overshoot, because the panel is being thrown out of the
            // corner rather than simply appearing there.
            Motion.springTo(sheet, DynamicAnimation.SCALE_X, 1f,
                    Motion.DAMPING_PLAYFUL, Motion.RESPONSE_QUICK);
            Motion.springTo(sheet, DynamicAnimation.SCALE_Y, 1f,
                    Motion.DAMPING_PLAYFUL, Motion.RESPONSE_QUICK);
        });
    }

    /** Leaves the way it arrived: back into the corner it came out of. */
    private void dismiss() {
        if (dismissing) {
            return;
        }
        dismissing = true;
        if (Motion.reduced(this)) {
            finishSilently();
            return;
        }
        scrim.animate().alpha(0f).setDuration(160).start();
        // Scale goes back through the same springs that brought it in, so a
        // dismiss during the entrance continues from the current size rather
        // than snapping to full first.
        Motion.springTo(sheet, DynamicAnimation.SCALE_X, ORIGIN_SCALE,
                Motion.DAMPING_SMOOTH, Motion.RESPONSE_QUICK);
        Motion.springTo(sheet, DynamicAnimation.SCALE_Y, ORIGIN_SCALE,
                Motion.DAMPING_SMOOTH, Motion.RESPONSE_QUICK);
        sheet.animate()
                .alpha(0f)
                .setDuration(160)
                .withEndAction(this::finishSilently)
                .start();
    }

    private void finishSilently() {
        finish();
        // The panel has already animated itself out; a window animation on top
        // of that would play the exit twice.
        overridePendingTransition(0, 0);
    }

    @Override
    public void onBackPressed() {
        dismiss();
    }
}

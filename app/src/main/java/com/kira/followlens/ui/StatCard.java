package com.kira.followlens.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.kira.followlens.R;

/**
 * One headline number, what it measures, and how it moved since the last scan.
 *
 * The tiles used to be three copies of the same twenty lines of XML with
 * different ids. Making them one view is not tidiness for its own sake: it is
 * the only way the delta pill, the tap target and the value animation get added
 * once instead of three times and drift apart later.
 */
public class StatCard extends LinearLayout {

    private final TextView label;
    private final TextView value;
    private final TextView delta;

    /** Suppresses the change animation for the first value the card ever shows. */
    private boolean everSet;

    public StatCard(Context context) {
        this(context, null);
    }

    public StatCard(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.bg_card);
        int side = dp(14);
        setPadding(side, dp(16), side, dp(14));

        LayoutInflater.from(context).inflate(R.layout.view_stat_card, this, true);
        label = findViewById(R.id.stat_label);
        value = findViewById(R.id.stat_value);
        delta = findViewById(R.id.stat_delta);

        TypedArray styled = context.obtainStyledAttributes(attrs, R.styleable.StatCard);
        label.setText(styled.getString(R.styleable.StatCard_statLabel));
        styled.recycle();
    }

    /**
     * @param current  the number now, or null when there is no scan yet
     * @param previous the same number at the scan before, or null when this is
     *                 the first scan and there is nothing to compare against
     */
    public void setValue(@Nullable Integer current, @Nullable Integer previous) {
        String text = current == null
                ? getContext().getString(R.string.dash_placeholder)
                : String.valueOf(current);
        setText(text);

        if (current == null || previous == null) {
            delta.setVisibility(GONE);
            return;
        }
        int change = current - previous;
        if (change == 0) {
            delta.setVisibility(GONE);
            return;
        }
        delta.setText((change > 0 ? "+" : "−") + Math.abs(change));
        delta.setTextColor(ContextCompat.getColor(getContext(),
                change > 0 ? R.color.positive : R.color.negative));
        delta.setBackgroundResource(R.drawable.bg_pill);
        delta.getBackground().mutate().setColorFilter(
                ContextCompat.getColor(getContext(),
                        change > 0 ? R.color.positive_dim : R.color.negative_dim),
                PorterDuff.Mode.SRC_IN);
        delta.setVisibility(VISIBLE);
    }

    /**
     * Crossfades to a new number rather than swapping it.
     *
     * Deliberately not a count-up. A tally that rolls from 812 to 815 is charming
     * exactly once; this number changes on every scan, and at that frequency the
     * only acceptable animation is one short enough to stop being an event.
     */
    private void setText(String text) {
        if (text.contentEquals(value.getText())) {
            return;
        }
        if (!everSet || Motion.reduced(getContext())) {
            everSet = true;
            value.setText(text);
            return;
        }
        value.animate()
                .alpha(0f)
                .setDuration(90)
                .withEndAction(() -> {
                    value.setText(text);
                    value.animate().alpha(1f).setDuration(140).start();
                })
                .start();
    }

    /** Makes the whole tile a control, with the press response buttons get. */
    public void setOnCardClickListener(OnClickListener listener) {
        setClickable(true);
        setFocusable(true);
        setForeground(ContextCompat.getDrawable(getContext(), R.drawable.fg_card_ripple));
        setOnClickListener(listener);
        Press.applyTo(this);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

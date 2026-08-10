package com.kira.followlens.ui;

import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.kira.followlens.R;

/**
 * A segmented control: every choice visible at once, one of them filled.
 *
 * This replaces the dropdown that used to switch lists. A dropdown hides the
 * options behind a tap and gives no sense of how many there are; a row of
 * segments answers "where am I, and where else can I go" without any
 * interaction, which is the whole job of this control.
 *
 * Segments are built in code rather than declared in XML because the choices
 * come from an enum, and two sources of truth for the same list is how one of
 * them ends up wrong.
 */
public final class Segmented {

    public interface Listener {
        void onSelected(int index);
    }

    private Segmented() {
    }

    /**
     * Fills {@code track} with one segment per label.
     *
     * @param fill spread the segments evenly across the width. Right for three
     *             or four choices; wrong for a list long enough to scroll,
     *             where equal widths would squeeze the labels to nothing.
     */
    public static void install(LinearLayout track, CharSequence[] labels, int initial,
                               boolean fill, Listener listener) {
        Context context = track.getContext();
        track.removeAllViews();

        for (int i = 0; i < labels.length; i++) {
            TextView segment = new TextView(context, null, 0, R.style.Segment);
            segment.setText(labels[i]);

            LinearLayout.LayoutParams params = fill
                    ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            segment.setLayoutParams(params);

            final int index = i;
            segment.setOnClickListener(v -> {
                if (v.isSelected()) {
                    return;
                }
                // A haptic tick on the commit, not on the press: the feedback
                // marks the moment the selection actually changed.
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                select(track, index);
                listener.onSelected(index);
            });
            Press.applyTo(segment);

            track.addView(segment);
        }

        select(track, initial);
    }

    /** Moves the filled state, and brings the choice fully into view if it scrolls. */
    public static void select(LinearLayout track, int index) {
        for (int i = 0; i < track.getChildCount(); i++) {
            View child = track.getChildAt(i);
            boolean selected = i == index;
            child.setSelected(selected);
            // Selection is announced, not only drawn, so it does not depend on
            // seeing which segment is filled.
            child.setContentDescription(((TextView) child).getText()
                    + (selected ? ", " + track.getContext().getString(R.string.selected) : ""));
        }
        revealSelected(track, index);
    }

    private static void revealSelected(LinearLayout track, int index) {
        if (index < 0 || index >= track.getChildCount()) {
            return;
        }
        View parent = (View) track.getParent();
        if (!(parent instanceof HorizontalScrollView)) {
            return;
        }
        View child = track.getChildAt(index);
        // Posted, because immediately after install the child has no measured
        // position yet and the scroll would land on zero.
        parent.post(() -> {
            int target = child.getLeft() - (parent.getWidth() - child.getWidth()) / 2;
            ((HorizontalScrollView) parent).smoothScrollTo(Math.max(target, 0), 0);
        });
    }
}

package com.kira.followlens.ui;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;

import androidx.dynamicanimation.animation.DynamicAnimation;

/**
 * The press response: a control shrinks the instant a finger lands on it and
 * springs back when the finger lifts.
 *
 * Two details matter more than the effect itself.
 *
 * It fires on ACTION_DOWN, not on click. Feedback that waits for the release
 * arrives after the user has already decided the tap did nothing, and that is
 * the moment directness falls apart.
 *
 * It never consumes the event. The listener returns false, so the ripple, the
 * click and any parent scroll all still see the gesture; this only adds a
 * response, it does not take one over.
 */
public final class Press {

    /**
     * Shallow on purpose. 3% is enough to read as give without the control
     * appearing to move away from the finger that is pressing it.
     */
    private static final float PRESSED_SCALE = 0.97f;

    private Press() {
    }

    /** Applies the press response to every view given. */
    public static void applyTo(View... views) {
        for (View view : views) {
            if (view != null) {
                apply(view);
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private static void apply(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    scale(v, PRESSED_SCALE, Motion.RESPONSE_QUICK);
                    break;
                case MotionEvent.ACTION_MOVE:
                    // Dragging off a control cancels it, so the size must come
                    // back at that moment rather than at the eventual lift.
                    if (!within(v, event)) {
                        scale(v, 1f, Motion.RESPONSE_STANDARD);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    scale(v, 1f, Motion.RESPONSE_STANDARD);
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private static boolean within(View view, MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        return x >= 0 && y >= 0 && x <= view.getWidth() && y <= view.getHeight();
    }

    private static void scale(View view, float target, float response) {
        Motion.springTo(view, DynamicAnimation.SCALE_X, target, Motion.DAMPING_SMOOTH, response);
        Motion.springTo(view, DynamicAnimation.SCALE_Y, target, Motion.DAMPING_SMOOTH, response);
    }
}

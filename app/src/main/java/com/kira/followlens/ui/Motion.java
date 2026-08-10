package com.kira.followlens.ui;

import android.content.Context;
import android.provider.Settings;
import android.view.View;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.kira.followlens.R;

/**
 * The app's motion vocabulary, in one place.
 *
 * Everything a finger can touch is animated with a spring rather than a
 * fixed-duration animator. The reason is interruption: a spring always starts
 * from where the view actually is, so a control that is pressed, released and
 * pressed again mid-flight continues from its current scale instead of jumping
 * back to a start value. A duration-based animator cannot do that, and the jump
 * is visible.
 *
 * Springs are described the way Apple describes them — damping ratio and
 * response — rather than as stiffness. Response is the time the value takes to
 * reach the target; it is not a duration, because a spring has no fixed end.
 */
public final class Motion {

    /** Critically damped: settles without overshoot. The default for everything. */
    public static final float DAMPING_SMOOTH = 1f;

    /**
     * Slight overshoot, for motion that a flick or a drag put in flight. Bounce
     * on something the user threw feels physical; bounce on a panel that simply
     * appeared feels like a toy.
     */
    public static final float DAMPING_PLAYFUL = 0.8f;

    public static final float RESPONSE_QUICK = 0.25f;
    public static final float RESPONSE_STANDARD = 0.35f;

    private Motion() {
    }

    /**
     * Stiffness for a given response time, so callers can think in seconds.
     *
     * The spring library takes stiffness against unit mass, where the natural
     * frequency is sqrt(k). Response is one period of that frequency, so
     * k = (2π / response)².
     */
    public static float stiffnessFor(float responseSeconds) {
        double omega = 2 * Math.PI / responseSeconds;
        return (float) (omega * omega);
    }

    /**
     * Honours the system animator scale, which is what "remove animations" in
     * accessibility settings and every battery saver actually set.
     *
     * Reduced motion means gentler, not absent: callers snap the property to its
     * final value, so the feedback still happens, it just does not travel.
     */
    public static boolean reduced(Context context) {
        float scale = Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        return scale == 0f;
    }

    /**
     * Springs a view property to a target, re-targeting any spring already
     * running on it rather than starting a second one.
     */
    public static void springTo(View view, DynamicAnimation.ViewProperty property,
                                float target, float damping, float response) {
        if (reduced(view.getContext())) {
            property.setValue(view, target);
            return;
        }
        SpringAnimation animation = existing(view, property);
        animation.getSpring()
                .setDampingRatio(damping)
                .setStiffness(stiffnessFor(response));
        animation.animateToFinalPosition(target);
    }

    /**
     * One spring per (view, property), cached on the view.
     *
     * A fresh SpringAnimation per gesture would start from the property's
     * current value but drop the current velocity, which is exactly the
     * discontinuity — the "brick wall" — that reversing a gesture must avoid.
     * Reusing the instance lets animateToFinalPosition re-target through it.
     */
    private static SpringAnimation existing(View view, DynamicAnimation.ViewProperty property) {
        int key = keyFor(property);
        Object cached = view.getTag(key);
        if (cached instanceof SpringAnimation) {
            return (SpringAnimation) cached;
        }
        SpringAnimation created = new SpringAnimation(view, property);
        created.setSpring(new SpringForce());
        view.setTag(key, created);
        return created;
    }

    /**
     * View tags are keyed by resource id rather than by an arbitrary int, so
     * they cannot collide with any other tag set on the same view. The ids
     * exist only as keys and are declared in res/values/ids.xml.
     */
    private static int keyFor(DynamicAnimation.ViewProperty property) {
        if (property == DynamicAnimation.SCALE_X) {
            return R.id.spring_scale_x;
        }
        if (property == DynamicAnimation.SCALE_Y) {
            return R.id.spring_scale_y;
        }
        return R.id.spring_other;
    }
}

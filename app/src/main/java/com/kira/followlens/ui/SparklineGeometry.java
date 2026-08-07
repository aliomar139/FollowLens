package com.kira.followlens.ui;

/**
 * Maps a series onto a drawing box. Separated from the View so the arithmetic —
 * the part that is actually easy to get wrong — is testable on the JVM.
 */
public final class SparklineGeometry {

    private SparklineGeometry() {
    }

    /** Evenly spaced x positions, oldest at 0 and newest at {@code width}. */
    public static float[] xs(int count, float width) {
        float[] out = new float[Math.max(count, 0)];
        if (count <= 0) {
            return out;
        }
        if (count == 1) {
            out[0] = 0f;
            return out;
        }
        float step = width / (count - 1);
        for (int i = 0; i < count; i++) {
            out[i] = step * i;
        }
        return out;
    }

    /**
     * y positions scaled to the series range, inverted because canvas y grows
     * downward: the largest value maps to 0 and the smallest to {@code height}.
     *
     * A flat series is centred rather than divided by zero, which is the normal
     * case for an account whose counts have not moved between scans.
     */
    public static float[] ys(int[] values, float height) {
        if (values == null || values.length == 0) {
            return new float[0];
        }
        int min = values[0];
        int max = values[0];
        for (int value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        float[] out = new float[values.length];
        if (max == min) {
            for (int i = 0; i < values.length; i++) {
                out[i] = height / 2f;
            }
            return out;
        }

        float range = max - min;
        for (int i = 0; i < values.length; i++) {
            float fraction = (values[i] - min) / range;
            out[i] = height - fraction * height;
        }
        return out;
    }
}

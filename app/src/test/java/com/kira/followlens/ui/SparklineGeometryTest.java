package com.kira.followlens.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SparklineGeometryTest {

    private static final float EPS = 0.01f;

    @Test
    public void mapsOldestToLeftAndNewestToRight() {
        float[] xs = SparklineGeometry.xs(3, 100f);

        assertEquals(0f, xs[0], EPS);
        assertEquals(50f, xs[1], EPS);
        assertEquals(100f, xs[2], EPS);
    }

    @Test
    public void mapsTheHighestValueToTheTop() {
        // y grows downward on a canvas, so the maximum must land at 0.
        float[] ys = SparklineGeometry.ys(new int[]{10, 30, 20}, 60f);

        assertEquals(60f, ys[0], EPS);
        assertEquals(0f, ys[1], EPS);
        assertEquals(30f, ys[2], EPS);
    }

    @Test
    public void centresAFlatSeriesInsteadOfDividingByZero() {
        // Every scan returning the same count is the common case for a quiet
        // account; a naive range calculation would divide by zero here.
        float[] ys = SparklineGeometry.ys(new int[]{50, 50, 50}, 60f);

        for (float y : ys) {
            assertEquals(30f, y, EPS);
        }
    }

    @Test
    public void aSinglePointSitsAtTheLeftEdgeAndCentre() {
        assertEquals(0f, SparklineGeometry.xs(1, 100f)[0], EPS);
        assertEquals(30f, SparklineGeometry.ys(new int[]{7}, 60f)[0], EPS);
    }

    @Test
    public void staysInsideTheGivenBox() {
        float[] ys = SparklineGeometry.ys(new int[]{5, 900, 12, 60, 3}, 40f);

        for (float y : ys) {
            assertTrue("y outside box: " + y, y >= 0f && y <= 40f);
        }
    }

    @Test
    public void handlesEmptyInput() {
        assertEquals(0, SparklineGeometry.xs(0, 100f).length);
        assertEquals(0, SparklineGeometry.ys(new int[0], 60f).length);
    }
}

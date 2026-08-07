package com.kira.followlens.notify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ScanSummaryTest {

    @Test
    public void reportsBothDirections() {
        assertEquals("3 new followers, 1 unfollowed", ScanSummary.textFor(3, 1));
    }

    @Test
    public void usesSingularForOne() {
        assertEquals("1 new follower", ScanSummary.textFor(1, 0));
    }

    @Test
    public void reportsOnlyUnfollows() {
        assertEquals("2 unfollowed", ScanSummary.textFor(0, 2));
    }

    @Test
    public void returnsNullWhenNothingChanged() {
        assertNull(ScanSummary.textFor(0, 0));
    }
}

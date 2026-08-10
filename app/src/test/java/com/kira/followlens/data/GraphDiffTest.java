package com.kira.followlens.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class GraphDiffTest {

    private static Set<String> ids(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    @Test
    public void reportsAddedAndRemoved() {
        GraphDiff diff = GraphDiff.of(ids("1", "2"), ids("2", "3"));

        assertEquals(ids("3"), diff.added());
        assertEquals(ids("1"), diff.removed());
    }

    @Test
    public void reportsNothingWhenUnchanged() {
        GraphDiff diff = GraphDiff.of(ids("1", "2"), ids("2", "1"));

        assertTrue(diff.added().isEmpty());
        assertTrue(diff.removed().isEmpty());
    }

    @Test
    public void treatsEmptyPreviousAsEverythingAdded() {
        GraphDiff diff = GraphDiff.of(Collections.emptySet(), ids("1", "2"));

        assertEquals(ids("1", "2"), diff.added());
        assertTrue(diff.removed().isEmpty());
    }
}

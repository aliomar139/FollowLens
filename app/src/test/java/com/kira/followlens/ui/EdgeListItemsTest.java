package com.kira.followlens.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.kira.followlens.R;
import com.kira.followlens.data.EdgeRow;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EdgeListItemsTest {

    private static EdgeRow row(String username, boolean isNew) {
        EdgeRow row = new EdgeRow();
        row.username = username;
        row.isNew = isNew;
        return row;
    }

    private static List<Integer> headings(List<EdgeListItems.Item> items) {
        List<Integer> found = new ArrayList<>();
        for (EdgeListItems.Item item : items) {
            if (item.isHeading()) {
                found.add(item.heading());
            }
        }
        return found;
    }

    @Test
    public void splitsNewAccountsFromTheRest() {
        List<EdgeListItems.Item> items = EdgeListItems.build(Arrays.asList(
                row("alice", true), row("bob", false), row("carol", false)));

        assertEquals(Arrays.asList(R.string.section_new, R.string.section_earlier),
                headings(items));
        assertTrue(items.get(0).isHeading());
        assertEquals("alice", items.get(1).row().username);
        assertTrue(items.get(2).isHeading());
        assertEquals("bob", items.get(3).row().username);
    }

    @Test
    public void addsNoHeadingsWhenNothingIsNew() {
        List<EdgeListItems.Item> items = EdgeListItems.build(Arrays.asList(
                row("alice", false), row("bob", false)));

        assertTrue(headings(items).isEmpty());
        assertEquals(2, items.size());
    }

    /**
     * A heading has to be dividing something. Over a list where every row is new
     * it states the obvious and costs a row of screen.
     */
    @Test
    public void addsNoHeadingsWhenEverythingIsNew() {
        List<EdgeListItems.Item> items = EdgeListItems.build(Arrays.asList(
                row("alice", true), row("bob", true)));

        assertTrue(headings(items).isEmpty());
        assertEquals(2, items.size());
    }

    @Test
    public void handlesAnEmptyList() {
        assertTrue(EdgeListItems.build(new ArrayList<>()).isEmpty());
        assertTrue(EdgeListItems.build(null).isEmpty());
    }

    @Test
    public void headingsAndRowsAreNeverBoth() {
        List<EdgeListItems.Item> items = EdgeListItems.build(Arrays.asList(
                row("alice", true), row("bob", false)));

        for (EdgeListItems.Item item : items) {
            assertFalse(item.isHeading() && item.row() != null);
        }
    }
}

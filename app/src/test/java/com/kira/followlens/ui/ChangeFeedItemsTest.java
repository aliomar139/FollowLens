package com.kira.followlens.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.kira.followlens.data.ChangeDirection;
import com.kira.followlens.data.ChangeEventEntity;
import com.kira.followlens.data.ListKind;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class ChangeFeedItemsTest {

    private static ChangeEventEntity change(long scanId, long occurredAt, String username) {
        ChangeEventEntity event = new ChangeEventEntity();
        event.scanId = scanId;
        event.occurredAt = occurredAt;
        event.username = username;
        event.kind = ListKind.FOLLOWER;
        event.direction = ChangeDirection.ADDED;
        return event;
    }

    @Test
    public void insertsOneHeaderPerScan() {
        List<ChangeFeedItems.Item> items = ChangeFeedItems.build(Arrays.asList(
                change(2, 2000L, "carol"),
                change(2, 2000L, "dave"),
                change(1, 1000L, "alice")));

        assertEquals(5, items.size());
        assertTrue(items.get(0).isHeader());
        assertFalse(items.get(1).isHeader());
        assertFalse(items.get(2).isHeader());
        assertTrue(items.get(3).isHeader());
        assertFalse(items.get(4).isHeader());
    }

    @Test
    public void headerCarriesTheScanTimestamp() {
        List<ChangeFeedItems.Item> items =
                ChangeFeedItems.build(Collections.singletonList(change(1, 1234L, "alice")));

        assertEquals(1234L, items.get(0).occurredAt());
    }

    @Test
    public void producesNothingForAnEmptyFeed() {
        assertTrue(ChangeFeedItems.build(Collections.emptyList()).isEmpty());
    }
}

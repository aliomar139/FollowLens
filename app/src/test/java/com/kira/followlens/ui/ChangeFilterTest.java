package com.kira.followlens.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.kira.followlens.data.ChangeDirection;
import com.kira.followlens.data.ChangeEventEntity;
import com.kira.followlens.data.ListKind;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class ChangeFilterTest {

    private static ChangeEventEntity event(ChangeDirection direction, String username) {
        ChangeEventEntity e = new ChangeEventEntity();
        e.direction = direction;
        e.kind = ListKind.FOLLOWER;
        e.username = username;
        return e;
    }

    private static final List<ChangeEventEntity> EVENTS = Arrays.asList(
            event(ChangeDirection.ADDED, "alice"),
            event(ChangeDirection.REMOVED, "bob"),
            event(ChangeDirection.ADDED, "carol"));

    @Test
    public void allKeepsEverything() {
        assertEquals(3, ChangeFilter.matching(EVENTS, ChangeFilter.Mode.ALL).size());
    }

    @Test
    public void newKeepsOnlyAdditions() {
        List<ChangeEventEntity> result =
                ChangeFilter.matching(EVENTS, ChangeFilter.Mode.ADDED_ONLY);

        assertEquals(2, result.size());
        for (ChangeEventEntity e : result) {
            assertEquals(ChangeDirection.ADDED, e.direction);
        }
    }

    @Test
    public void lostKeepsOnlyRemovals() {
        List<ChangeEventEntity> result =
                ChangeFilter.matching(EVENTS, ChangeFilter.Mode.REMOVED_ONLY);

        assertEquals(1, result.size());
        assertEquals("bob", result.get(0).username);
    }

    @Test
    public void handlesNullInput() {
        assertTrue(ChangeFilter.matching(null, ChangeFilter.Mode.ALL).isEmpty());
    }
}

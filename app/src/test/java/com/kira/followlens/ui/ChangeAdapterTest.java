package com.kira.followlens.ui;

import static org.junit.Assert.assertEquals;

import com.kira.followlens.data.ChangeDirection;
import com.kira.followlens.data.ChangeEventEntity;
import com.kira.followlens.data.ListKind;

import org.junit.Test;

public class ChangeAdapterTest {

    private static ChangeEventEntity change(ChangeDirection direction, ListKind kind,
                                            String username) {
        ChangeEventEntity event = new ChangeEventEntity();
        event.direction = direction;
        event.kind = kind;
        event.username = username;
        return event;
    }

    // The row draws the username on its own line, so these describe the action
    // alone. A label that repeated the name would print it twice.

    @Test
    public void labelsANewFollower() {
        assertEquals("Started following you",
                ChangeAdapter.actionFor(change(ChangeDirection.ADDED, ListKind.FOLLOWER, "alice")));
    }

    @Test
    public void labelsAnUnfollow() {
        assertEquals("Unfollowed you",
                ChangeAdapter.actionFor(change(ChangeDirection.REMOVED, ListKind.FOLLOWER, "bob")));
    }

    @Test
    public void labelsAccountsYouStartedFollowing() {
        assertEquals("You started following",
                ChangeAdapter.actionFor(change(ChangeDirection.ADDED, ListKind.FOLLOWING, "carol")));
    }

    @Test
    public void labelsAccountsYouStoppedFollowing() {
        assertEquals("You stopped following",
                ChangeAdapter.actionFor(change(ChangeDirection.REMOVED, ListKind.FOLLOWING, "dave")));
    }

    @Test
    public void signIsAGlyphSoMeaningDoesNotRestOnColourAlone() {
        assertEquals("+",
                ChangeAdapter.signFor(change(ChangeDirection.ADDED, ListKind.FOLLOWER, "alice")));
        assertEquals("−",
                ChangeAdapter.signFor(change(ChangeDirection.REMOVED, ListKind.FOLLOWER, "bob")));
    }
}

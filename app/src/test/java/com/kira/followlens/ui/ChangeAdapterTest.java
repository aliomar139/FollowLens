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

    @Test
    public void labelsANewFollower() {
        assertEquals("+ alice started following you",
                ChangeAdapter.labelFor(change(ChangeDirection.ADDED, ListKind.FOLLOWER, "alice")));
    }

    @Test
    public void labelsAnUnfollow() {
        assertEquals("- bob unfollowed you",
                ChangeAdapter.labelFor(change(ChangeDirection.REMOVED, ListKind.FOLLOWER, "bob")));
    }

    @Test
    public void labelsAccountsYouStartedFollowing() {
        assertEquals("+ you started following carol",
                ChangeAdapter.labelFor(change(ChangeDirection.ADDED, ListKind.FOLLOWING, "carol")));
    }

    @Test
    public void labelsAccountsYouStoppedFollowing() {
        assertEquals("- you stopped following dave",
                ChangeAdapter.labelFor(change(ChangeDirection.REMOVED, ListKind.FOLLOWING, "dave")));
    }
}

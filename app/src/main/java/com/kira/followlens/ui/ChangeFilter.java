package com.kira.followlens.ui;

import com.kira.followlens.data.ChangeDirection;
import com.kira.followlens.data.ChangeEventEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Narrows the change feed to gains or losses. */
public final class ChangeFilter {

    public enum Mode {
        ALL,
        ADDED_ONLY,
        REMOVED_ONLY
    }

    private ChangeFilter() {
    }

    public static List<ChangeEventEntity> matching(List<ChangeEventEntity> events, Mode mode) {
        if (events == null) {
            return Collections.emptyList();
        }
        if (mode == Mode.ALL) {
            return events;
        }
        ChangeDirection wanted = mode == Mode.ADDED_ONLY
                ? ChangeDirection.ADDED : ChangeDirection.REMOVED;

        List<ChangeEventEntity> out = new ArrayList<>();
        for (ChangeEventEntity event : events) {
            if (event.direction == wanted) {
                out.add(event);
            }
        }
        return out;
    }
}

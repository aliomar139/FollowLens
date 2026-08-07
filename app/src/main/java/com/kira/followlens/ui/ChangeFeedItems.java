package com.kira.followlens.ui;

import com.kira.followlens.data.ChangeEventEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Flattens the change feed into headers and rows. The feed arrives newest first
 * and already grouped by scan, so this only has to insert a header whenever the
 * scan id changes.
 */
public final class ChangeFeedItems {

    /** Either a scan header or a single change. */
    public static final class Item {

        private final ChangeEventEntity change;
        private final long occurredAt;

        private Item(ChangeEventEntity change, long occurredAt) {
            this.change = change;
            this.occurredAt = occurredAt;
        }

        public boolean isHeader() {
            return change == null;
        }

        public ChangeEventEntity change() {
            return change;
        }

        public long occurredAt() {
            return occurredAt;
        }
    }

    private ChangeFeedItems() {
    }

    public static List<Item> build(List<ChangeEventEntity> events) {
        List<Item> items = new ArrayList<>();
        Long currentScanId = null;
        for (ChangeEventEntity event : events) {
            if (currentScanId == null || currentScanId != event.scanId) {
                items.add(new Item(null, event.occurredAt));
                currentScanId = event.scanId;
            }
            items.add(new Item(event, event.occurredAt));
        }
        return items;
    }
}

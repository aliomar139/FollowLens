package com.kira.followlens.ui;

import androidx.annotation.StringRes;

import com.kira.followlens.R;
import com.kira.followlens.data.EdgeRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a list of accounts into "new since the last scan" and the rest.
 *
 * The queries already sort new accounts first. Grouping them under a heading is
 * what turns that ordering into something readable: without it, a list that
 * happens to start with four badged rows looks like an alphabetical accident.
 *
 * Kept apart from the adapter so the grouping rules can be tested without
 * inflating a view.
 */
public final class EdgeListItems {

    /** A heading or an account, never both. */
    public static final class Item {

        private final Integer headingRes;
        private final EdgeRow row;

        private Item(Integer headingRes, EdgeRow row) {
            this.headingRes = headingRes;
            this.row = row;
        }

        public boolean isHeading() {
            return headingRes != null;
        }

        @StringRes
        public int heading() {
            return headingRes;
        }

        public EdgeRow row() {
            return row;
        }
    }

    private EdgeListItems() {
    }

    /**
     * Headings appear only when the list actually has both groups. A single
     * "NEW" heading above a list where everything is new says nothing, and the
     * same is true of "EARLIER" above a list with no new accounts at all — the
     * heading has to be dividing something to be worth the row it occupies.
     */
    public static List<Item> build(List<EdgeRow> rows) {
        List<Item> items = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return items;
        }

        int newCount = 0;
        for (EdgeRow row : rows) {
            if (row.isNew) {
                newCount++;
            }
        }
        boolean split = newCount > 0 && newCount < rows.size();

        boolean earlierHeadingWritten = false;
        for (int i = 0; i < rows.size(); i++) {
            EdgeRow row = rows.get(i);
            if (split && i == 0) {
                items.add(new Item(R.string.section_new, null));
            }
            if (split && !row.isNew && !earlierHeadingWritten) {
                items.add(new Item(R.string.section_earlier, null));
                earlierHeadingWritten = true;
            }
            items.add(new Item(null, row));
        }
        return items;
    }
}

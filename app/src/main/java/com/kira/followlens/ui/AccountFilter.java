package com.kira.followlens.ui;

import com.kira.followlens.data.EdgeRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Substring search over a loaded list.
 *
 * Filtering in memory rather than re-querying keeps typing instant: the rows are
 * already here, and a query per keystroke would thrash the database for no gain
 * at these list sizes.
 */
public final class AccountFilter {

    private AccountFilter() {
    }

    public static List<EdgeRow> matching(List<EdgeRow> rows, String query) {
        if (rows == null) {
            return Collections.emptyList();
        }
        if (query == null || query.trim().isEmpty()) {
            return rows;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);

        List<EdgeRow> out = new ArrayList<>();
        for (EdgeRow row : rows) {
            if (row.username == null) {
                continue;
            }
            if (row.username.toLowerCase(Locale.ROOT).contains(needle)) {
                out.add(row);
            }
        }
        return out;
    }
}

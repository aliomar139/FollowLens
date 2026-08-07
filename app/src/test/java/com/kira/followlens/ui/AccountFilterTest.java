package com.kira.followlens.ui;

import static org.junit.Assert.assertEquals;

import com.kira.followlens.data.EdgeRow;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class AccountFilterTest {

    private static EdgeRow row(String username) {
        EdgeRow r = new EdgeRow();
        r.username = username;
        return r;
    }

    private static final List<EdgeRow> ROWS = Arrays.asList(
            row("alice"), row("Bob_Smith"), row("carol.jones"), row("DAVE"));

    private static String names(List<EdgeRow> rows) {
        StringBuilder out = new StringBuilder();
        for (EdgeRow r : rows) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(r.username);
        }
        return out.toString();
    }

    @Test
    public void anEmptyQueryKeepsEverything() {
        assertEquals(4, AccountFilter.matching(ROWS, "").size());
        assertEquals(4, AccountFilter.matching(ROWS, null).size());
        assertEquals(4, AccountFilter.matching(ROWS, "   ").size());
    }

    @Test
    public void matchesAnywhereInTheName() {
        assertEquals("Bob_Smith", names(AccountFilter.matching(ROWS, "smith")));
    }

    @Test
    public void isCaseInsensitiveInBothDirections() {
        assertEquals("DAVE", names(AccountFilter.matching(ROWS, "dave")));
        assertEquals("alice", names(AccountFilter.matching(ROWS, "ALI")));
    }

    @Test
    public void trimsTheQuery() {
        assertEquals("carol.jones", names(AccountFilter.matching(ROWS, "  carol ")));
    }

    @Test
    public void returnsEmptyWhenNothingMatches() {
        assertEquals(0, AccountFilter.matching(ROWS, "zzz").size());
    }

    @Test
    public void toleratesNullUsernames() {
        List<EdgeRow> withNull = Arrays.asList(row(null), row("alice"));

        assertEquals("alice", names(AccountFilter.matching(withNull, "ali")));
    }

    @Test
    public void handlesANullList() {
        assertEquals(0, AccountFilter.matching(null, "a").size());
    }
}

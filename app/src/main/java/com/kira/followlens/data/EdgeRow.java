package com.kira.followlens.data;

/**
 * One account as the list shows it: the stored edge plus whether the follow is
 * reciprocated.
 *
 * The mutual flag is computed in SQL rather than by a second query per row, so a
 * 5000-account list still costs one statement.
 */
public class EdgeRow {

    public String accountId;

    public ListKind kind;

    public String userId;

    public String username;

    /** True when this account appears in both the followers and following lists. */
    public boolean mutual;

    /**
     * True when the account entered this particular list in the most recent
     * scan.
     *
     * "New to the list" is not the same as "new to the graph". Someone who
     * unfollowed you did not appear anywhere new — but they did just land in
     * "not following back", and that is the arrival worth pointing at. The
     * queries compute this per list for that reason.
     *
     * The very first scan is excluded: on a baseline every single account is
     * technically new, and a list where everything is flagged flags nothing.
     */
    public boolean isNew;
}

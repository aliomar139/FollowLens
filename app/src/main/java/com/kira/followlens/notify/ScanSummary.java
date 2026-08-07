package com.kira.followlens.notify;

/** Builds the one-line notification text for a scan. */
public final class ScanSummary {

    private ScanSummary() {
    }

    /** Returns null when there is nothing worth interrupting the user for. */
    public static String textFor(int added, int removed) {
        if (added == 0 && removed == 0) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        if (added > 0) {
            text.append(added).append(added == 1 ? " new follower" : " new followers");
        }
        if (removed > 0) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(removed).append(" unfollowed");
        }
        return text.toString();
    }
}

package com.kira.followlens.scan;

/** The result of one scan attempt. */
public final class ScanOutcome {

    private final boolean ok;
    private final String error;
    private final boolean retryable;
    private final long scanId;
    private final boolean baseline;
    private final int addedFollowers;
    private final int removedFollowers;

    private ScanOutcome(boolean ok, String error, boolean retryable, long scanId,
                        boolean baseline, int addedFollowers, int removedFollowers) {
        this.ok = ok;
        this.error = error;
        this.retryable = retryable;
        this.scanId = scanId;
        this.baseline = baseline;
        this.addedFollowers = addedFollowers;
        this.removedFollowers = removedFollowers;
    }

    public static ScanOutcome committed(long scanId, boolean baseline, int addedFollowers,
                                        int removedFollowers) {
        return new ScanOutcome(true, null, false, scanId, baseline, addedFollowers,
                removedFollowers);
    }

    /** Nothing was attempted: cooldown, or no session. Retrying now will not help. */
    public static ScanOutcome skipped(String reason) {
        return new ScanOutcome(false, reason, false, -1, false, 0, 0);
    }

    /** The attempt failed partway. Worth retrying later. */
    public static ScanOutcome failed(String reason) {
        return new ScanOutcome(false, reason, true, -1, false, 0, 0);
    }

    public boolean ok() {
        return ok;
    }

    public String error() {
        return error;
    }

    public boolean retryable() {
        return retryable;
    }

    public long scanId() {
        return scanId;
    }

    public boolean baseline() {
        return baseline;
    }

    public int addedFollowers() {
        return addedFollowers;
    }

    public int removedFollowers() {
        return removedFollowers;
    }

    public boolean hasFollowerChanges() {
        return addedFollowers > 0 || removedFollowers > 0;
    }
}

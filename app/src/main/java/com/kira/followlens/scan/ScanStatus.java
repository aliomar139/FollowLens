package com.kira.followlens.scan;

/**
 * Decides which single message the dashboard shows.
 *
 * Kept as a pure function because the ordering is the whole point and it is easy
 * to get subtly wrong: a stale error must not hide an in-progress scan, and it
 * must not replace "no scan yet" when nothing has ever succeeded.
 */
public final class ScanStatus {

    private ScanStatus() {
    }

    /**
     * @param running     a scan worker is executing right now
     * @param queued      a manual scan is enqueued but not started
     * @param hasScan     at least one scan has committed
     * @param baselineOnly the only committed scan is the baseline
     * @param lastError   message from the most recent failed attempt, or null
     */
    public static ScanState of(boolean running, boolean queued, boolean hasScan,
                               boolean baselineOnly, String lastError) {
        if (running) {
            return ScanState.SCANNING;
        }
        if (queued) {
            return ScanState.QUEUED;
        }
        if (!hasScan) {
            // Nothing has ever succeeded; that is more useful than the error text.
            return ScanState.NO_SCAN;
        }
        if (lastError != null && !lastError.trim().isEmpty()) {
            return ScanState.FAILED;
        }
        return baselineOnly ? ScanState.BASELINE : ScanState.OK;
    }
}

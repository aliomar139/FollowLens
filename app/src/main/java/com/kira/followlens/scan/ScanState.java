package com.kira.followlens.scan;

/** What the dashboard should be telling the user about scanning right now. */
public enum ScanState {

    /** No snapshot exists yet. */
    NO_SCAN,

    /** A scan is running. */
    SCANNING,

    /** A manual scan is waiting for a slot or for the network. */
    QUEUED,

    /** The only scan so far was the baseline, so there is nothing to compare to. */
    BASELINE,

    /** At least two scans have run and the last one succeeded. */
    OK,

    /** The last attempt failed and the reason is worth showing. */
    FAILED
}

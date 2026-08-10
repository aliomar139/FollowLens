package com.kira.followlens.scan;

/**
 * Where a running scan has got to.
 *
 * A scan takes up to a minute and spends nearly all of it waiting between
 * paginated requests, deliberately, to avoid being throttled. Without a running
 * count that pause is indistinguishable from a hang, and the usual response to
 * an app that looks hung is to kill it — which is exactly the thing that loses
 * the scan.
 */
public interface ScanProgress {

    enum Stage {
        /** Walking the following list. */
        FOLLOWING,
        /** Walking the followers list, which takes several passes. */
        FOLLOWERS,
        /** Both lists are in; writing them as one transaction. */
        SAVING,
    }

    /** Reported once per fetched page, and once when the commit starts. */
    void onProgress(Stage stage, int collected);

    /** For callers that do not care, so no call site needs a null check. */
    ScanProgress NONE = (stage, collected) -> {
    };
}

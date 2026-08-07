package com.kira.followlens.scan;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ScanStatusTest {

    @Test
    public void runningBeatsEverythingElse() {
        assertEquals(ScanState.SCANNING,
                ScanStatus.of(true, true, true, false, "boom"));
    }

    @Test
    public void queuedIsShownWhenNothingIsRunningYet() {
        assertEquals(ScanState.QUEUED,
                ScanStatus.of(false, true, true, false, null));
    }

    @Test
    public void aStoredErrorIsSurfacedOverAnOldSuccess() {
        // The counts on screen are from the last good scan, but the user still
        // needs to know the most recent attempt failed.
        assertEquals(ScanState.FAILED,
                ScanStatus.of(false, false, true, false, "Cooldown active"));
    }

    @Test
    public void aFailedFirstScanReportsTheErrorNotNoScanYet() {
        // Verified on device: saying "No scan yet, tap Refresh" right after
        // Refresh failed hides the only information that matters.
        assertEquals(ScanState.FAILED,
                ScanStatus.of(false, false, false, false, "throttled on this endpoint"));
    }

    @Test
    public void reportsNoScanWhenThereIsNothingAtAll() {
        assertEquals(ScanState.NO_SCAN,
                ScanStatus.of(false, false, false, false, null));
    }

    @Test
    public void reportsBaselineAfterOnlyTheFirstScan() {
        assertEquals(ScanState.BASELINE,
                ScanStatus.of(false, false, true, true, null));
    }

    @Test
    public void reportsOkOnceASecondScanHasRun() {
        assertEquals(ScanState.OK,
                ScanStatus.of(false, false, true, false, null));
    }

    @Test
    public void treatsABlankErrorAsNoError() {
        assertEquals(ScanState.OK,
                ScanStatus.of(false, false, true, false, "   "));
    }
}

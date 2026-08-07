package com.kira.followlens.scan;

import android.content.Context;
import android.content.SharedPreferences;

/** When the last successful scan finished. Not secret, so plain preferences. */
public interface ScanPrefs {

    long lastScanAt();

    void setLastScanAt(long millis);

    static ScanPrefs of(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences("followlens_scan", Context.MODE_PRIVATE);
        return new ScanPrefs() {
            @Override
            public long lastScanAt() {
                return prefs.getLong("last_scan_at", 0L);
            }

            @Override
            public void setLastScanAt(long millis) {
                prefs.edit().putLong("last_scan_at", millis).apply();
            }
        };
    }
}

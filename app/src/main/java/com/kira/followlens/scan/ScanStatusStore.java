package com.kira.followlens.scan;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers why the last scan attempt failed.
 *
 * Without this the outcome message was computed and thrown away, so a scan that
 * failed every time looked identical to a button that did nothing.
 */
public class ScanStatusStore {

    private static final String FILE = "followlens_status";
    private static final String KEY_LAST_ERROR = "last_error";

    private final SharedPreferences prefs;

    public ScanStatusStore(Context context) {
        this.prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public String lastError() {
        return prefs.getString(KEY_LAST_ERROR, null);
    }

    /** Pass null after a successful scan to clear the message. */
    public void setLastError(String message) {
        if (message == null) {
            prefs.edit().remove(KEY_LAST_ERROR).apply();
            return;
        }
        prefs.edit().putString(KEY_LAST_ERROR, message).apply();
    }
}

package com.kira.followlens.auth;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.kira.followlens.net.SessionId;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Holds the session cookie. This value is a full account session, so it lives in
 * EncryptedSharedPreferences and is never logged.
 */
public class SessionStore {

    private static final String FILE = "followlens_session";
    private static final String KEY_SESSION_ID = "sessionid";
    private static final String KEY_CSRF_TOKEN = "csrftoken";

    private final SharedPreferences prefs;

    public SessionStore(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            this.prefs = EncryptedSharedPreferences.create(
                    context,
                    FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("cannot open encrypted preferences", e);
        }
    }

    public void save(String sessionId) {
        save(sessionId, null);
    }

    /**
     * @param csrfToken the csrftoken that came with the paste, or null. Kept
     *                  because a browser sends it on every request to these
     *                  endpoints, and discarding it made our request differ from
     *                  the one the API expects.
     */
    public void save(String sessionId, String csrfToken) {
        // Throws if the cookie is malformed, so a useless session is never stored.
        SessionId.userIdOf(sessionId);
        SharedPreferences.Editor editor = prefs.edit().putString(KEY_SESSION_ID, sessionId);
        if (csrfToken == null || csrfToken.trim().isEmpty()) {
            editor.remove(KEY_CSRF_TOKEN);
        } else {
            editor.putString(KEY_CSRF_TOKEN, csrfToken.trim());
        }
        editor.apply();
    }

    public String sessionId() {
        return prefs.getString(KEY_SESSION_ID, null);
    }

    public String csrfToken() {
        return prefs.getString(KEY_CSRF_TOKEN, null);
    }

    public String userId() {
        String sessionId = sessionId();
        return sessionId == null ? null : SessionId.userIdOf(sessionId);
    }

    public boolean hasSession() {
        return sessionId() != null;
    }

    public void clear() {
        prefs.edit().remove(KEY_SESSION_ID).remove(KEY_CSRF_TOKEN).apply();
    }
}

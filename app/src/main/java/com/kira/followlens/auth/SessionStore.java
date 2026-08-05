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
        // Throws if the cookie is malformed, so a useless session is never stored.
        SessionId.userIdOf(sessionId);
        prefs.edit().putString(KEY_SESSION_ID, sessionId).apply();
    }

    public String sessionId() {
        return prefs.getString(KEY_SESSION_ID, null);
    }

    public String userId() {
        String sessionId = sessionId();
        return sessionId == null ? null : SessionId.userIdOf(sessionId);
    }

    public boolean hasSession() {
        return sessionId() != null;
    }

    public void clear() {
        prefs.edit().remove(KEY_SESSION_ID).apply();
    }
}

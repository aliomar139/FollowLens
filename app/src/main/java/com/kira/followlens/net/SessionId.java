package com.kira.followlens.net;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/** Derives the Instagram user id that is embedded in a sessionid cookie. */
public final class SessionId {

    private SessionId() {
    }

    /**
     * A sessionid looks like "12345:abcdef:99" (sometimes percent-encoded).
     * The leading segment is the account's own user id, which is why this app
     * never needs to ask for a username.
     */
    public static String userIdOf(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionid is empty");
        }
        String decoded;
        try {
            decoded = URLDecoder.decode(sessionId, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8 always available", e);
        }
        String userId = decoded.split(":")[0];
        if (userId.isEmpty()) {
            throw new IllegalArgumentException("sessionid has no leading user id");
        }
        return userId;
    }
}

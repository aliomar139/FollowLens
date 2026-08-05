package com.kira.followlens.auth;

/**
 * CookieManager returns every cookie for a domain as one header string, so the
 * session cookie has to be picked out of it.
 */
public final class CookieParser {

    private CookieParser() {
    }

    /** Returns the raw sessionid value, or null when it is absent or empty. */
    public static String sessionIdFrom(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return null;
        }
        for (String part : cookieHeader.split(";")) {
            String cookie = part.trim();
            if (!cookie.startsWith("sessionid=")) {
                continue;
            }
            String value = cookie.substring("sessionid=".length()).trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }
}

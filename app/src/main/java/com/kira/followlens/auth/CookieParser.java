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
        return valueFrom(cookieHeader, "sessionid");
    }

    /**
     * Returns the csrftoken value, or null.
     *
     * A browser sends this alongside sessionid on every request to the private
     * web endpoints, so a client that drops it is not making the same request
     * the API expects.
     */
    public static String csrfTokenFrom(String cookieHeader) {
        return valueFrom(cookieHeader, "csrftoken");
    }

    /** Reads one named cookie out of a {@code name=value; name=value} header. */
    public static String valueFrom(String cookieHeader, String name) {
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return null;
        }
        String prefix = name + "=";
        for (String part : cookieHeader.split(";")) {
            String cookie = part.trim();
            if (!cookie.startsWith(prefix)) {
                continue;
            }
            String value = cookie.substring(prefix.length()).trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }
}

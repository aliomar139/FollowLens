package com.kira.followlens.auth;

/**
 * Cleans up a pasted session key.
 *
 * People paste whatever the clipboard happened to hold: a bare cookie value, a
 * {@code sessionid=...} pair, or the entire cookie header copied out of DevTools.
 * All three are accepted, and anything that cannot be a session is rejected here
 * rather than being stored and failing later with a 401 on every scan.
 */
public final class SessionInput {

    private SessionInput() {
    }

    /** Returns a usable sessionid value, or null when the input cannot be one. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }

        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }

        // A whole cookie header: hand it to the parser that already knows the format.
        if (value.contains("sessionid=")) {
            value = CookieParser.sessionIdFrom(value);
            if (value == null) {
                return null;
            }
        }

        value = stripQuotes(value.trim());
        if (value.isEmpty()) {
            return null;
        }

        return isPlausibleSession(value) ? value : null;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2
                && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    /**
     * A session looks like {@code <numeric user id>:<secret>...}, where the colon
     * may arrive percent-encoded as {@code %3A} from a URL-encoded cookie.
     */
    private static boolean isPlausibleSession(String value) {
        String separator = value.contains(":") ? ":" : (value.contains("%3A") ? "%3A" : null);
        if (separator == null) {
            return false;
        }
        String userId = value.substring(0, value.indexOf(separator));
        if (userId.isEmpty()) {
            return false;
        }
        for (int i = 0; i < userId.length(); i++) {
            if (!Character.isDigit(userId.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}

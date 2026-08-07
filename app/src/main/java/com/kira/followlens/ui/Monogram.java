package com.kira.followlens.ui;

import java.util.Locale;

/**
 * Avatar stand-ins drawn from the username itself.
 *
 * Real profile pictures would cost one request per row — a 220-account list means
 * 220 extra calls against the same endpoint family that already rate-limits this
 * app. A letter and a stable tint give rows a visual anchor for free.
 */
public final class Monogram {

    /** How many tints the palette offers. */
    public static final int TINT_COUNT = 6;

    private Monogram() {
    }

    /** The letter to draw, or a placeholder when the name has none. */
    public static String initialOf(String username) {
        if (username == null || username.isEmpty()) {
            return "?";
        }
        for (int i = 0; i < username.length(); i++) {
            char c = username.charAt(i);
            if (Character.isLetter(c)) {
                return String.valueOf(c).toUpperCase(Locale.ROOT);
            }
        }
        // Names made only of digits or symbols still need something to show.
        return "#";
    }

    /**
     * A tint index derived from the account id, so a given account keeps the same
     * colour across scrolls, list switches and app restarts. Keyed on the id
     * rather than the name because a rename should not recolour the row.
     */
    public static int tintIndexOf(String userId) {
        if (userId == null || userId.isEmpty()) {
            return 0;
        }
        int hash = 0;
        for (int i = 0; i < userId.length(); i++) {
            hash = hash * 31 + userId.charAt(i);
        }
        return Math.abs(hash % TINT_COUNT);
    }
}

package com.kira.followlens.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SessionInputTest {

    @Test
    public void acceptsABareSessionId() {
        assertEquals("12345:abcdef:99", SessionInput.normalize("12345:abcdef:99"));
    }

    @Test
    public void trimsSurroundingWhitespaceAndNewlines() {
        assertEquals("12345:abcdef:99", SessionInput.normalize("  12345:abcdef:99\n"));
    }

    @Test
    public void stripsASessionidPrefixPastedFromDevTools() {
        assertEquals("12345:abcdef:99", SessionInput.normalize("sessionid=12345:abcdef:99"));
    }

    @Test
    public void extractsFromAWholeCookieHeader() {
        assertEquals("12345%3Aabcdef%3A99", SessionInput.normalize(
                "csrftoken=zzz; sessionid=12345%3Aabcdef%3A99; ds_user_id=12345"));
    }

    @Test
    public void stripsSurroundingQuotes() {
        assertEquals("12345:abcdef:99", SessionInput.normalize("\"12345:abcdef:99\""));
    }

    @Test
    public void acceptsPercentEncodedSeparators() {
        assertEquals("12345%3Aabcdef", SessionInput.normalize("12345%3Aabcdef"));
    }

    @Test
    public void rejectsEmptyOrBlankInput() {
        assertNull(SessionInput.normalize(null));
        assertNull(SessionInput.normalize(""));
        assertNull(SessionInput.normalize("   "));
    }

    @Test
    public void rejectsInputWithNoSeparator() {
        assertNull(SessionInput.normalize("justsomerandomtext"));
    }

    @Test
    public void rejectsANonNumericLeadingSegment() {
        // The leading segment is the Instagram user id, so it must be digits.
        // Without this check a typo would be stored and every scan would 401.
        assertNull(SessionInput.normalize("abcdef:12345:99"));
    }

    @Test
    public void rejectsAMissingUserId() {
        assertNull(SessionInput.normalize(":abcdef:99"));
    }
}

package com.kira.followlens.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CookieParserTest {

    @Test
    public void extractsSessionIdFromAMultiCookieHeader() {
        String header = "csrftoken=abc; sessionid=12345%3Asecret%3A99; ds_user_id=12345";

        assertEquals("12345%3Asecret%3A99", CookieParser.sessionIdFrom(header));
    }

    @Test
    public void handlesSessionIdAsTheOnlyCookie() {
        assertEquals("12345:secret:99", CookieParser.sessionIdFrom("sessionid=12345:secret:99"));
    }

    @Test
    public void doesNotMatchASuffixedCookieName() {
        assertNull(CookieParser.sessionIdFrom("x_sessionid=nope; other=1"));
    }

    @Test
    public void returnsNullWhenAbsent() {
        assertNull(CookieParser.sessionIdFrom("csrftoken=abc; mid=xyz"));
    }

    @Test
    public void returnsNullForNullOrEmptyHeader() {
        assertNull(CookieParser.sessionIdFrom(null));
        assertNull(CookieParser.sessionIdFrom(""));
    }

    @Test
    public void returnsNullForAnEmptySessionIdValue() {
        assertNull(CookieParser.sessionIdFrom("sessionid=; csrftoken=abc"));
    }

    @Test
    public void extractsTheCsrfTokenToo() {
        String header = "csrftoken=Xy9Tok; sessionid=12345:secret:99; ds_user_id=12345";

        assertEquals("Xy9Tok", CookieParser.csrfTokenFrom(header));
    }

    @Test
    public void csrfTokenIsNullWhenOnlyASessionIdWasPasted() {
        assertNull(CookieParser.csrfTokenFrom("sessionid=12345:secret:99"));
    }
}

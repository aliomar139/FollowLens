package com.kira.followlens.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class IgWebClientTest {

    private MockWebServer server;
    private IgWebClient client;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        // A csrftoken is supplied so tests exercise the steady state; the
        // bootstrap path has its own tests below.
        client = new IgWebClient(server.url("/"), "12345:secret:99", "Tok123",
                Sleeper.NONE, 0, 0);
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private static MockResponse page(String nextMaxId, String... idUsernamePairs) {
        StringBuilder users = new StringBuilder();
        for (int i = 0; i < idUsernamePairs.length; i += 2) {
            if (users.length() > 0) {
                users.append(',');
            }
            users.append("{\"pk\":\"").append(idUsernamePairs[i])
                    .append("\",\"username\":\"").append(idUsernamePairs[i + 1]).append("\"}");
        }
        String next = nextMaxId == null ? "null" : "\"" + nextMaxId + "\"";
        return new MockResponse()
                .setResponseCode(200)
                .setBody("{\"users\":[" + users + "],\"next_max_id\":" + next + "}");
    }

    @Test
    public void followingFollowsPaginationUntilNextMaxIdIsNull() throws Exception {
        server.enqueue(page("cursor1", "1", "alice"));
        server.enqueue(page(null, "2", "bob"));

        Map<String, String> result = client.following("999");

        assertEquals(2, result.size());
        assertEquals("alice", result.get("1"));
        assertEquals("bob", result.get("2"));

        RecordedRequest first = server.takeRequest();
        assertTrue(first.getPath().contains("/api/v1/friendships/999/following/"));
        assertTrue(server.takeRequest().getPath().contains("max_id=cursor1"));
    }

    @Test
    public void sendsInstagramHeadersAndSessionCookie() throws Exception {
        server.enqueue(page(null, "1", "alice"));

        client.following("999");

        RecordedRequest request = server.takeRequest();
        assertEquals("936619743392459", request.getHeader("x-ig-app-id"));
        assertEquals("XMLHttpRequest", request.getHeader("x-requested-with"));
        assertTrue(request.getHeader("user-agent").contains("Mozilla/5.0"));
        assertTrue(request.getHeader("cookie").contains("sessionid=12345:secret:99"));
        assertTrue(request.getHeader("cookie").contains("ds_user_id=12345"));
        assertEquals("same-origin", request.getHeader("sec-fetch-site"));
    }

    @Test
    public void sendsTheCsrfTokenAsBothCookieAndHeader() throws Exception {
        server.enqueue(page(null, "1", "alice"));

        client.following("999");

        RecordedRequest request = server.takeRequest();
        assertEquals("Tok123", request.getHeader("x-csrftoken"));
        assertTrue(request.getHeader("cookie").contains("csrftoken=Tok123"));
    }

    @Test
    public void bootstrapsACsrfTokenWhenTheSessionDidNotCarryOne() throws Exception {
        // Root request that hands back a csrftoken, then the real page.
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("set-cookie", "csrftoken=FromRoot; Path=/; Secure")
                .setBody("<html></html>"));
        server.enqueue(page(null, "1", "alice"));

        IgWebClient bare = new IgWebClient(server.url("/"), "12345:secret:99", null,
                Sleeper.NONE, 0, 0);
        bare.following("999");

        server.takeRequest();                                  // the bootstrap call
        RecordedRequest apiCall = server.takeRequest();
        assertEquals("FromRoot", apiCall.getHeader("x-csrftoken"));
    }

    @Test
    public void aFailedBootstrapDoesNotFailTheScan() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));   // bootstrap fails
        server.enqueue(page(null, "1", "alice"));

        IgWebClient bare = new IgWebClient(server.url("/"), "12345:secret:99", null,
                Sleeper.NONE, 0, 0);

        assertEquals(1, bare.following("999").size());
    }

    @Test
    public void bootstrapsOnlyOnceAcrossManyPages() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("set-cookie", "csrftoken=FromRoot; Path=/")
                .setBody("<html></html>"));
        server.enqueue(page("cursor1", "1", "alice"));
        server.enqueue(page(null, "2", "bob"));

        IgWebClient bare = new IgWebClient(server.url("/"), "12345:secret:99", null,
                Sleeper.NONE, 0, 0);
        bare.following("999");

        // 1 bootstrap + 2 pages. A bootstrap per page would double the traffic
        // against an endpoint that is already rate-limit sensitive.
        assertEquals(3, server.getRequestCount());
    }

    @Test
    public void followersUnionsRepeatedPassesAndStopsWhenAPassAddsNothing() throws Exception {
        // Pass 1 sees alice, pass 2 additionally sees bob, pass 3 adds nothing.
        server.enqueue(page(null, "1", "alice"));
        server.enqueue(page(null, "1", "alice", "2", "bob"));
        server.enqueue(page(null, "1", "alice", "2", "bob"));

        Map<String, String> result = client.followers("999", 3);

        assertEquals(2, result.size());
        assertEquals(3, server.getRequestCount());
    }

    @Test
    public void followersStopsEarlyWhenSecondPassAddsNothing() throws Exception {
        server.enqueue(page(null, "1", "alice"));
        server.enqueue(page(null, "1", "alice"));

        Map<String, String> result = client.followers("999", 3);

        assertEquals(1, result.size());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void retriesOnceAfter429ThenSucceeds() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(page(null, "1", "alice"));

        Map<String, String> result = client.following("999");

        assertEquals(1, result.size());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void throwsRateLimitedAfterThreeConsecutive429s() {
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(429));

        assertThrows(IgException.RateLimited.class, () -> client.following("999"));
    }

    @Test
    public void throwsSessionExpiredOn401() {
        server.enqueue(new MockResponse().setResponseCode(401));

        assertThrows(IgException.SessionExpired.class, () -> client.following("999"));
    }

    @Test
    public void reportsARedirectAsRateLimitingRatherThanFollowingIt() {
        // Instagram soft-blocks an over-queried endpoint by 302-ing to the web
        // homepage. Followed, that becomes a 200 full of HTML and looks like a
        // parse bug; unfollowed, it is recognisable as throttling.
        server.enqueue(new MockResponse().setResponseCode(302)
                .setHeader("Location", "https://www.instagram.com/"));

        IgException thrown = assertThrows(IgException.RateLimited.class,
                () -> client.followers("999", 3));
        assertTrue(thrown.getMessage().contains("redirected"));
        // Exactly one request: the redirect must not be chased.
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void throwsFetchWhenA200BodyIsNotJson() {
        // Instagram answers a throttled or challenged session with HTTP 200 and
        // a body that is not the expected object. Observed on a real device
        // when a second scan started right after one finished.
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("<!DOCTYPE html><html><body>login</body></html>"));

        assertThrows(IgException.Fetch.class, () -> client.following("999"));
    }

    @Test
    public void throwsFetchWhenA200BodyIsAJsonPrimitive() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("\"rate limited\""));

        assertThrows(IgException.Fetch.class, () -> client.following("999"));
    }

    @Test
    public void throwsFetchWhenA200BodyIsEmpty() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(""));

        assertThrows(IgException.Fetch.class, () -> client.following("999"));
    }

    @Test
    public void abortsOnNon200SoAPartialListIsNeverReturned() {
        server.enqueue(page("cursor1", "1", "alice"));
        server.enqueue(new MockResponse().setResponseCode(500));

        // The first page succeeded, but a truncated list is indistinguishable
        // from a mass unfollow, so the whole walk must fail.
        assertThrows(IgException.Fetch.class, () -> client.following("999"));
    }
}

package com.kira.followlens.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.kira.followlens.data.FollowLensDatabase;
import com.kira.followlens.data.ScanRepository;
import com.kira.followlens.net.IgWebClient;
import com.kira.followlens.net.Sleeper;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ScanServiceTest {

    private static final String SESSION = "999:secret:1";

    private MockWebServer server;
    private FollowLensDatabase db;
    private ScanRepository repository;
    private FakePrefs prefs;
    private MutableClock clock;
    private ScanService service;

    /** In-memory stand-in so the test does not touch real preferences. */
    private static class FakePrefs implements ScanPrefs {
        long lastScanAt;

        @Override
        public long lastScanAt() {
            return lastScanAt;
        }

        @Override
        public void setLastScanAt(long millis) {
            lastScanAt = millis;
        }
    }

    private static class MutableClock implements Clock {
        long now = 1_000_000L;

        @Override
        public long nowMillis() {
            return now;
        }
    }

    private void enqueueList(String... idUsernamePairs) {
        StringBuilder users = new StringBuilder();
        for (int i = 0; i < idUsernamePairs.length; i += 2) {
            if (users.length() > 0) {
                users.append(',');
            }
            users.append("{\"pk\":\"").append(idUsernamePairs[i])
                    .append("\",\"username\":\"").append(idUsernamePairs[i + 1]).append("\"}");
        }
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"users\":[" + users + "],\"next_max_id\":null}"));
    }

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        Context context = ApplicationProvider.getApplicationContext();
        db = FollowLensDatabase.inMemory(context);
        repository = new ScanRepository(db);
        prefs = new FakePrefs();
        clock = new MutableClock();
        service = new ScanService(repository, prefs, clock,
                sessionId -> new IgWebClient(server.url("/"), sessionId, Sleeper.NONE, 0, 0));
    }

    @After
    public void tearDown() throws Exception {
        db.close();
        server.shutdown();
    }

    @Test
    public void firstScanCommitsAsBaselineWithNoReportedChanges() {
        enqueueList("1", "alice");   // following
        enqueueList("2", "bob");     // followers, pass 1
        enqueueList("2", "bob");     // followers, pass 2 adds nothing

        ScanOutcome outcome = service.run(SESSION, false);

        assertTrue(outcome.ok());
        assertTrue(outcome.baseline());
        assertEquals(0, outcome.addedFollowers());
        assertEquals(0, outcome.removedFollowers());
    }

    @Test
    public void secondScanReportsFollowerChanges() {
        enqueueList("1", "alice");
        enqueueList("2", "bob");
        enqueueList("2", "bob");
        service.run(SESSION, false);

        clock.now += 10_000_000L;
        enqueueList("1", "alice");
        enqueueList("3", "carol");
        enqueueList("3", "carol");

        ScanOutcome outcome = service.run(SESSION, false);

        assertTrue(outcome.ok());
        assertFalse(outcome.baseline());
        assertEquals(1, outcome.addedFollowers());
        assertEquals(1, outcome.removedFollowers());
    }

    @Test
    public void cooldownBlocksAScanAndIssuesNoRequests() {
        prefs.lastScanAt = clock.now - 60_000L;   // 60s ago, cooldown is 600s

        ScanOutcome outcome = service.run(SESSION, false);

        assertFalse(outcome.ok());
        assertTrue(outcome.error().contains("Cooldown"));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void forceBypassesTheCooldown() {
        prefs.lastScanAt = clock.now - 60_000L;
        enqueueList("1", "alice");
        enqueueList("2", "bob");
        enqueueList("2", "bob");

        ScanOutcome outcome = service.run(SESSION, true);

        assertTrue(outcome.ok());
    }

    @Test
    public void rateLimitFailsWithoutWritingAnything() {
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(429));

        ScanOutcome outcome = service.run(SESSION, false);

        assertFalse(outcome.ok());
        assertFalse(repository.hasAnyScan("999"));
    }

    @Test
    public void midScanFailureWritesNothing() {
        enqueueList("1", "alice");                                    // following ok
        server.enqueue(new MockResponse().setResponseCode(500));      // followers fails

        ScanOutcome outcome = service.run(SESSION, false);

        assertFalse(outcome.ok());
        assertFalse(repository.hasAnyScan("999"));
        assertEquals(0, db.dao().edgeCount());
    }

    @Test
    public void failedScanDoesNotAdvanceTheCooldownClock() {
        server.enqueue(new MockResponse().setResponseCode(500));

        service.run(SESSION, false);

        assertEquals(0L, prefs.lastScanAt);
    }
}

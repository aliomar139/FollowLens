package com.kira.followlens.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ScanRepositoryTest {

    private static final String ACCOUNT = "999";

    private FollowLensDatabase db;
    private ScanRepository repository;

    private static Map<String, String> users(String... idUsernamePairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < idUsernamePairs.length; i += 2) {
            map.put(idUsernamePairs[i], idUsernamePairs[i + 1]);
        }
        return map;
    }

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = FollowLensDatabase.inMemory(context);
        repository = new ScanRepository(db);
        repository.ensureAccount(ACCOUNT, "me");
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void firstScanIsBaselineAndProducesNoChangeEvents() {
        long scanId = repository.commitScan(ACCOUNT,
                users("1", "alice"), users("2", "bob"), 100L, 200L);

        assertTrue(repository.changesForScan(scanId).isEmpty());
        assertEquals(2, db.dao().edgeCount());
    }

    @Test
    public void secondScanRecordsAddedAndRemovedFollowers() {
        repository.commitScan(ACCOUNT, users("1", "alice"), users(), 100L, 200L);

        long scanId = repository.commitScan(ACCOUNT,
                users("2", "bob"), users(), 300L, 400L);

        List<ChangeEventEntity> changes = repository.changesForScan(scanId);
        assertEquals(2, changes.size());

        ChangeEventEntity added = changes.stream()
                .filter(c -> c.direction == ChangeDirection.ADDED).findFirst().orElseThrow();
        assertEquals("bob", added.username);
        assertEquals(ListKind.FOLLOWER, added.kind);

        ChangeEventEntity removed = changes.stream()
                .filter(c -> c.direction == ChangeDirection.REMOVED).findFirst().orElseThrow();
        assertEquals("alice", removed.username);
    }

    @Test
    public void removedAccountsAreDeletedFromEdgeNotKeptAsStaleRows() {
        repository.commitScan(ACCOUNT, users("1", "alice"), users(), 100L, 200L);

        repository.commitScan(ACCOUNT, users(), users(), 300L, 400L);

        assertEquals(0, db.dao().edgeCount());
        assertTrue(db.dao().edgeIds(ACCOUNT, ListKind.FOLLOWER).isEmpty());
    }

    @Test
    public void unchangedAccountKeepsFirstSeenScanId() {
        long first = repository.commitScan(ACCOUNT, users("1", "alice"), users(), 100L, 200L);
        long second = repository.commitScan(ACCOUNT, users("1", "alice"), users(), 300L, 400L);

        assertTrue(repository.changesForScan(second).isEmpty());

        EdgeEntity edge = db.dao().edge(ACCOUNT, ListKind.FOLLOWER, "1");
        assertEquals(first, edge.firstSeenScanId);
        assertEquals(second, edge.lastSeenScanId);
    }

    @Test
    public void baselineFlagIsRecordedOnTheScanRow() {
        long first = repository.commitScan(ACCOUNT, users("1", "alice"), users(), 100L, 200L);
        long second = repository.commitScan(ACCOUNT, users("1", "alice"), users(), 300L, 400L);

        assertTrue(repository.wasBaseline(first));
        assertFalse(repository.wasBaseline(second));
    }

    @Test
    public void hasAnyScanReflectsCommittedScans() {
        assertFalse(repository.hasAnyScan(ACCOUNT));

        repository.commitScan(ACCOUNT, users("1", "alice"), users(), 100L, 200L);

        assertTrue(repository.hasAnyScan(ACCOUNT));
    }

    @Test
    public void aFailedCommitLeavesTheDatabaseUntouched() {
        repository.commitScan(ACCOUNT, users("1", "alice"), users("1", "alice"), 100L, 200L);
        int edgesBefore = db.dao().edgeCount();
        int eventsBefore = db.dao().changeEventCount();
        int scansBefore = db.dao().scanCount(ACCOUNT);

        // A null user id forces a failure partway through the transaction.
        Map<String, String> broken = new LinkedHashMap<>();
        broken.put("2", "bob");
        broken.put(null, "corrupt");

        assertThrows(RuntimeException.class,
                () -> repository.commitScan(ACCOUNT, broken, users(), 300L, 400L));

        assertEquals(edgesBefore, db.dao().edgeCount());
        assertEquals(eventsBefore, db.dao().changeEventCount());
        assertEquals(scansBefore, db.dao().scanCount(ACCOUNT));
    }
}

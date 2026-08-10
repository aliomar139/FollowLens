package com.kira.followlens.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Commits scans. The commit is all-or-nothing: a scan either lands completely or
 * writes nothing at all, because a partially written scan looks exactly like a
 * mass unfollow to every downstream reader.
 */
public class ScanRepository {

    private final FollowLensDatabase db;

    public ScanRepository(FollowLensDatabase db) {
        this.db = db;
    }

    public void ensureAccount(String accountId, String username) {
        AccountEntity account = new AccountEntity();
        account.id = accountId;
        account.username = username;
        account.addedAt = System.currentTimeMillis();
        db.dao().upsertAccount(account);
    }

    public boolean hasAnyScan(String accountId) {
        return db.dao().scanCount(accountId) > 0;
    }

    public boolean wasBaseline(long scanId) {
        return db.dao().isBaseline(scanId);
    }

    public List<ChangeEventEntity> changesForScan(long scanId) {
        return db.dao().changesForScan(scanId);
    }

    /**
     * Writes one scan and everything derived from it inside a single
     * transaction.
     *
     * @return the id of the new scan row
     */
    public long commitScan(String accountId,
                           Map<String, String> followers,
                           Map<String, String> following,
                           long startedAt,
                           long finishedAt) {
        FollowLensDao dao = db.dao();
        long[] scanIdHolder = new long[1];

        db.runInTransaction(() -> {
            boolean baseline = dao.scanCount(accountId) == 0;

            ScanEntity scan = new ScanEntity();
            scan.accountId = accountId;
            scan.startedAt = startedAt;
            scan.finishedAt = finishedAt;
            scan.followersCount = followers.size();
            scan.followingCount = following.size();
            scan.isBaseline = baseline;
            long scanId = dao.insertScan(scan);
            scanIdHolder[0] = scanId;

            applyList(dao, accountId, ListKind.FOLLOWER, followers, scanId, baseline, finishedAt);
            applyList(dao, accountId, ListKind.FOLLOWING, following, scanId, baseline, finishedAt);
        });

        return scanIdHolder[0];
    }

    private void applyList(FollowLensDao dao,
                           String accountId,
                           ListKind kind,
                           Map<String, String> current,
                           long scanId,
                           boolean baseline,
                           long occurredAt) {
        // Load the existing rows once, up front. Two reasons: it avoids a point
        // query per account, and the removed accounts' usernames must be read
        // before their rows are deleted or the change log would only have ids.
        Map<String, String> previousUsernames = new HashMap<>();
        Map<String, Long> firstSeen = new HashMap<>();
        for (EdgeEntity existing : dao.edgesNow(accountId, kind)) {
            previousUsernames.put(existing.userId, existing.username);
            firstSeen.put(existing.userId, existing.firstSeenScanId);
        }

        GraphDiff diff = GraphDiff.of(previousUsernames.keySet(), current.keySet());

        List<EdgeEntity> upserts = new ArrayList<>();
        for (Map.Entry<String, String> entry : current.entrySet()) {
            String userId = entry.getKey();
            if (userId == null) {
                throw new IllegalArgumentException("user id must not be null");
            }
            EdgeEntity edge = new EdgeEntity();
            edge.accountId = accountId;
            edge.kind = kind;
            edge.userId = userId;
            edge.username = entry.getValue();
            Long seen = firstSeen.get(userId);
            edge.firstSeenScanId = seen == null ? scanId : seen;
            edge.lastSeenScanId = scanId;
            upserts.add(edge);
        }
        dao.upsertEdges(upserts);
        deleteInChunks(dao, accountId, kind, diff.removed());

        if (baseline) {
            return;
        }

        List<ChangeEventEntity> events = new ArrayList<>();
        for (String userId : diff.added()) {
            events.add(event(accountId, scanId, kind, ChangeDirection.ADDED, userId,
                    current.get(userId), occurredAt));
        }
        for (String userId : diff.removed()) {
            String username = previousUsernames.get(userId);
            events.add(event(accountId, scanId, kind, ChangeDirection.REMOVED, userId,
                    username == null ? userId : username, occurredAt));
        }
        if (!events.isEmpty()) {
            dao.insertChangeEvents(events);
        }
    }

    /**
     * SQLite caps the number of bound variables in one statement, so a large
     * batch of removals has to be deleted in slices.
     */
    private void deleteInChunks(FollowLensDao dao, String accountId, ListKind kind,
                                Set<String> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        List<String> all = new ArrayList<>(userIds);
        int chunkSize = 500;
        for (int start = 0; start < all.size(); start += chunkSize) {
            dao.deleteEdges(accountId, kind,
                    all.subList(start, Math.min(start + chunkSize, all.size())));
        }
    }

    private ChangeEventEntity event(String accountId, long scanId, ListKind kind,
                                    ChangeDirection direction, String userId, String username,
                                    long occurredAt) {
        ChangeEventEntity e = new ChangeEventEntity();
        e.accountId = accountId;
        e.scanId = scanId;
        e.kind = kind;
        e.direction = direction;
        e.userId = userId;
        e.username = username;
        e.occurredAt = occurredAt;
        return e;
    }
}

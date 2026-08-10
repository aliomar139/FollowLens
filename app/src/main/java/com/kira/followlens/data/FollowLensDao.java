package com.kira.followlens.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FollowLensDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAccount(AccountEntity account);

    @Query("SELECT * FROM account LIMIT 1")
    AccountEntity account();

    @Insert
    long insertScan(ScanEntity scan);

    @Query("SELECT COUNT(*) FROM scan WHERE accountId = :accountId")
    int scanCount(String accountId);

    /** Observable variant: the dashboard needs to know when a second scan lands. */
    @Query("SELECT COUNT(*) FROM scan WHERE accountId = :accountId")
    LiveData<Integer> scanCountLive(String accountId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertEdges(List<EdgeEntity> edges);

    @Query("DELETE FROM edge WHERE accountId = :accountId AND kind = :kind AND userId IN (:userIds)")
    void deleteEdges(String accountId, ListKind kind, List<String> userIds);

    @Query("SELECT userId FROM edge WHERE accountId = :accountId AND kind = :kind")
    List<String> edgeIds(String accountId, ListKind kind);

    /**
     * Every current row of one list. The commit loads these once rather than
     * issuing a point query per account, which for a 5000-follower list is the
     * difference between one query and five thousand.
     */
    @Query("SELECT * FROM edge WHERE accountId = :accountId AND kind = :kind")
    List<EdgeEntity> edgesNow(String accountId, ListKind kind);

    @Query("SELECT * FROM edge WHERE accountId = :accountId AND kind = :kind AND userId = :userId")
    EdgeEntity edge(String accountId, ListKind kind, String userId);

    @Query("SELECT COUNT(*) FROM edge")
    int edgeCount();

    @Query("SELECT isBaseline FROM scan WHERE id = :scanId")
    boolean isBaseline(long scanId);

    @Insert
    void insertChangeEvents(List<ChangeEventEntity> events);

    @Query("SELECT * FROM change_event WHERE scanId = :scanId ORDER BY id")
    List<ChangeEventEntity> changesForScan(long scanId);

    @Query("SELECT COUNT(*) FROM change_event")
    int changeEventCount();

    @Query("SELECT * FROM change_event WHERE accountId = :accountId ORDER BY occurredAt DESC, id DESC")
    LiveData<List<ChangeEventEntity>> changeFeed(String accountId);

    /**
     * The most recent scan that could have produced a change.
     *
     * Deliberately not simply MAX(id): the first scan is a baseline, where every
     * account is seen for the first time. Anchoring "new" to it would light up
     * the entire list on the very first run, which tells the user nothing. Until
     * a second scan lands this is NULL, and no row compares equal to NULL, so
     * nothing is flagged.
     */
    String LATEST = "(SELECT MAX(id) FROM scan WHERE accountId = :accountId AND isBaseline = 0)";

    /**
     * The mutual flag is an EXISTS subquery rather than a per-row lookup, so the
     * badge costs nothing extra no matter how long the list is. The same is true
     * of the new flag below it.
     */
    @Query("SELECT e.accountId, e.kind, e.userId, e.username,"
            + " EXISTS(SELECT 1 FROM edge o WHERE o.accountId = e.accountId"
            + " AND o.userId = e.userId AND o.kind <> e.kind) AS mutual,"
            + " (e.firstSeenScanId = " + LATEST + ") AS isNew"
            + " FROM edge e WHERE e.accountId = :accountId AND e.kind = :kind"
            + " ORDER BY isNew DESC, e.username COLLATE NOCASE")
    LiveData<List<EdgeRow>> edges(String accountId, ListKind kind);

    /**
     * Accounts present in both lists. A mutual is new when either half of the
     * pair arrived in the latest scan — following someone back makes the mutual
     * new even though you have followed them for months.
     */
    @Query("SELECT e.accountId, e.kind, e.userId, e.username, 1 AS mutual,"
            + " (e.firstSeenScanId = " + LATEST
            + " OR EXISTS(SELECT 1 FROM edge o WHERE o.accountId = e.accountId"
            + " AND o.userId = e.userId AND o.kind = 'FOLLOWING'"
            + " AND o.firstSeenScanId = " + LATEST + ")) AS isNew"
            + " FROM edge e WHERE e.accountId = :accountId AND e.kind = 'FOLLOWER'"
            + " AND e.userId IN (SELECT userId FROM edge WHERE accountId = :accountId"
            + " AND kind = 'FOLLOWING') ORDER BY isNew DESC, e.username COLLATE NOCASE")
    LiveData<List<EdgeRow>> mutuals(String accountId);

    /**
     * You follow them, they do not follow you back.
     *
     * There are two ways into this list and both count as new: you followed
     * someone who has not followed back, or someone you already followed
     * unfollowed you in the latest scan. The second is the case people actually
     * open this screen for, and it leaves no new edge behind — only a removal
     * event — which is why it is checked separately.
     */
    @Query("SELECT e.accountId, e.kind, e.userId, e.username, 0 AS mutual,"
            + " (e.firstSeenScanId = " + LATEST
            + " OR EXISTS(SELECT 1 FROM change_event c WHERE c.accountId = e.accountId"
            + " AND c.userId = e.userId AND c.kind = 'FOLLOWER'"
            + " AND c.direction = 'REMOVED' AND c.scanId = " + LATEST + ")) AS isNew"
            + " FROM edge e WHERE e.accountId = :accountId AND e.kind = 'FOLLOWING'"
            + " AND e.userId NOT IN (SELECT userId FROM edge WHERE accountId = :accountId"
            + " AND kind = 'FOLLOWER') ORDER BY isNew DESC, e.username COLLATE NOCASE")
    LiveData<List<EdgeRow>> notFollowingBack(String accountId);

    /**
     * They follow you, you do not follow them back. The mirror of the list
     * above: either they just followed you, or you just unfollowed them.
     */
    @Query("SELECT e.accountId, e.kind, e.userId, e.username, 0 AS mutual,"
            + " (e.firstSeenScanId = " + LATEST
            + " OR EXISTS(SELECT 1 FROM change_event c WHERE c.accountId = e.accountId"
            + " AND c.userId = e.userId AND c.kind = 'FOLLOWING'"
            + " AND c.direction = 'REMOVED' AND c.scanId = " + LATEST + ")) AS isNew"
            + " FROM edge e WHERE e.accountId = :accountId AND e.kind = 'FOLLOWER'"
            + " AND e.userId NOT IN (SELECT userId FROM edge WHERE accountId = :accountId"
            + " AND kind = 'FOLLOWING') ORDER BY isNew DESC, e.username COLLATE NOCASE")
    LiveData<List<EdgeRow>> fans(String accountId);

    @Query("SELECT * FROM scan WHERE accountId = :accountId ORDER BY finishedAt DESC LIMIT 1")
    LiveData<ScanEntity> latestScan(String accountId);

    /**
     * The latest scan and the one before it, newest first.
     *
     * Two rows rather than one because every number on the dashboard is shown
     * with how it moved, and a delta needs both ends. Fetching them together
     * keeps the pair consistent: two separate queries could deliver a new
     * "current" against a stale "previous" and briefly show a wrong change.
     */
    @Query("SELECT * FROM scan WHERE accountId = :accountId ORDER BY finishedAt DESC LIMIT 2")
    LiveData<List<ScanEntity>> latestTwoScans(String accountId);

    /** Newest first, for the history screen. */
    @Query("SELECT * FROM scan WHERE accountId = :accountId ORDER BY finishedAt DESC LIMIT 120")
    LiveData<List<ScanEntity>> scanHistory(String accountId);
}

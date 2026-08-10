package com.kira.followlens.scan;

import com.kira.followlens.data.ChangeDirection;
import com.kira.followlens.data.ChangeEventEntity;
import com.kira.followlens.data.ListKind;
import com.kira.followlens.data.ScanRepository;
import com.kira.followlens.net.IgException;
import com.kira.followlens.net.IgWebClient;
import com.kira.followlens.net.SessionId;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Runs one scan: cooldown check, fetch both lists in full, then commit. Port of
 * backend/scanner.py. Nothing is written unless both lists arrived complete.
 */
public class ScanService {

    public static final long COOLDOWN_MILLIS = 600_000L;
    public static final int FOLLOWER_PASSES = 3;
    public static final double DELAY_SECONDS = 3.0;
    public static final double JITTER_SECONDS = 2.5;

    /** Lets tests point the client at a MockWebServer. */
    public interface ClientFactory {
        IgWebClient create(String sessionId);
    }

    private final ScanRepository repository;
    private final ScanPrefs prefs;
    private final Clock clock;
    private final ClientFactory clientFactory;

    public ScanService(ScanRepository repository, ScanPrefs prefs, Clock clock,
                       ClientFactory clientFactory) {
        this.repository = repository;
        this.prefs = prefs;
        this.clock = clock;
        this.clientFactory = clientFactory;
    }

    public ScanOutcome run(String sessionId, boolean force) {
        return run(sessionId, force, ScanProgress.NONE);
    }

    /**
     * @param progress told as each page lands, so a caller can show how far the
     *                 scan has got. Reporting never affects the outcome: a
     *                 listener that throws would fail a scan that had otherwise
     *                 succeeded, so it is kept to the one call below and nothing
     *                 downstream depends on it.
     */
    public ScanOutcome run(String sessionId, boolean force, ScanProgress progress) {
        if (sessionId == null) {
            return ScanOutcome.skipped("No session. Log in to Instagram first.");
        }

        long now = clock.nowMillis();
        long elapsed = now - prefs.lastScanAt();
        if (!force && elapsed < COOLDOWN_MILLIS) {
            long remaining = (COOLDOWN_MILLIS - elapsed) / 1000;
            return ScanOutcome.skipped("Cooldown active: try again in " + remaining + "s.");
        }

        String accountId;
        try {
            accountId = SessionId.userIdOf(sessionId);
        } catch (IllegalArgumentException e) {
            return ScanOutcome.skipped("Stored session is malformed. Log in again.");
        }

        IgWebClient client = clientFactory.create(sessionId);
        client.setPageListener((kind, collected) -> progress.onProgress(
                "following".equals(kind)
                        ? ScanProgress.Stage.FOLLOWING
                        : ScanProgress.Stage.FOLLOWERS,
                collected));

        Map<String, String> following;
        Map<String, String> followers;
        try {
            // Both lists are fetched completely before anything is written.
            following = client.following(accountId);

            // One cheap request that can save an entire extra walk of the
            // followers list. It is allowed to fail: null simply means the
            // fetch below decides when to stop the way it always has.
            Integer expectedFollowers = client.followerCount(accountId);
            followers = client.followers(accountId, FOLLOWER_PASSES, expectedFollowers);
        } catch (IgException.SessionExpired e) {
            return ScanOutcome.skipped(e.getMessage());
        } catch (IgException | IOException e) {
            return ScanOutcome.failed(e.getMessage());
        }

        progress.onProgress(ScanProgress.Stage.SAVING, followers.size() + following.size());

        repository.ensureAccount(accountId, accountId);
        long scanId = repository.commitScan(accountId, followers, following, now,
                clock.nowMillis());
        prefs.setLastScanAt(clock.nowMillis());

        List<ChangeEventEntity> changes = repository.changesForScan(scanId);
        int added = 0;
        int removed = 0;
        for (ChangeEventEntity change : changes) {
            if (change.kind != ListKind.FOLLOWER) {
                continue;
            }
            if (change.direction == ChangeDirection.ADDED) {
                added++;
            } else {
                removed++;
            }
        }

        return ScanOutcome.committed(scanId, repository.wasBaseline(scanId), added, removed);
    }
}

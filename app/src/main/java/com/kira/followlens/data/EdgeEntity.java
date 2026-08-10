package com.kira.followlens.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;

/**
 * Current graph state, and only current state. An account that leaves a list has
 * its row deleted and the removal recorded in change_event, so this table always
 * answers "who is in this list right now" with no filtering.
 */
@Entity(tableName = "edge", primaryKeys = {"accountId", "kind", "userId"})
public class EdgeEntity {

    @NonNull
    public String accountId = "";

    @NonNull
    public ListKind kind = ListKind.FOLLOWER;

    @NonNull
    public String userId = "";

    public String username;

    public long firstSeenScanId;

    public long lastSeenScanId;
}

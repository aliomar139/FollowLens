package com.kira.followlens.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Append-only log of every membership change, the source of all history. */
@Entity(tableName = "change_event")
public class ChangeEventEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String accountId;

    public long scanId;

    public ListKind kind;

    public ChangeDirection direction;

    public String userId;

    public String username;

    public long occurredAt;
}

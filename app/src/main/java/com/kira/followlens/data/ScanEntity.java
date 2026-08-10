package com.kira.followlens.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** One row per completed scan. Failed scans are never inserted. */
@Entity(tableName = "scan")
public class ScanEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String accountId;

    public long startedAt;

    public long finishedAt;

    public int followersCount;

    public int followingCount;

    /** True for an account's first scan, which produces no change events. */
    public boolean isBaseline;
}

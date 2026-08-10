package com.kira.followlens.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "account")
public class AccountEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    public String username;

    public long addedAt;
}

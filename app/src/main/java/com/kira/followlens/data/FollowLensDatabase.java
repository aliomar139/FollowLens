package com.kira.followlens.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(
        entities = {AccountEntity.class, ScanEntity.class, EdgeEntity.class,
                ChangeEventEntity.class},
        version = 1,
        exportSchema = false)
@TypeConverters(Converters.class)
public abstract class FollowLensDatabase extends RoomDatabase {

    private static volatile FollowLensDatabase instance;

    public abstract FollowLensDao dao();

    public static FollowLensDatabase get(Context context) {
        if (instance == null) {
            synchronized (FollowLensDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                            FollowLensDatabase.class, "followlens.db").build();
                }
            }
        }
        return instance;
    }

    /** For tests: a throwaway database that allows queries on the test thread. */
    public static FollowLensDatabase inMemory(Context context) {
        return Room.inMemoryDatabaseBuilder(context, FollowLensDatabase.class)
                .allowMainThreadQueries()
                .build();
    }
}

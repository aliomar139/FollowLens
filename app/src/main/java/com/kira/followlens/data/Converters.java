package com.kira.followlens.data;

import androidx.room.TypeConverter;

/** Stores enums as readable TEXT rather than ordinals. */
public class Converters {

    @TypeConverter
    public String fromListKind(ListKind kind) {
        return kind == null ? null : kind.name();
    }

    @TypeConverter
    public ListKind toListKind(String value) {
        return value == null ? null : ListKind.valueOf(value);
    }

    @TypeConverter
    public String fromDirection(ChangeDirection direction) {
        return direction == null ? null : direction.name();
    }

    @TypeConverter
    public ChangeDirection toDirection(String value) {
        return value == null ? null : ChangeDirection.valueOf(value);
    }
}

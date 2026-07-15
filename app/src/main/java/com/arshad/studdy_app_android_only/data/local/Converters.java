package com.arshad.studdy_app_android_only.data.local;

import androidx.room.TypeConverter;
import com.arshad.studdy_app_android_only.data.local.entity.SyncStatus;

/**
 * Type converters for Room Database to store non-standard SQLite types.
 */
public class Converters {

    @TypeConverter
    public static SyncStatus toSyncStatus(String value) {
        return value == null ? null : SyncStatus.valueOf(value);
    }

    @TypeConverter
    public static String fromSyncStatus(SyncStatus status) {
        return status == null ? null : status.name();
    }
}

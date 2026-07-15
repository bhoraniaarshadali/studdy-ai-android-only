package com.arshad.studdy_app_android_only.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.arshad.studdy_app_android_only.data.local.dao.ExamDraftDao;
import com.arshad.studdy_app_android_only.data.local.dao.LocalExamAttemptDao;
import com.arshad.studdy_app_android_only.data.local.entity.ExamDraft;
import com.arshad.studdy_app_android_only.data.local.entity.LocalExamAttempt;


@Database(entities = {ExamDraft.class, LocalExamAttempt.class}, version = 2, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract ExamDraftDao examDraftDao();
    public abstract LocalExamAttemptDao localExamAttemptDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "studdy_local.db"
                    )
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}

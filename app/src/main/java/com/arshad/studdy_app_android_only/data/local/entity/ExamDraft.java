package com.arshad.studdy_app_android_only.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Local draft entity holding configuration and generated question states.
 */
@Entity(tableName = "exam_drafts")
public class ExamDraft {

    @PrimaryKey
    @NonNull
    public String id; // maps to teacherId

    public String title;

    @ColumnInfo(name = "questions_json")
    public String questionsJson;

    @ColumnInfo(name = "timer_mode")
    public String timerMode;

    @ColumnInfo(name = "duration_minutes")
    public Integer durationMinutes;

    @ColumnInfo(name = "window_start")
    public String windowStart;

    @ColumnInfo(name = "window_end")
    public String windowEnd;

    @ColumnInfo(name = "result_mode")
    public String resultMode;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;
}

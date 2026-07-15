package com.arshad.studdy_app_android_only.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

/**
 * Local cache representing a student's active exam attempt and answers.
 * Serves as a pending-sync buffer and local backup for offline resilience.
 *
 * Primary key is composite, uniquely identified by (exam_id, enrollment_number).
 */
@Entity(
    tableName = "local_exam_attempts",
    primaryKeys = {"exam_id", "enrollment_number"}
)
public class LocalExamAttempt {

    @NonNull
    @ColumnInfo(name = "exam_id")
    public String examId; // maps to Exam.id or Exam.code depending on join key (UUID or code)

    @NonNull
    @ColumnInfo(name = "enrollment_number")
    public String enrollmentNumber;

    /**
     * JSON representation of the student's current answers.
     * Example structure:
     * A JSON array of integers matching option indices per question (e.g. [-1, 2, 0, 1, -1]),
     * where index matches the question index, and the value is the selected option index (or -1 if unanswered).
     */
    @ColumnInfo(name = "current_answers")
    public String currentAnswers;

    @ColumnInfo(name = "last_question_index")
    public int lastQuestionIndex;

    /**
     * Timestamp of the last local update, stored as Unix epoch milliseconds
     * (System.currentTimeMillis()). Used for last-write-wins conflict resolution.
     */
    @ColumnInfo(name = "local_updated_at")
    public long localUpdatedAt;

    @ColumnInfo(name = "sync_status")
    public SyncStatus syncStatus;

    /**
     * Maps to the row ID in the remote Supabase 'exam_sessions' table (bigint / Long).
     * This field remains null until the first successful server synchronization / session creation,
     * and is populated once the server assigns or confirms the session ID.
     */
    @ColumnInfo(name = "server_session_id")
    public Long serverSessionId;

    // TODO: In Phase 3 (WorkManager sync worker), we may add the following debugging fields:
    // @ColumnInfo(name = "last_sync_attempt_at")
    // public Long lastSyncAttemptAt;
    //
    // @ColumnInfo(name = "retry_count")
    // public int retryCount;
    //
    // @ColumnInfo(name = "last_sync_error")
    // public String lastSyncError;
}

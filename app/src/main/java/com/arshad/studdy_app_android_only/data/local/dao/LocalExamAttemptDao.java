package com.arshad.studdy_app_android_only.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.arshad.studdy_app_android_only.data.local.entity.LocalExamAttempt;
import java.util.List;

@Dao
public interface LocalExamAttemptDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(LocalExamAttempt attempt);

    @Query("SELECT * FROM local_exam_attempts WHERE exam_id = :examId AND enrollment_number = :enrollmentNumber LIMIT 1")
    LocalExamAttempt getAttempt(String examId, String enrollmentNumber);

    @Query("SELECT * FROM local_exam_attempts WHERE sync_status IN ('PENDING', 'SUBMIT_PENDING') ORDER BY local_updated_at ASC")
    List<LocalExamAttempt> getUnsyncedAttempts();

    @Query("DELETE FROM local_exam_attempts WHERE exam_id = :examId AND enrollment_number = :enrollmentNumber")
    void deleteAttempt(String examId, String enrollmentNumber);

    @Query("UPDATE local_exam_attempts SET sync_status = :status, local_updated_at = :updatedAt WHERE exam_id = :examId AND enrollment_number = :enrollmentNumber")
    void updateSyncStatus(String examId, String enrollmentNumber, String status, long updatedAt);

    @Query("SELECT * FROM local_exam_attempts")
    List<LocalExamAttempt> getAllAttempts();
}

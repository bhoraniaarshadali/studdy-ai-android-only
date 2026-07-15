package com.arshad.studdy_app_android_only.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.arshad.studdy_app_android_only.data.local.entity.ExamDraft;

@Dao
public interface ExamDraftDao {

    @Query("SELECT * FROM exam_drafts WHERE id = :id LIMIT 1")
    ExamDraft getDraft(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertDraft(ExamDraft draft);

    @Query("DELETE FROM exam_drafts WHERE id = :id")
    void deleteDraft(String id);
}

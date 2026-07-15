package com.arshad.studdy_app_android_only.data.remote.api;

import com.arshad.studdy_app_android_only.data.model.Exam;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * PostgREST interface for the {@code public.exams} table.
 * Base URL: https://odbycjunebfncpkkbbew.supabase.co/rest/v1/
 */
public interface ExamApi {

    /** POST /exams — publish/insert a new exam */
    @POST("exams")
    Call<List<Exam>> insertExam(@Body Exam exam);

    /**
     * Fetch a single exam by its 6-character code.
     * GET /exams?code=eq.{code}&select=*
     * Returns a list (PostgREST always returns arrays); take index 0.
     */
    @GET("exams")
    Call<List<Exam>> getExamByCode(
            @Query("code") String codeFilter,   // "eq.ABC123"
            @Query("select") String select       // "*"
    );

    /**
     * All exams — used by the student dashboard (anon SELECT policy allows this).
     * GET /exams?select=*&order=created_at.desc
     */
    @GET("exams")
    Call<List<Exam>> getAllExams(
            @Query("select") String select,
            @Query("order") String order        // "created_at.desc"
    );

    /**
     * Teacher's own exams — filtered by teacher_id.
     * GET /exams?teacher_id=eq.{uid}&select=*&order=created_at.desc
     */
    @GET("exams")
    Call<List<Exam>> getTeacherExams(
            @Query("teacher_id") String teacherIdFilter,  // "eq.{uid}"
            @Query("select") String select,
            @Query("order") String order
    );

    /** PATCH /exams?id=eq.{uuid} — update properties like results_published */
    @retrofit2.http.PATCH("exams")
    Call<List<Exam>> updateExam(
            @Query("id") String idFilter,    // "eq.{uuid}"
            @Body java.util.Map<String, Object> patch
    );

    /** DELETE /exams?id=eq.{uuid} — delete exam */
    @retrofit2.http.DELETE("exams")
    Call<Void> deleteExam(
            @Query("id") String idFilter     // "eq.{uuid}"
    );
}

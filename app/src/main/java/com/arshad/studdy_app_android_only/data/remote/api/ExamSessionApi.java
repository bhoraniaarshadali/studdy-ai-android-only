package com.arshad.studdy_app_android_only.data.remote.api;

import com.arshad.studdy_app_android_only.data.model.ExamSession;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

/** PostgREST interface for {@code public.exam_sessions}. */
public interface ExamSessionApi {

    /** POST /exam_sessions — create new session on exam start. */
    @POST("exam_sessions")
    Call<List<ExamSession>> createSession(@Body ExamSession session);

    /** PATCH /exam_sessions?id=eq.{n} — update any subset of fields. */
    @PATCH("exam_sessions")
    Call<List<ExamSession>> updateSession(
            @Query("id") String idFilter,    // "eq.{bigint id}"
            @Body Map<String, Object> patch
    );

    /** GET for teacher monitor — all active sessions for an exam. */
    @GET("exam_sessions")
    Call<List<ExamSession>> getSessionsForExam(
            @Query("exam_code") String codeFilter,  // "eq.CODE"
            @Query("status") String statusFilter,    // "eq.active"
            @Query("select") String select,
            @Query("order") String order
    );

    /** GET to retrieve a student's active exam session for resumption. */
    @GET("exam_sessions")
    Call<List<ExamSession>> getActiveSession(
            @Query("exam_code") String codeFilter,
            @Query("enrollment_number") String enrollmentFilter,
            @Query("status") String statusFilter,
            @Query("select") String select
    );

    /** DELETE /exam_sessions?id=eq.{id} — delete exam session */
    @retrofit2.http.DELETE("exam_sessions")
    Call<Void> deleteSession(
            @Query("id") String idFilter
    );
}

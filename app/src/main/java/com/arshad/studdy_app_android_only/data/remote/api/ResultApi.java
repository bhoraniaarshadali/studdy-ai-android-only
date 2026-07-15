package com.arshad.studdy_app_android_only.data.remote.api;

import com.arshad.studdy_app_android_only.data.model.Result;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/** PostgREST interface for {@code public.results}. */
public interface ResultApi {

    /**
     * Check if a student already has a result for a given exam.
     * GET /results?exam_code=eq.{code}&enrollment_number=eq.{n}&select=id
     */
    @GET("results")
    Call<List<Result>> checkExistingResult(
            @Query("exam_code") String codeFilter,
            @Query("enrollment_number") String enrollmentFilter,
            @Query("select") String select
    );

    /**
     * All results for an exam — used by teacher results screen.
     * GET /results?exam_code=eq.{code}&select=*&order=score.desc
     */
    @GET("results")
    Call<List<Result>> getResultsForExam(
            @Query("exam_code") String codeFilter,
            @Query("select") String select,
            @Query("order") String order
    );

    /**
     * All results for a student.
     * GET /results?enrollment_number=eq.{enrollmentNumber}&select={select}
     */
    @GET("results")
    Call<List<Result>> getResultsForStudent(
            @Query("enrollment_number") String enrollmentFilter,
            @Query("select") String select
    );

    /**
     * Submit a student's result.
     * POST /results
     * Returns 409 if UNIQUE(exam_code, enrollment_number) is violated.
     */
    @POST("results")
    Call<List<Result>> submitResult(@Body Result result);
}

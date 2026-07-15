package com.arshad.studdy_app_android_only.data.remote.api;

import com.arshad.studdy_app_android_only.data.model.Student;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

/** PostgREST interface for {@code public.students}. */
public interface StudentApi {

    /** GET /students?enrollment_number=eq.{n}&select=* */
    @GET("students")
    Call<List<Student>> getByEnrollment(
            @Query("enrollment_number") String enrollmentFilter,  // "eq.XXX"
            @Query("select") String select
    );

    /** POST /students  — insert new student row. */
    @POST("students")
    Call<List<Student>> insertStudent(@Body Student student);

    /**
     * PATCH /students?enrollment_number=eq.{n}  — update name only.
     * Body: {"name": "..."}
     */
    @PATCH("students")
    Call<List<Student>> updateName(
            @Query("enrollment_number") String enrollmentFilter,  // "eq.XXX"
            @Body Map<String, String> body
    );
}

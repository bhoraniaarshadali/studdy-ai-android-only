package com.arshad.studdy_app_android_only.data.remote.api;

import com.arshad.studdy_app_android_only.data.model.GeneratedPaper;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import retrofit2.http.Body;

/**
 * PostgREST API interface for {@code public.generated_papers}.
 */
public interface PaperApi {

    /** GET /generated_papers — fetch list of printable papers */
    @GET("generated_papers")
    Call<List<GeneratedPaper>> getPapers(
            @Query("select") String select,
            @Query("order") String order
    );

    /** DELETE /generated_papers?id=eq.{id} — delete a paper record */
    @DELETE("generated_papers")
    Call<Void> deletePaper(
            @Query("id") String idFilter      // "eq.{id}"
    );

    /** POST /generated_papers — create a new paper record */
    @POST("generated_papers")
    Call<List<GeneratedPaper>> createPaper(
            @Body GeneratedPaper paper
    );
}

package com.arshad.studdy_app_android_only.data.remote.api;

import com.arshad.studdy_app_android_only.data.remote.dto.GeminiRequest;
import com.arshad.studdy_app_android_only.data.remote.dto.GeminiResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Retrofit interface for Google Gemini Content Generation API.
 */
public interface GeminiApi {

    /**
     * Generate MCQ questions based on raw text prompt.
     * POST /v1beta/models/gemini-flash-latest:generateContent?key={API_KEY}
     */
    @POST("v1beta/models/gemini-flash-latest:generateContent")
    Call<GeminiResponse> generateContent(
            @Query("key") String apiKey,
            @Body GeminiRequest request
    );
}

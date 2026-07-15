package com.arshad.studdy_app_android_only.data.remote.api;

import com.arshad.studdy_app_android_only.data.remote.dto.AuthResponse;
import com.arshad.studdy_app_android_only.data.remote.dto.PasswordResetRequest;
import com.arshad.studdy_app_android_only.data.remote.dto.RefreshTokenRequest;
import com.arshad.studdy_app_android_only.data.remote.dto.SignInRequest;
import com.arshad.studdy_app_android_only.data.remote.dto.SignUpRequest;

import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Query;

/**
 * Retrofit interface for the Supabase Auth v1 REST endpoints.
 *
 * Base URL: https://odbycjunebfncpkkbbew.supabase.co/auth/v1/
 *
 * Reference: https://supabase.com/docs/reference/api/introduction
 */
public interface AuthApi {

    /**
     * Sign up a new teacher.
     * POST /auth/v1/signup
     */
    @POST("signup")
    Call<AuthResponse> signUp(@Body SignUpRequest body);

    /**
     * Sign in with email + password.
     * POST /auth/v1/token?grant_type=password
     */
    @POST("token")
    Call<AuthResponse> signIn(
            @Query("grant_type") String grantType,
            @Body SignInRequest body
    );

    /**
     * Refresh access token.
     * POST /auth/v1/token?grant_type=refresh_token
     */
    @POST("token?grant_type=refresh_token")
    Call<AuthResponse> refreshToken(@Body RefreshTokenRequest body);

    /**
     * Send a password-reset email.
     * POST /auth/v1/recover
     */
    @POST("recover")
    Call<ResponseBody> resetPassword(
            @Query("redirect_to") String redirectTo,
            @Body PasswordResetRequest body
    );

    /**
     * Update user (e.g. set new password).
     * PUT /auth/v1/user
     */
    @PUT("user")
    Call<ResponseBody> updateUser(@Body Map<String, Object> body);
}

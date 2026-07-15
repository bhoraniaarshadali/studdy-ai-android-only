package com.arshad.studdy_app_android_only.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Request body for Supabase Auth sign-in.
 * POST /auth/v1/token?grant_type=password
 */
public class SignInRequest {

    @SerializedName("email")
    private final String email;

    @SerializedName("password")
    private final String password;

    public SignInRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }
}

package com.arshad.studdy_app_android_only.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Request body for Supabase password-reset email.
 * POST /auth/v1/recover
 */
public class PasswordResetRequest {

    @SerializedName("email")
    private final String email;

    public PasswordResetRequest(String email) {
        this.email = email;
    }
}

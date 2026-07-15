package com.arshad.studdy_app_android_only.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Request body for Supabase Auth sign-up.
 * POST /auth/v1/signup
 */
public class SignUpRequest {

    @SerializedName("email")
    private final String email;

    @SerializedName("password")
    private final String password;

    @SerializedName("data")
    private final UserMetadata data;

    public SignUpRequest(String email, String password, String name) {
        this.email = email;
        this.password = password;
        this.data = new UserMetadata(name, "teacher");
    }

    // ── Nested metadata class ──────────────────────────────────────────────

    public static class UserMetadata {
        @SerializedName("name")
        public final String name;

        @SerializedName("role")
        public final String role;

        public UserMetadata(String name, String role) {
            this.name = name;
            this.role = role;
        }
    }
}

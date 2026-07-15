package com.arshad.studdy_app_android_only.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Response from Supabase Auth sign-in (/auth/v1/token) and sign-up (/auth/v1/signup).
 *
 * Key fields used by the app:
 *   • access_token  — teacher JWT; stored in EncryptedSharedPreferences
 *   • refresh_token — used for silent session refresh
 *   • user.id       — teacher_id written into exams rows
 *   • user.email    — displayed in teacher dashboard
 *   • user.user_metadata.name — teacher display name
 */
public class AuthResponse {

    @SerializedName("access_token")
    public String accessToken;

    @SerializedName("refresh_token")
    public String refreshToken;

    @SerializedName("token_type")
    public String tokenType;

    @SerializedName("expires_in")
    public long expiresIn;

    @SerializedName("user")
    public User user;

    // ── Nested user object ─────────────────────────────────────────────────

    public static class User {
        @SerializedName("id")
        public String id;

        @SerializedName("email")
        public String email;

        @SerializedName("user_metadata")
        public UserMetadata userMetadata;

        public static class UserMetadata {
            @SerializedName("name")
            public String name;

            @SerializedName("role")
            public String role;
        }
    }

    // ── Auth error response ────────────────────────────────────────────────

    public static class AuthError {
        @SerializedName("error")
        public String error;

        @SerializedName("error_description")
        public String errorDescription;

        @SerializedName("msg")
        public String msg;

        @SerializedName("message")
        public String message;

        /** Returns the most descriptive error string available. */
        public String getBestMessage() {
            if (errorDescription != null && !errorDescription.isEmpty()) return errorDescription;
            if (msg != null && !msg.isEmpty()) return msg;
            if (message != null && !message.isEmpty()) return message;
            if (error != null && !error.isEmpty()) return error;
            return "Unknown error";
        }
    }
}

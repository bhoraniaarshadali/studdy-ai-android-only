package com.arshad.studdy_app_android_only.network;

import androidx.annotation.Nullable;

import com.arshad.studdy_app_android_only.BuildConfig;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * OkHttp interceptor that attaches the required Supabase headers on every request:
 *
 *   apikey: <publishable-or-anon-key>          — always present
 *   Authorization: Bearer <token>              — anon key for students,
 *                                                teacher JWT for teachers
 *   Content-Type: application/json             — always present
 *   Accept: application/json                   — always present
 *   Prefer: return=representation              — enables PostgREST to echo
 *                                                inserted/updated rows back
 *
 * Pass {@code null} for {@code accessToken} for unauthenticated (anon) requests.
 * Pass the teacher's JWT access token for authenticated operations.
 */
public class SupabaseHeaderInterceptor implements Interceptor {

    private final String accessToken;

    public SupabaseHeaderInterceptor(@Nullable String accessToken) {
        this.accessToken = accessToken;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        String bearerToken = (accessToken != null && !accessToken.isEmpty())
                ? accessToken
                : BuildConfig.SUPABASE_ANON_KEY;

        // Respect existing Authorization header (e.g., from Authenticator retry)
        String existingAuth = request.header("Authorization");
        if (existingAuth != null && existingAuth.startsWith("Bearer ")) {
            bearerToken = existingAuth.substring(7);
        }

        request = request.newBuilder()
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + bearerToken)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Prefer", "return=representation")
                .build();

        return chain.proceed(request);
    }
}

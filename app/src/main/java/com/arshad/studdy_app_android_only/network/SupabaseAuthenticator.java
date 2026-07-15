package com.arshad.studdy_app_android_only.network;

import android.util.Log;

import androidx.annotation.Nullable;

import com.arshad.studdy_app_android_only.data.remote.api.AuthApi;
import com.arshad.studdy_app_android_only.data.remote.dto.AuthResponse;
import com.arshad.studdy_app_android_only.data.remote.dto.RefreshTokenRequest;
import com.arshad.studdy_app_android_only.util.SessionManager;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/**
 * Automatically intercepts HTTP 401 response and attempts to refresh the expired JWT
 * session token synchronously. If successful, updates SessionManager.
 */
public class SupabaseAuthenticator implements Authenticator {

    private static final String TAG = "SupabaseAuthenticator";

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, Response response) throws IOException {
        if (response.code() != 401) {
            return null;
        }

        // Limit retries to prevent infinite loops
        if (responseCount(response) >= 3) {
            Log.e(TAG, "Refresh retry limit reached. Aborting retry.");
            return null;
        }

        Log.d(TAG, "HTTP 401 encountered. Attempting token refresh...");

        SessionManager sessionManager = SessionManager.getInstance();
        if (sessionManager == null || !sessionManager.hasTeacherSession()) {
            Log.d(TAG, "No active session manager or session found.");
            return null;
        }

        String refreshToken = sessionManager.getRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            Log.e(TAG, "No refresh token available to perform refresh.");
            return null;
        }

        // Construct synchronous Auth client to make the refresh request
        AuthApi authApi = SupabaseClient.getAuthApiClient().create(AuthApi.class);
        RefreshTokenRequest refreshBody = new RefreshTokenRequest(refreshToken);

        retrofit2.Response<AuthResponse> refreshResponse = authApi.refreshToken(refreshBody).execute();
        if (refreshResponse.isSuccessful() && refreshResponse.body() != null) {
            AuthResponse newAuth = refreshResponse.body();
            Log.d(TAG, "Token refresh succeeded. Saving new session tokens.");

            String name = (newAuth.user != null && newAuth.user.userMetadata != null)
                    ? newAuth.user.userMetadata.name : "";
            String email = (newAuth.user != null) ? newAuth.user.email : "";
            String userId = (newAuth.user != null) ? newAuth.user.id : "";

            sessionManager.saveTeacherSession(
                    newAuth.accessToken,
                    newAuth.refreshToken,
                    userId,
                    email,
                    name
            );

            // Re-submit the failed request with the new access token
            return response.request().newBuilder()
                    .header("Authorization", "Bearer " + newAuth.accessToken)
                    .build();
        } else {
            Log.e(TAG, "Token refresh call failed. Clearing expired session.");
            sessionManager.clearTeacherSession();
            return null;
        }
    }

    private int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) {
            result++;
        }
        return result;
    }
}

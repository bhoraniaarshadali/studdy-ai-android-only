package com.arshad.studdy_app_android_only.data.repository;

import android.content.Context;

import com.arshad.studdy_app_android_only.data.remote.api.AuthApi;
import com.arshad.studdy_app_android_only.data.remote.dto.AuthResponse;
import com.arshad.studdy_app_android_only.data.remote.dto.PasswordResetRequest;
import com.arshad.studdy_app_android_only.data.remote.dto.SignInRequest;
import com.arshad.studdy_app_android_only.data.remote.dto.SignUpRequest;
import com.arshad.studdy_app_android_only.network.SupabaseClient;
import com.arshad.studdy_app_android_only.util.SessionManager;
import com.arshad.studdy_app_android_only.util.ValidationUtils;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.arshad.studdy_app_android_only.data.model.Student;
import com.arshad.studdy_app_android_only.data.repository.StudentRepository;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository for all teacher authentication operations.
 *
 * Exposes callbacks instead of LiveData so callers retain full control
 * over threading and UI updates without requiring a ViewModel reference here.
 */
public class AuthRepository {

    public interface AuthCallback<T> {
        void onSuccess(T result);
        void onFailure(String errorMessage);
    }

    private final AuthApi authApi;
    private final SessionManager sessionManager;
    private final Gson gson = new Gson();

    public AuthRepository(Context context) {
        this.authApi = SupabaseClient.getAuthApiClient().create(AuthApi.class);
        this.sessionManager = SessionManager.getInstance(context);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sign Up
    // ══════════════════════════════════════════════════════════════════════

    public void signUp(String name, String email, String password,
                       AuthCallback<Void> callback) {
        SignUpRequest request = new SignUpRequest(email, password, name);
        authApi.signUp(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful()) {
                    // Per spec: signup does NOT enter the dashboard; just notify success.
                    callback.onSuccess(null);
                } else {
                    callback.onFailure(parseAuthError(response));
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sign In
    // ══════════════════════════════════════════════════════════════════════

    public void signIn(String email, String password,
                       AuthCallback<AuthResponse> callback) {
        SignInRequest request = new SignInRequest(email, password);
        authApi.signIn("password", request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse auth = response.body();
                    // Persist session
                    String name = (auth.user != null && auth.user.userMetadata != null)
                            ? auth.user.userMetadata.name : "";
                    String email2 = (auth.user != null) ? auth.user.email : "";
                    String userId = (auth.user != null) ? auth.user.id : "";
                    sessionManager.saveTeacherSession(
                            auth.accessToken,
                            auth.refreshToken,
                            userId,
                            email2,
                            name
                    );
                    callback.onSuccess(auth);
                } else {
                    callback.onFailure(parseAuthError(response));
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // Student Auth
    // ══════════════════════════════════════════════════════════════════════

    public void signUpStudent(String name, String enrollmentNumber, String password,
                              AuthCallback<Void> callback) {
        String email = enrollmentNumber + "@studdy-student.local";
        SignUpRequest request = new SignUpRequest(email, password, name);
        authApi.signUp(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful()) {
                    // Create student row in `students` table if not already present
                    StudentRepository studentRepo = new StudentRepository();
                    studentRepo.checkOrCreateStudent(enrollmentNumber, name, new StudentRepository.Callback<Student>() {
                        @Override
                        public void onSuccess(Student student) {
                            callback.onSuccess(null);
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            callback.onFailure("Auth user created, but failed to insert student record: " + errorMessage);
                        }
                    });
                } else {
                    callback.onFailure(parseAuthError(response));
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }

    public void signInStudent(String enrollmentNumber, String password,
                              AuthCallback<AuthResponse> callback) {
        String email = enrollmentNumber + "@studdy-student.local";
        SignInRequest request = new SignInRequest(email, password);
        authApi.signIn("password", request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse auth = response.body();
                    String name = (auth.user != null && auth.user.userMetadata != null)
                            ? auth.user.userMetadata.name : "Student";
                    // Save student session
                    sessionManager.saveStudentSession(
                            enrollmentNumber,
                            name,
                            auth.accessToken,
                            auth.refreshToken
                    );
                    callback.onSuccess(auth);
                } else {
                    callback.onFailure(parseAuthError(response));
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // Password Reset
    // ══════════════════════════════════════════════════════════════════════

    public void resetPassword(String email, AuthCallback<Void> callback) {
        authApi.resetPassword("studdyapp://reset-password", new PasswordResetRequest(email))
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        // Supabase returns 200 for valid emails; treat all 2xx as success.
                        if (response.isSuccessful()) {
                            callback.onSuccess(null);
                        } else {
                            callback.onFailure(parseGenericError(response));
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        callback.onFailure("Network error: " + t.getMessage());
                    }
                });
    }

    public void updateUserPassword(String accessToken, String newPassword, AuthCallback<Void> callback) {
        AuthApi authService = SupabaseClient.getAuthApiClient(accessToken).create(AuthApi.class);
        Map<String, Object> body = new HashMap<>();
        body.put("password", newPassword);

        authService.updateUser(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(null);
                } else {
                    callback.onFailure(parseGenericError(response));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // Session management helpers
    // ══════════════════════════════════════════════════════════════════════

    public boolean hasActiveSession() {
        return sessionManager.hasTeacherSession();
    }

    public void signOut() {
        sessionManager.clearTeacherSession();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Error parsing helpers
    // ══════════════════════════════════════════════════════════════════════

    private String parseAuthError(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String errorBody = response.errorBody().string();
                AuthResponse.AuthError authError =
                        gson.fromJson(errorBody, AuthResponse.AuthError.class);
                if (authError != null) {
                    String rawMsg = authError.getBestMessage();
                    ValidationUtils.AuthErrorType type =
                            ValidationUtils.normalizeAuthError(rawMsg);
                    switch (type) {
                        case INVALID_CREDENTIALS:
                            return "INVALID_CREDENTIALS";
                        case ALREADY_REGISTERED:
                            return "ALREADY_REGISTERED";
                        case PASSWORD_TOO_SHORT:
                            return "PASSWORD_TOO_SHORT";
                        default:
                            return "GENERIC";
                    }
                }
            }
        } catch (IOException e) {
            // fall through
        }
        return "GENERIC";
    }

    private String parseGenericError(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                return response.errorBody().string();
            }
        } catch (IOException e) {
            // ignore
        }
        return "HTTP " + response.code();
    }
}

package com.arshad.studdy_app_android_only.ui.auth;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.repository.AuthRepository;
import com.arshad.studdy_app_android_only.databinding.ActivityResetPasswordBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ResetPasswordActivity extends AppCompatActivity {

    private static final String TAG = "ResetPasswordActivity";
    private ActivityResetPasswordBinding binding;
    private AuthRepository authRepository;
    private String accessToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityResetPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authRepository = new AuthRepository(this);

        // Parse deep link parameters
        handleIntent(getIntent());

        if (accessToken == null || accessToken.isEmpty()) {
            Toast.makeText(this, "Invalid or missing reset token.", Toast.LENGTH_LONG).show();
            navigateToAuth();
            return;
        }

        binding.btnBack.setOnClickListener(v -> navigateToAuth());
        binding.btnSubmit.setOnClickListener(v -> attemptResetPassword());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        Uri data = intent.getData();
        if (data != null) {
            Log.d(TAG, "Deep link received: " + data.toString());
            // Fragment contains access_token and other parameters (e.g. #access_token=xxx&...)
            String fragment = data.getFragment();
            if (fragment != null) {
                String[] params = fragment.split("&");
                String errorCode = null;
                String errorDescription = null;
                for (String param : params) {
                    String[] kv = param.split("=");
                    if (kv.length == 2) {
                        if ("access_token".equals(kv[0])) {
                            accessToken = kv[1];
                            Log.d(TAG, "Access token successfully parsed from fragment");
                        } else if ("error_code".equals(kv[0])) {
                            errorCode = kv[1];
                        } else if ("error_description".equals(kv[0])) {
                            errorDescription = Uri.decode(kv[1]);
                        }
                    }
                }
                if (errorCode != null) {
                    String msg = "Reset link invalid: " + (errorDescription != null ? errorDescription : errorCode);
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    navigateToAuth();
                }
            }
        }
    }

    private void attemptResetPassword() {
        binding.tilPassword.setError(null);
        binding.tilConfirmPassword.setError(null);

        String password = binding.etPassword.getText() != null
                ? binding.etPassword.getText().toString().trim() : "";
        String confirmPassword = binding.etConfirmPassword.getText() != null
                ? binding.etConfirmPassword.getText().toString().trim() : "";

        if (password.isEmpty()) {
            binding.tilPassword.setError("Password is required");
            return;
        }

        if (password.length() < 6) {
            binding.tilPassword.setError("Password must be at least 6 characters");
            return;
        }

        if (!password.equals(confirmPassword)) {
            binding.tilConfirmPassword.setError("Passwords do not match");
            return;
        }

        setLoading(true);

        authRepository.updateUserPassword(accessToken, password, new AuthRepository.AuthCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                setLoading(false);
                new MaterialAlertDialogBuilder(ResetPasswordActivity.this, R.style.Studdy_Dialog)
                        .setTitle("Success")
                        .setMessage("Your password has been successfully updated. You can now sign in using your new password.")
                        .setPositiveButton("Sign In", (d, w) -> navigateToAuth())
                        .setCancelable(false)
                        .show();
            }

            @Override
            public void onFailure(String error) {
                setLoading(false);
                Log.e(TAG, "Password update failed: " + error);
                Toast.makeText(ResetPasswordActivity.this, "Failed to update password: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean isLoading) {
        binding.btnSubmit.setVisibility(isLoading ? View.GONE : View.VISIBLE);
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.etPassword.setEnabled(!isLoading);
        binding.etConfirmPassword.setEnabled(!isLoading);
    }

    private void navigateToAuth() {
        boolean isStudent = false;
        if (accessToken != null) {
            isStudent = isStudentToken(accessToken);
        } else {
            String studentEnrollment = com.arshad.studdy_app_android_only.util.SessionManager.getInstance(this).getStudentEnrollment();
            isStudent = (studentEnrollment != null && !studentEnrollment.isEmpty());
        }

        Intent intent;
        if (isStudent) {
            intent = new Intent(ResetPasswordActivity.this, StudentAuthActivity.class);
        } else {
            intent = new Intent(ResetPasswordActivity.this, TeacherAuthActivity.class);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private boolean isStudentToken(String token) {
        if (token == null) return false;
        try {
            String[] parts = token.split("\\.");
            if (parts.length > 1) {
                byte[] decodedBytes = android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE);
                String payload = new String(decodedBytes, "UTF-8");
                return payload.contains("@studdy-student.local");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing JWT", e);
        }
        return false;
    }
}

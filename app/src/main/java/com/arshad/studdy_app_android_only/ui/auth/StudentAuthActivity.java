package com.arshad.studdy_app_android_only.ui.auth;

import android.content.Intent;
import com.arshad.studdy_app_android_only.util.ErrorMessageMapper;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.remote.dto.AuthResponse;
import com.arshad.studdy_app_android_only.data.repository.AuthRepository;
import com.arshad.studdy_app_android_only.databinding.ActivityStudentAuthBinding;
import com.arshad.studdy_app_android_only.ui.BaseActivity;
import com.arshad.studdy_app_android_only.ui.student.dashboard.StudentDashboardActivity;
import com.arshad.studdy_app_android_only.util.ValidationUtils;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class StudentAuthActivity extends BaseActivity {

    private ActivityStudentAuthBinding binding;
    private AuthRepository authRepository;
    private boolean isSignUpMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudentAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authRepository = new AuthRepository(this);

        setupTabLayout();
        setupClickListeners();
        clearErrors();
    }

    @Override
    protected void onResume() {
        super.onResume();
        clearFields();
    }

    private void setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                isSignUpMode = (tab.getPosition() == 1);
                updateUiForMode();
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    private void updateUiForMode() {
        if (isSignUpMode) {
            binding.tilName.setVisibility(View.VISIBLE);
            binding.btnPrimary.setText(R.string.create_account);
            binding.btnForgotPassword.setVisibility(View.GONE);
        } else {
            binding.tilName.setVisibility(View.GONE);
            binding.btnPrimary.setText(R.string.sign_in);
            binding.btnForgotPassword.setVisibility(View.GONE);
        }
        clearFields();
    }

    private void setupClickListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnPrimary.setOnClickListener(v -> {
            hideKeyboard();
            if (isSignUpMode) {
                attemptSignUp();
            } else {
                attemptSignIn();
            }
        });

        binding.btnForgotPassword.setOnClickListener(v -> showResetPasswordDialog());

        // Clear errors on text change
        addClearErrorWatcher(binding.etEnrollment, binding.tilEnrollment);
        addClearErrorWatcher(binding.etPassword, binding.tilPassword);
        addClearErrorWatcher(binding.etName, binding.tilName);
    }

    private void addClearErrorWatcher(TextInputEditText et, TextInputLayout til) {
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int i1, int i2) { }
            @Override public void onTextChanged(CharSequence s, int i, int i1, int i2) {
                til.setError(null);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void attemptSignIn() {
        String enrollment = getTextTrimmed(binding.etEnrollment);
        String password = getTextTrimmed(binding.etPassword);

        boolean valid = true;
        if (!ValidationUtils.isEnrollmentValid(enrollment)) {
            binding.tilEnrollment.setError("Enrollment number must be at least 4 characters");
            valid = false;
        }
        if (!ValidationUtils.isPasswordValid(password)) {
            binding.tilPassword.setError(getString(R.string.msg_password_too_short));
            valid = false;
        }
        if (!valid) return;

        setLoading(true);
        authRepository.signInStudent(enrollment, password, new AuthRepository.AuthCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse result) {
                setLoading(false);
                Intent intent = new Intent(StudentAuthActivity.this, StudentDashboardActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }

            @Override
            public void onFailure(String errorCode) {
                setLoading(false);
                showAuthError(errorCode);
            }
        });
    }

    private void attemptSignUp() {
        String name = getTextTrimmed(binding.etName);
        String enrollment = getTextTrimmed(binding.etEnrollment);
        String password = getTextTrimmed(binding.etPassword);

        boolean valid = true;
        if (!ValidationUtils.isNameValid(name)) {
            binding.tilName.setError(getString(R.string.msg_name_required));
            valid = false;
        }
        if (!ValidationUtils.isEnrollmentValid(enrollment)) {
            binding.tilEnrollment.setError("Enrollment number must be at least 4 characters");
            valid = false;
        }
        if (!ValidationUtils.isPasswordValid(password)) {
            binding.tilPassword.setError(getString(R.string.msg_password_too_short));
            valid = false;
        }
        if (!valid) return;

        setLoading(true);
        authRepository.signUpStudent(name, enrollment, password, new AuthRepository.AuthCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                setLoading(false);
                if (binding.tabLayout.getTabAt(0) != null) {
                    binding.tabLayout.getTabAt(0).select();
                }
                binding.etPassword.setText("");
                Toast.makeText(
                        StudentAuthActivity.this,
                        R.string.msg_account_created,
                        Toast.LENGTH_LONG
                ).show();
            }

            @Override
            public void onFailure(String errorCode) {
                setLoading(false);
                showAuthError(errorCode);
            }
        });
    }

    private void showAuthError(String error) {
        String displayMsg;
        switch (error) {
            case "INVALID_CREDENTIALS":
                displayMsg = "Incorrect email/enrollment number or password. Please try again.";
                break;
            case "ALREADY_REGISTERED":
                displayMsg = "A student with this enrollment number is already registered.";
                break;
            case "PASSWORD_TOO_SHORT":
                displayMsg = getString(R.string.msg_password_too_short);
                break;
            default:
                displayMsg = ErrorMessageMapper.toUserMessage("StudentAuthActivity", "Authentication failed", error);
                break;
        }
        Toast.makeText(this, displayMsg, Toast.LENGTH_LONG).show();
    }

    private void setLoading(boolean isLoading) {
        binding.btnPrimary.setVisibility(isLoading ? View.GONE : View.VISIBLE);
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.etEnrollment.setEnabled(!isLoading);
        binding.etPassword.setEnabled(!isLoading);
        binding.etName.setEnabled(!isLoading);
        binding.tabLayout.setEnabled(!isLoading);
    }

    private void clearFields() {
        binding.etEnrollment.setText("");
        binding.etPassword.setText("");
        binding.etName.setText("");
        clearErrors();
    }

    private void clearErrors() {
        binding.tilEnrollment.setError(null);
        binding.tilPassword.setError(null);
        binding.tilName.setError(null);
    }

    private String getTextTrimmed(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void showResetPasswordDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_reset_password, null);
        TextInputLayout tilEnrollment = dialogView.findViewById(R.id.til_reset_email);
        TextInputEditText etEnrollment = dialogView.findViewById(R.id.et_reset_email);

        tilEnrollment.setHint("Enrollment Number");
        etEnrollment.setInputType(android.text.InputType.TYPE_CLASS_TEXT);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.Studdy_Dialog)
                .setTitle("Reset Password")
                .setMessage("Enter your enrollment number and we'll send you a link to reset your password.")
                .setView(dialogView)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_send_reset, null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String enrollment = etEnrollment.getText() != null
                        ? etEnrollment.getText().toString().trim() : "";
                if (enrollment.isEmpty()) {
                    tilEnrollment.setError("Enrollment number is required");
                    return;
                }
                if (!ValidationUtils.isEnrollmentValid(enrollment)) {
                    tilEnrollment.setError("Enrollment number must be at least 4 characters");
                    return;
                }
                dialog.dismiss();

                setLoading(true);
                String syntheticEmail = enrollment + "@studdy-student.local";
                authRepository.resetPassword(syntheticEmail, new AuthRepository.AuthCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        setLoading(false);
                        Toast.makeText(
                                StudentAuthActivity.this,
                                "Password reset link sent to your registered student email.",
                                Toast.LENGTH_LONG
                        ).show();
                    }

                    @Override
                    public void onFailure(String error) {
                        setLoading(false);
                        String friendlyMsg = ErrorMessageMapper.toUserMessage("StudentAuthActivity", "Failed to send reset link", error);
                        Toast.makeText(
                                StudentAuthActivity.this,
                                friendlyMsg,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
            });
        });

        dialog.show();
    }
}

package com.arshad.studdy_app_android_only.ui.auth;

import androidx.appcompat.app.AlertDialog;
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
import com.arshad.studdy_app_android_only.databinding.ActivityTeacherAuthBinding;
import com.arshad.studdy_app_android_only.ui.BaseActivity;
import com.arshad.studdy_app_android_only.ui.teacher.dashboard.TeacherDashboardActivity;
import com.arshad.studdy_app_android_only.util.ValidationUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Teacher sign-in / sign-up screen.
 *
 * Spec behaviour implemented:
 *  - Back button returns to RoleSelection.
 *  - Sign In / Sign Up tabs toggle field visibility (Full Name hidden for sign-in).
 *  - Email validation: must contain '@'.
 *  - Password validation: min 6 characters.
 *  - Name required only for sign-up.
 *  - Successful signup → switch to sign-in tab, clear password, show toast.
 *  - Successful sign-in → clear back stack → TeacherDashboardActivity.
 *  - Auth errors mapped to spec-defined messages.
 *  - Forgot Password dialog with email field, Cancel and Send Reset Link.
 */
public class TeacherAuthActivity extends BaseActivity {

    private ActivityTeacherAuthBinding binding;
    private AuthRepository authRepository;
    private boolean isSignUpMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTeacherAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authRepository = new AuthRepository(this);

        setupTabLayout();
        setupClickListeners();
        clearErrors();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Defensively clear fields whenever the screen becomes active to prevent
        // autofill/back-stack stale text from concatenating with new input.
        clearFields();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Setup
    // ══════════════════════════════════════════════════════════════════════

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
            binding.btnForgotPassword.setVisibility(View.GONE);
            binding.btnPrimary.setText(R.string.create_account);
        } else {
            binding.tilName.setVisibility(View.GONE);
            binding.btnForgotPassword.setVisibility(View.VISIBLE);
            binding.btnPrimary.setText(R.string.sign_in);
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
        addClearErrorWatcher(binding.etEmail, binding.tilEmail);
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

    // ══════════════════════════════════════════════════════════════════════
    // Sign In
    // ══════════════════════════════════════════════════════════════════════

    private void attemptSignIn() {
        String email = getTextTrimmed(binding.etEmail);
        String password = getTextTrimmed(binding.etPassword);

        boolean valid = true;
        if (!ValidationUtils.isEmailValid(email)) {
            binding.tilEmail.setError(getString(R.string.msg_email_invalid));
            valid = false;
        }
        if (!ValidationUtils.isPasswordValid(password)) {
            binding.tilPassword.setError(getString(R.string.msg_password_too_short));
            valid = false;
        }
        if (!valid) return;

        setLoading(true);
        authRepository.signIn(email, password, new AuthRepository.AuthCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse result) {
                setLoading(false);
                // Spec: "clears all navigation history and opens Teacher Dashboard"
                Intent intent = new Intent(TeacherAuthActivity.this, TeacherDashboardActivity.class);
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

    // ══════════════════════════════════════════════════════════════════════
    // Sign Up
    // ══════════════════════════════════════════════════════════════════════

    private void attemptSignUp() {
        String name = getTextTrimmed(binding.etName);
        String email = getTextTrimmed(binding.etEmail);
        String password = getTextTrimmed(binding.etPassword);

        boolean valid = true;
        if (!ValidationUtils.isNameValid(name)) {
            binding.tilName.setError(getString(R.string.msg_name_required));
            valid = false;
        }
        if (!ValidationUtils.isEmailValid(email)) {
            binding.tilEmail.setError(getString(R.string.msg_email_invalid));
            valid = false;
        }
        if (!ValidationUtils.isPasswordValid(password)) {
            binding.tilPassword.setError(getString(R.string.msg_password_too_short));
            valid = false;
        }
        if (!valid) return;

        setLoading(true);
        authRepository.signUp(name, email, password, new AuthRepository.AuthCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                setLoading(false);
                // Spec: "switches back to sign-in, clears password, and says 'Account created...'"
                if (binding.tabLayout.getTabAt(0) != null) {
                    binding.tabLayout.getTabAt(0).select();
                }
                binding.etPassword.setText("");
                Toast.makeText(
                        TeacherAuthActivity.this,
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

    // ══════════════════════════════════════════════════════════════════════
    // Forgot Password dialog
    // ══════════════════════════════════════════════════════════════════════

    private void showResetPasswordDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_reset_password, null);
        TextInputLayout tilEmail = dialogView.findViewById(R.id.til_reset_email);
        TextInputEditText etEmail = dialogView.findViewById(R.id.et_reset_email);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.Studdy_Dialog)
                .setTitle(R.string.reset_password)
                .setMessage(R.string.reset_password_body)
                .setView(dialogView)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_send_reset, null) // set manually to prevent dismiss
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String email = etEmail.getText() != null
                        ? etEmail.getText().toString().trim() : "";
                if (email.isEmpty()) {
                    tilEmail.setError(getString(R.string.msg_email_required));
                    return;
                }
                dialog.dismiss();
                authRepository.resetPassword(email, new AuthRepository.AuthCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        Toast.makeText(
                                TeacherAuthActivity.this,
                                R.string.msg_reset_sent,
                                Toast.LENGTH_LONG
                        ).show();
                    }

                    @Override
                    public void onFailure(String error) {
                        String friendly = ErrorMessageMapper.toUserMessage("TeacherAuthActivity", "Failed to send reset link", error);
                        Toast.makeText(
                                TeacherAuthActivity.this,
                                friendly,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
            });
        });

        dialog.show();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════

    private void showAuthError(String errorCode) {
        int messageRes;
        switch (errorCode) {
            case "INVALID_CREDENTIALS":
                String credentialMsg = "Incorrect email/enrollment number or password. Please try again.";
                Toast.makeText(this, credentialMsg, Toast.LENGTH_LONG).show();
                return;
            case "ALREADY_REGISTERED":
                messageRes = R.string.msg_already_registered;
                break;
            case "PASSWORD_TOO_SHORT":
                messageRes = R.string.msg_password_too_short;
                break;
            default:
                String friendlyMsg = ErrorMessageMapper.toUserMessage("TeacherAuthActivity", "Authentication failed", errorCode);
                Toast.makeText(this, friendlyMsg, Toast.LENGTH_LONG).show();
                return;
        }
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show();
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnPrimary.setEnabled(!loading);
        binding.tabLayout.setEnabled(!loading);
    }

    private void clearErrors() {
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        binding.tilName.setError(null);
    }

    /** Reset all input fields to empty — prevents autofill/back-stack text concatenation. */
    private void clearFields() {
        binding.etEmail.setText("");
        binding.etPassword.setText("");
        binding.etName.setText("");
        clearErrors();
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
}

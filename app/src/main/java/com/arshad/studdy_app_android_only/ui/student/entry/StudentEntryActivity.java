package com.arshad.studdy_app_android_only.ui.student.entry;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.Exam;
import com.arshad.studdy_app_android_only.data.model.ExamSession;
import com.arshad.studdy_app_android_only.data.model.Student;
import com.arshad.studdy_app_android_only.data.repository.StudentRepository;
import com.arshad.studdy_app_android_only.databinding.ActivityStudentEntryBinding;
import com.arshad.studdy_app_android_only.ui.BaseActivity;
import com.arshad.studdy_app_android_only.ui.qr.QrScanActivity;
import com.arshad.studdy_app_android_only.ui.student.exam.ExamActivity;
import com.arshad.studdy_app_android_only.ui.student.exam.PreExamInstructionsActivity;
import com.arshad.studdy_app_android_only.util.DateTimeUtils;
import com.arshad.studdy_app_android_only.util.SessionManager;
import com.arshad.studdy_app_android_only.util.ValidationUtils;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Student join flow.
 * Handles the 2-step verification and enrollment sequence.
 */
public class StudentEntryActivity extends BaseActivity {

    private ActivityStudentEntryBinding binding;
    private StudentRepository studentRepository;
    private Exam verifiedExam;

    // Activity launcher for QR Code scanning
    private final ActivityResultLauncher<Intent> qrScanLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String scannedCode = result.getData().getStringExtra("scanned_code");
                    if (scannedCode != null && !scannedCode.isEmpty()) {
                        binding.etExamCode.setText(scannedCode);
                        verifyExamCode(scannedCode);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudentEntryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        studentRepository = new StudentRepository();

        setupClickListeners();
        setupTextWatchers();

        // If student is logged in, pre-populate and disable editing
        SessionManager session = SessionManager.getInstance(this);
        if (session.hasStudentSession()) {
            binding.etStudentName.setText(session.getStudentName());
            binding.etStudentEnrollment.setText(session.getStudentEnrollment());
            binding.etStudentName.setEnabled(false);
            binding.etStudentEnrollment.setEnabled(false);
        }
    }

    private void setupClickListeners() {
        binding.btnBack.setOnClickListener(v -> handleBackAction());

        binding.btnVerifyCode.setOnClickListener(v -> {
            String code = getExamCode();
            if (!ValidationUtils.isExamCodeValid(code)) {
                binding.tilExamCode.setError(getString(R.string.err_code_invalid));
                return;
            }
            verifyExamCode(code);
        });

        binding.btnScanQr.setOnClickListener(v -> {
            Intent intent = new Intent(this, QrScanActivity.class);
            qrScanLauncher.launch(intent);
        });

        binding.btnContinueJoin.setOnClickListener(v -> {
            attemptJoinExam();
        });
    }

    private void setupTextWatchers() {
        binding.etExamCode.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.tilExamCode.setError(null);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.etStudentName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.tilStudentName.setError(null);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.etStudentEnrollment.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.tilStudentEnrollment.setError(null);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void verifyExamCode(String code) {
        setLoading(true);
        hideKeyboard();
        studentRepository.getExamByCode(code, new StudentRepository.Callback<Exam>() {
            @Override
            public void onSuccess(Exam exam) {
                setLoading(false);
                
                // Active window validation if timer mode is window
                if (exam.isTimerWindow()) {
                    if (DateTimeUtils.isBeforeWindow(exam.windowStart)) {
                        showToast(getString(R.string.err_exam_not_started));
                        return;
                    }
                    if (DateTimeUtils.isAfterWindow(exam.windowEnd)) {
                        showToast(getString(R.string.err_exam_expired));
                        return;
                    }
                }

                // Advance to Step 2
                verifiedExam = exam;
                showStep2();
            }

            @Override
            public void onFailure(String errorMessage) {
                setLoading(false);
                if ("EXAM_NOT_FOUND".equals(errorMessage)) {
                    binding.tilExamCode.setError(getString(R.string.err_code_not_found));
                } else {
                    showToast(getString(R.string.msg_network_error) + ": " + errorMessage);
                }
            }
        });
    }

    private void showStep2() {
        binding.layoutStep1.setVisibility(View.GONE);
        binding.layoutStep2.setVisibility(View.VISIBLE);
        
        binding.tvSubtitle.setText(R.string.enter_enrollment);
        binding.tvVerifiedExamTitle.setText(verifiedExam.title);
        binding.tvVerifiedExamCode.setText(getString(R.string.label_exam_code_display) + verifiedExam.code);
    }

    private void attemptJoinExam() {
        String name = binding.etStudentName.getText() != null ? binding.etStudentName.getText().toString().trim() : "";
        String enrollment = binding.etStudentEnrollment.getText() != null ? binding.etStudentEnrollment.getText().toString().trim() : "";

        boolean valid = true;
        if (!ValidationUtils.isNameValid(name)) {
            binding.tilStudentName.setError(getString(R.string.err_name_required));
            valid = false;
        }
        if (!ValidationUtils.isEnrollmentValid(enrollment)) {
            binding.tilStudentEnrollment.setError(getString(R.string.err_enrollment_too_short));
            valid = false;
        }

        if (!valid) return;

        setLoading(true);
        hideKeyboard();

        // 1. Check or Create Student record
        studentRepository.checkOrCreateStudent(enrollment, name, new StudentRepository.Callback<Student>() {
            @Override
            public void onSuccess(Student student) {
                // 2. Check if student has already submitted results for this exam
                studentRepository.checkExistingResult(verifiedExam.code, enrollment, new StudentRepository.Callback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean hasResult) {
                        if (hasResult) {
                            setLoading(false);
                            showToast(getString(R.string.err_already_attempted));
                        } else {
                            // 3. Create active exam session
                            createActiveExamSession(student);
                        }
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        setLoading(false);
                        showToast(getString(R.string.msg_network_error) + ": " + errorMessage);
                    }
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                setLoading(false);
                showToast(getString(R.string.msg_network_error) + ": " + errorMessage);
            }
        });
    }

    private void createActiveExamSession(Student student) {
        ExamSession session = new ExamSession();
        session.examCode = verifiedExam.code;
        session.enrollmentNumber = student.enrollmentNumber;
        session.studentName = student.name;
        session.joinedAt = DateTimeUtils.formatIso8601Now();
        session.status = "active";
        session.warnings = 0;
        session.warningsLog = new ArrayList<>();
        session.currentAnswers = new ArrayList<>(Collections.nCopies(verifiedExam.getQuestionCount(), -1));

        studentRepository.createExamSession(session, new StudentRepository.Callback<ExamSession>() {
            @Override
            public void onSuccess(ExamSession createdSession) {
                setLoading(false);
                
                // Save enrollment locally
                SessionManager.getInstance(StudentEntryActivity.this).saveStudentEnrollment(student.enrollmentNumber);

                // Start PreExamInstructionsActivity
                Intent intent = new Intent(StudentEntryActivity.this, PreExamInstructionsActivity.class);
                intent.putExtra("exam_code", verifiedExam.code);
                intent.putExtra("student_name", student.name);
                intent.putExtra("enrollment_number", student.enrollmentNumber);
                intent.putExtra("session_id", createdSession.id);
                startActivity(intent);
                finish();
            }

            @Override
            public void onFailure(String errorMessage) {
                setLoading(false);
                showToast("Failed to create exam session: " + errorMessage);
            }
        });
    }

    private void handleBackAction() {
        if (binding.layoutStep2.getVisibility() == View.VISIBLE) {
            // Revert back to Step 1
            binding.layoutStep2.setVisibility(View.GONE);
            binding.layoutStep1.setVisibility(View.VISIBLE);
            binding.tvSubtitle.setText(R.string.enter_exam_code);
            verifiedExam = null;
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        handleBackAction();
    }

    private void setLoading(boolean loading) {
        binding.progressLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnVerifyCode.setEnabled(!loading);
        binding.btnScanQr.setEnabled(!loading);
        binding.btnContinueJoin.setEnabled(!loading);
    }

    private String getExamCode() {
        return binding.etExamCode.getText() != null ? binding.etExamCode.getText().toString().trim().toUpperCase() : "";
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}

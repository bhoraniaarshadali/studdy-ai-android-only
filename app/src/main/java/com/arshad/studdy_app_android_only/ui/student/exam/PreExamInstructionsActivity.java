package com.arshad.studdy_app_android_only.ui.student.exam;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.Exam;
import com.arshad.studdy_app_android_only.data.repository.StudentRepository;
import com.arshad.studdy_app_android_only.databinding.ActivityPreExamInstructionsBinding;
import com.arshad.studdy_app_android_only.ui.BaseActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class PreExamInstructionsActivity extends BaseActivity {

    private ActivityPreExamInstructionsBinding binding;
    private StudentRepository studentRepository;

    private String examCode;
    private String studentName;
    private String enrollmentNumber;
    private long sessionId;

    private Exam exam;
    private boolean isStarting = false;

    private final ActivityResultLauncher<String> requestCameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startExamActivity();
                } else {
                    Toast.makeText(this, "Camera permission is required for proctored exams.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPreExamInstructionsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        studentRepository = new StudentRepository();

        // Retrieve inputs
        examCode = getIntent().getStringExtra("exam_code");
        studentName = getIntent().getStringExtra("student_name");
        enrollmentNumber = getIntent().getStringExtra("enrollment_number");
        sessionId = getIntent().getLongExtra("session_id", -1);

        if (examCode == null || sessionId == -1) {
            Toast.makeText(this, "Invalid Exam Session parameters.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbar();
        loadExamDetails();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isStarting = false;
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadExamDetails() {
        binding.progressLoading.setVisibility(View.VISIBLE);
        binding.layoutContent.setVisibility(View.GONE);

        studentRepository.getExamByCode(examCode, new StudentRepository.Callback<Exam>() {
            @Override
            public void onSuccess(Exam fetchedExam) {
                exam = fetchedExam;
                binding.progressLoading.setVisibility(View.GONE);
                binding.layoutContent.setVisibility(View.VISIBLE);

                binding.tvExamTitle.setText(exam.title);
                binding.tvExamCode.setText("Code: " + exam.code);

                int qCount = exam.getQuestionCount();
                binding.tvQuestionCount.setText(String.valueOf(qCount));
                binding.tvTotalMarks.setText(String.valueOf(qCount));

                if ("duration".equals(exam.timerMode) && exam.durationMinutes != null) {
                    binding.tvDuration.setText(exam.durationMinutes + " min");
                } else {
                    binding.tvDuration.setText("None");
                }

                if (exam.proctoringEnabled) {
                    binding.tvInstructionCamera.setVisibility(View.VISIBLE);
                } else {
                    binding.tvInstructionCamera.setVisibility(View.GONE);
                }

                binding.btnStartExam.setOnClickListener(v -> onStartExamClicked());
            }

            @Override
            public void onFailure(String errorMessage) {
                binding.progressLoading.setVisibility(View.GONE);
                Toast.makeText(PreExamInstructionsActivity.this, "Failed to load exam details: " + errorMessage, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void onStartExamClicked() {
        if (isStarting) return;
        isStarting = true;

        if (exam.proctoringEnabled) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                isStarting = false;
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            } else {
                startExamActivity();
            }
        } else {
            startExamActivity();
        }
    }

    private void startExamActivity() {
        Intent intent = new Intent(this, ExamActivity.class);
        intent.putExtra("exam_code", examCode);
        intent.putExtra("student_name", studentName);
        intent.putExtra("enrollment_number", enrollmentNumber);
        intent.putExtra("session_id", sessionId);
        startActivity(intent);
        finish();
    }
}

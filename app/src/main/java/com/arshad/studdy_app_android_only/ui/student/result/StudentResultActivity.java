package com.arshad.studdy_app_android_only.ui.student.result;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.Exam;
import com.arshad.studdy_app_android_only.data.model.Result;
import com.arshad.studdy_app_android_only.data.repository.StudentRepository;
import com.arshad.studdy_app_android_only.databinding.ActivityStudentResultBinding;
import com.arshad.studdy_app_android_only.ui.BaseActivity;
import com.arshad.studdy_app_android_only.ui.student.dashboard.StudentDashboardActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.view.WindowManager;

/**
 * Score display and answer review screen for students.
 * Respects exams.result_mode and exams.results_published flag.
 */
public class StudentResultActivity extends BaseActivity {

    private ActivityStudentResultBinding binding;
    private StudentRepository studentRepository;
    
    private String examCode;
    private String studentName;
    private String enrollmentNumber;

    private Exam exam;
    private Result result;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(this, null);
        }

        super.onCreate(savedInstanceState);
        binding = ActivityStudentResultBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        studentRepository = new StudentRepository();

        examCode = getIntent().getStringExtra("exam_code");
        studentName = getIntent().getStringExtra("student_name");
        enrollmentNumber = getIntent().getStringExtra("enrollment_number");

        if (examCode == null || enrollmentNumber == null) {
            Toast.makeText(this, "Missing result parameters.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        binding.tvStudentName.setText(studentName);
        binding.tvStudentEnrollment.setText(enrollmentNumber);

        binding.toolbar.setNavigationOnClickListener(v -> navigateToDashboard());

        binding.btnBackDashboard.setOnClickListener(v -> navigateToDashboard());

        binding.btnViewAnswers.setOnClickListener(v -> {
            if (exam != null && result != null) {
                showAnswersReviewDialog();
            }
        });

        loadResultData();
    }

    private void loadResultData() {
        setLoading(true);
        // 1. Fetch exam metadata
        studentRepository.getExamByCode(examCode, new StudentRepository.Callback<Exam>() {
            @Override
            public void onSuccess(Exam fetchedExam) {
                exam = fetchedExam;
                binding.tvExamTitle.setText(exam.title);

                // 2. Fetch student's submitted result
                fetchStudentResult();
            }

            @Override
            public void onFailure(String errorMessage) {
                setLoading(false);
                Toast.makeText(StudentResultActivity.this, "Failed to load exam details: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void fetchStudentResult() {
        studentRepository.getResult(examCode, enrollmentNumber, new StudentRepository.Callback<Result>() {
            @Override
            public void onSuccess(Result fetchedResult) {
                setLoading(false);
                result = fetchedResult;
                binding.tvStudentWarnings.setText(result.warnings + " warnings, " + result.appSwitches + " app switches");

                // Evaluate result visibility configurations
                evaluateResultVisibility();
            }

            @Override
            public void onFailure(String errorMessage) {
                setLoading(false);
                Toast.makeText(StudentResultActivity.this, "Failed to retrieve results: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void evaluateResultVisibility() {
        // Spec: Respect result_mode ("instant" vs "manual") and results_published flag
        boolean isScoreVisible = exam.isInstantResult() || exam.resultsPublished;

        if (isScoreVisible) {
            binding.layoutScoreCircle.setVisibility(View.VISIBLE);
            binding.btnViewAnswers.setVisibility(View.GONE);
            binding.cardPendingResults.setVisibility(View.GONE);

            binding.tvScoreValue.setText(String.valueOf(result.score));
            binding.tvScoreSlash.setText("out of " + result.total);

            // Bind inline questions breakdown
            binding.layoutAnswersBreakdown.setVisibility(View.VISIBLE);
            binding.rvQuestionsBreakdown.setLayoutManager(new LinearLayoutManager(this));
            ReviewAnswersAdapter reviewAdapter = new ReviewAnswersAdapter(exam.questions, result.answers);
            binding.rvQuestionsBreakdown.setAdapter(reviewAdapter);
        } else {
            binding.layoutScoreCircle.setVisibility(View.GONE);
            binding.btnViewAnswers.setVisibility(View.GONE);
            binding.cardPendingResults.setVisibility(View.VISIBLE);
            binding.layoutAnswersBreakdown.setVisibility(View.GONE);
        }
    }

    private void showAnswersReviewDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_review_answers, null);
        RecyclerView rvReview = dialogView.findViewById(R.id.rv_review_questions);
        
        rvReview.setLayoutManager(new LinearLayoutManager(this));
        ReviewAnswersAdapter reviewAdapter = new ReviewAnswersAdapter(exam.questions, result.answers);
        rvReview.setAdapter(reviewAdapter);

        new MaterialAlertDialogBuilder(this, R.style.Studdy_Dialog)
                .setTitle("Review Answers")
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .show();
    }

    private void navigateToDashboard() {
        Intent intent = new Intent(this, StudentDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        navigateToDashboard();
    }

    private void setLoading(boolean loading) {
        binding.progressLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnViewAnswers.setEnabled(!loading);
        binding.btnBackDashboard.setEnabled(!loading);
    }
}

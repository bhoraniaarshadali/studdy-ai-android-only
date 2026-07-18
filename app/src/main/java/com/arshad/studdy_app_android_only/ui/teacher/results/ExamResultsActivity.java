package com.arshad.studdy_app_android_only.ui.teacher.results;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.Exam;
import com.arshad.studdy_app_android_only.data.model.Result;
import com.arshad.studdy_app_android_only.data.remote.api.ExamApi;
import com.arshad.studdy_app_android_only.data.remote.api.ResultApi;
import com.arshad.studdy_app_android_only.databinding.ActivityExamResultsBinding;
import com.arshad.studdy_app_android_only.network.SupabaseClient;
import com.arshad.studdy_app_android_only.ui.BaseActivity;
import com.arshad.studdy_app_android_only.ui.teacher.monitor.ExamMonitorActivity;
import com.arshad.studdy_app_android_only.util.SessionManager;
import com.arshad.studdy_app_android_only.ui.student.result.ReviewAnswersAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Teacher results overview activity.
 * Lists completed student attempts, computes average metrics, allows toggling results publishing,
 * opening live monitoring, and deleting the exam record.
 */
public class ExamResultsActivity extends BaseActivity {

    private static final String TAG = "ExamResultsActivity";

    private ActivityExamResultsBinding binding;
    private StudentResultsAdapter adapter;
    private Exam exam;
    
    private String examCode;
    private String examTitle;
    private String examId;

    private ExamApi examApi;
    private ResultApi resultApi;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityExamResultsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = SessionManager.getInstance(this);

        examCode = getIntent().getStringExtra("exam_code");
        examTitle = getIntent().getStringExtra("exam_title");
        examId = getIntent().getStringExtra("exam_id");

        if (examCode == null || examId == null) {
            Toast.makeText(this, "Missing exam identifiers.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbar();
        setupRecyclerView();
        setupClickListeners();
        setupSearchWatcher();

        // Setup authenticated APIs
        String token = sessionManager.getAccessToken();
        examApi = SupabaseClient.createAuthService(ExamApi.class, token);
        resultApi = SupabaseClient.createAuthService(ResultApi.class, token);

        loadResults(true);
        verifyPublishStatus();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.setTitle(examTitle != null ? examTitle : "Results");
        binding.toolbar.setSubtitle("Code: " + examCode);
        binding.toolbar.inflateMenu(R.menu.menu_exam_results);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_show_answer_key) {
                Intent intent = new Intent(this, com.arshad.studdy_app_android_only.ui.teacher.results.ExamAnswerKeyActivity.class);
                intent.putExtra("exam_code", examCode);
                intent.putExtra("exam_title", examTitle);
                startActivity(intent);
                return true;
            }
            if (item.getItemId() == R.id.action_show_qr) {
                Intent intent = new Intent(this, com.arshad.studdy_app_android_only.ui.qr.QrDisplayActivity.class);
                intent.putExtra("exam_code", examCode);
                intent.putExtra("exam_title", examTitle);
                startActivity(intent);
                return true;
            }
            return false;
        });
    }

    private void setupRecyclerView() {
        adapter = new StudentResultsAdapter();
        binding.rvResults.setLayoutManager(new LinearLayoutManager(this));
        binding.rvResults.setAdapter(adapter);
        adapter.setOnResultClickListener(res -> {
            if (exam != null) {
                showStudentResultBreakdownDialog(res);
            } else {
                Toast.makeText(this, "Exam details are loading. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupClickListeners() {
        binding.btnPublishResults.setOnClickListener(v -> publishResults());

        binding.btnMonitorLive.setOnClickListener(v -> {
            Intent intent = new Intent(this, ExamMonitorActivity.class);
            intent.putExtra("exam_code", examCode);
            intent.putExtra("exam_title", examTitle);
            intent.putExtra("exam_id", examId);
            startActivity(intent);
        });

        binding.btnDeleteExam.setOnClickListener(v -> showDeleteConfirmationDialog());
    }

    private void setupSearchWatcher() {
        binding.etSearchStudents.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void loadResults(boolean showLoader) {
        if (showLoader) {
            binding.progressLoading.setVisibility(View.VISIBLE);
        }

        resultApi.getResultsForExam("eq." + examCode, "*", "score.desc").enqueue(new Callback<List<Result>>() {
            @Override
            public void onResponse(Call<List<Result>> call, Response<List<Result>> response) {
                binding.progressLoading.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    List<Result> resultsList = response.body();
                    
                    binding.tvParticipantsCount.setText(String.valueOf(resultsList.size()));

                    if (resultsList.isEmpty()) {
                        binding.rvResults.setVisibility(View.GONE);
                        binding.tvEmptyState.setVisibility(View.VISIBLE);
                        binding.tvAvgScore.setText("0%");
                    } else {
                        binding.tvEmptyState.setVisibility(View.GONE);
                        binding.rvResults.setVisibility(View.VISIBLE);
                        adapter.setResults(resultsList);

                        // Compute average percentage score
                        double totalPercent = 0.0;
                        for (Result r : resultsList) {
                            totalPercent += r.getPercentage();
                        }
                        double avgPercent = totalPercent / resultsList.size();
                        binding.tvAvgScore.setText(String.format(Locale.getDefault(), "%.0f%%", avgPercent));
                    }
                } else {
                    Toast.makeText(ExamResultsActivity.this, "Failed to load results: HTTP " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<Result>> call, Throwable t) {
                binding.progressLoading.setVisibility(View.GONE);
                Toast.makeText(ExamResultsActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void verifyPublishStatus() {
        // Query single exam record to see if results are already published
        examApi.getExamByCode("eq." + examCode, "*").enqueue(new Callback<List<Exam>>() {
            @Override
            public void onResponse(Call<List<Exam>> call, Response<List<Exam>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    exam = response.body().get(0);
                    if (exam.resultsPublished) {
                        binding.btnPublishResults.setEnabled(false);
                        binding.btnPublishResults.setText("Results Published");
                    }
                }
            }
            @Override public void onFailure(Call<List<Exam>> call, Throwable t) {}
        });
    }

    private void showStudentResultBreakdownDialog(Result result) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_teacher_student_result, null);
        
        android.widget.TextView tvName = dialogView.findViewById(R.id.tv_dialog_student_name);
        android.widget.TextView tvScore = dialogView.findViewById(R.id.tv_dialog_student_score);
        android.widget.TextView tvEnrollment = dialogView.findViewById(R.id.tv_dialog_student_enrollment);
        android.widget.TextView tvTelemetry = dialogView.findViewById(R.id.tv_dialog_student_telemetry);
        androidx.recyclerview.widget.RecyclerView rvReview = dialogView.findViewById(R.id.rv_review_questions);

        tvName.setText(result.studentName);
        tvScore.setText(result.score + " / " + result.total);
        tvEnrollment.setText("Enrollment: " + result.enrollmentNumber);
        tvTelemetry.setText(result.warnings + " warnings, " + result.appSwitches + " app switches");

        rvReview.setLayoutManager(new LinearLayoutManager(this));
        ReviewAnswersAdapter reviewAdapter = new ReviewAnswersAdapter(exam.questions, result.answers);
        rvReview.setAdapter(reviewAdapter);

        new MaterialAlertDialogBuilder(this, R.style.Studdy_Dialog)
                .setTitle("Student Attempt Details")
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .show();
    }

    private void publishResults() {
        binding.progressLoading.setVisibility(View.VISIBLE);

        Map<String, Object> patch = new HashMap<>();
        patch.put("results_published", true);

        examApi.updateExam("eq." + examId, patch).enqueue(new Callback<List<Exam>>() {
            @Override
            public void onResponse(Call<List<Exam>> call, Response<List<Exam>> response) {
                binding.progressLoading.setVisibility(View.GONE);
                if (response.isSuccessful()) {
                    Toast.makeText(ExamResultsActivity.this, "Exam results successfully published to students!", Toast.LENGTH_LONG).show();
                    binding.btnPublishResults.setEnabled(false);
                    binding.btnPublishResults.setText("Results Published");
                } else {
                    Toast.makeText(ExamResultsActivity.this, "Failed publishing results: HTTP " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<Exam>> call, Throwable t) {
                binding.progressLoading.setVisibility(View.GONE);
                Toast.makeText(ExamResultsActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDeleteConfirmationDialog() {
        new MaterialAlertDialogBuilder(this, R.style.Studdy_Dialog)
                .setTitle("Delete Exam")
                .setMessage("Are you sure you want to permanently delete this exam and all its results? This action cannot be undone.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Delete Permanently", (dialog, which) -> deleteExamRecord())
                .show();
    }

    private void deleteExamRecord() {
        binding.progressLoading.setVisibility(View.VISIBLE);

        examApi.deleteExam("eq." + examId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                binding.progressLoading.setVisibility(View.GONE);
                if (response.isSuccessful()) {
                    Toast.makeText(ExamResultsActivity.this, "Exam deleted successfully.", Toast.LENGTH_LONG).show();
                    finish(); // return to dashboard
                } else {
                    Toast.makeText(ExamResultsActivity.this, "Failed to delete exam: HTTP " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                binding.progressLoading.setVisibility(View.GONE);
                Toast.makeText(ExamResultsActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}

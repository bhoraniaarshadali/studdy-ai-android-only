package com.arshad.studdy_app_android_only.ui.teacher.monitor;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.Exam;
import com.arshad.studdy_app_android_only.data.model.ExamSession;
import com.arshad.studdy_app_android_only.data.remote.api.ExamApi;
import com.arshad.studdy_app_android_only.data.remote.api.ExamSessionApi;
import com.arshad.studdy_app_android_only.databinding.ActivityExamMonitorBinding;
import com.arshad.studdy_app_android_only.network.SupabaseClient;
import com.arshad.studdy_app_android_only.ui.BaseActivity;
import com.arshad.studdy_app_android_only.util.DateTimeUtils;
import com.arshad.studdy_app_android_only.util.SessionManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Date;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Real-time Proctoring Monitor activity.
 * Employs a robust, high-frequency polling thread to monitor student exam sessions,
 * warning logs, and live telemetry heartbeat updates.
 */
public class ExamMonitorActivity extends BaseActivity {

    private static final String TAG = "ExamMonitorActivity";

    private ActivityExamMonitorBinding binding;
    private LiveSessionsAdapter adapter;
    
    private String examCode;
    private String examTitle;
    private String examId;
    private Exam exam;

    private ExamSessionApi sessionApi;
    private ExamApi examApi;
    
    // Polling Runner
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = this::fetchLiveSessions;
    private static final long POLL_INTERVAL_MS = 3000; // 3 seconds
    private boolean isPolling = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityExamMonitorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        examCode = getIntent().getStringExtra("exam_code");
        examTitle = getIntent().getStringExtra("exam_title");
        examId = getIntent().getStringExtra("exam_id");

        if (examCode == null) {
            Toast.makeText(this, "Missing exam code parameter.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbar();
        setupRecyclerView();

        String token = SessionManager.getInstance(this).getAccessToken();
        sessionApi = SupabaseClient.createAuthService(ExamSessionApi.class, token);
        examApi = SupabaseClient.createAuthService(ExamApi.class, token);

        loadExamDetails();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.setTitle(examTitle != null ? examTitle : "Live Proctoring");
        binding.toolbar.setSubtitle("Code: " + examCode);
        binding.toolbar.inflateMenu(R.menu.menu_exam_monitor);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_edit_questions) {
                showEditQuestionsDialog();
                return true;
            }
            return false;
        });
    }

    private void setupRecyclerView() {
        adapter = new LiveSessionsAdapter();
        binding.rvLiveSessions.setLayoutManager(new LinearLayoutManager(this));
        binding.rvLiveSessions.setAdapter(adapter);
    }

    private void fetchLiveSessions() {
        if (!isPolling) return;

        sessionApi.getSessionsForExam("eq." + examCode, "eq.active", "*", "joined_at.desc").enqueue(new Callback<List<ExamSession>>() {
            @Override
            public void onResponse(Call<List<ExamSession>> call, Response<List<ExamSession>> response) {
                if (!isPolling) return;
                binding.progressLoading.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    List<ExamSession> sessions = response.body();
                    
                    if (sessions.isEmpty()) {
                        binding.rvLiveSessions.setVisibility(View.GONE);
                        binding.tvEmptyState.setVisibility(View.VISIBLE);
                        binding.tvActiveCount.setText("0 Students Active");
                    } else {
                        binding.tvEmptyState.setVisibility(View.GONE);
                        binding.rvLiveSessions.setVisibility(View.VISIBLE);
                        adapter.setSessions(sessions);

                        // Calculate active student counts (active status + heartbeat <= 12 seconds)
                        int activeCount = 0;
                        for (ExamSession session : sessions) {
                            if ("active".equals(session.status)) {
                                Date lastSeenDate = DateTimeUtils.parseIso8601(session.lastHeartbeat != null ? session.lastHeartbeat : session.joinedAt);
                                long elapsed = System.currentTimeMillis() - (lastSeenDate != null ? lastSeenDate.getTime() : 0);
                                if (elapsed <= 12000) {
                                    activeCount++;
                                }
                            }
                        }
                        binding.tvActiveCount.setText(activeCount + " of " + sessions.size() + " Students Taking Exam");
                    }
                } else {
                    Log.w(TAG, "Polling failed: HTTP " + response.code());
                }

                // Schedule next poll
                pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
            }

            @Override
            public void onFailure(Call<List<ExamSession>> call, Throwable t) {
                if (!isPolling) return;
                binding.progressLoading.setVisibility(View.GONE);
                Log.e(TAG, "Polling network error", t);
                
                // Schedule next poll anyway to keep syncing
                pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Start polling
        isPolling = true;
        binding.progressLoading.setVisibility(View.VISIBLE);
        fetchLiveSessions();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop polling to preserve resource consumption
        isPolling = false;
        pollHandler.removeCallbacks(pollRunnable);
    }

    private void loadExamDetails() {
        examApi.getExamByCode("eq." + examCode, "*").enqueue(new Callback<List<Exam>>() {
            @Override
            public void onResponse(Call<List<Exam>> call, Response<List<Exam>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    exam = response.body().get(0);
                    if (examId == null) {
                        examId = exam.id;
                    }
                }
            }
            @Override public void onFailure(Call<List<Exam>> call, Throwable t) {
                Log.e(TAG, "Failed to load exam details", t);
            }
        });
    }

    private boolean isExamInProgress() {
        if (exam == null) return false;
        if (exam.resultsPublished) return false;
        if (exam.isTimerWindow() && DateTimeUtils.isAfterWindow(exam.windowEnd)) {
            return false;
        }
        return true;
    }

    private void showEditQuestionsDialog() {
        if (exam == null) {
            Toast.makeText(this, "Exam details are still loading. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean inProgress = isExamInProgress();

        // Create a deep copy of the questions list so modifications in dialog don't apply until saved
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String json = gson.toJson(exam.questions);
        java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<com.arshad.studdy_app_android_only.data.model.Question>>(){}.getType();
        List<com.arshad.studdy_app_android_only.data.model.Question> questionsCopy = gson.fromJson(json, listType);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_questions, null);
        androidx.recyclerview.widget.RecyclerView rvQuestions = dialogView.findViewById(R.id.rv_edit_questions);
        rvQuestions.setLayoutManager(new LinearLayoutManager(this));
        
        EditQuestionsAdapter editAdapter = new EditQuestionsAdapter(questionsCopy, inProgress);
        rvQuestions.setAdapter(editAdapter);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.Studdy_Dialog)
                .setTitle("Edit Correct Answers")
                .setView(dialogView);

        if (inProgress) {
            builder.setPositiveButton("Save Changes", (dialog, which) -> {
                saveCorrectAnswers(questionsCopy);
            });
            builder.setNegativeButton(R.string.cancel, null);
        } else {
            builder.setPositiveButton("Close", null);
        }

        builder.show();
    }

    private void saveCorrectAnswers(List<com.arshad.studdy_app_android_only.data.model.Question> updatedQuestions) {
        binding.progressLoading.setVisibility(View.VISIBLE);

        Map<String, Object> patch = new HashMap<>();
        patch.put("questions", updatedQuestions);

        examApi.updateExam("eq." + examId, patch).enqueue(new Callback<List<Exam>>() {
            @Override
            public void onResponse(Call<List<Exam>> call, Response<List<Exam>> response) {
                binding.progressLoading.setVisibility(View.GONE);
                if (response.isSuccessful()) {
                    exam.questions = updatedQuestions;
                    Toast.makeText(ExamMonitorActivity.this, "Correct answers updated successfully!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(ExamMonitorActivity.this, "Failed to update: HTTP " + response.code(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<List<Exam>> call, Throwable t) {
                binding.progressLoading.setVisibility(View.GONE);
                Toast.makeText(ExamMonitorActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}

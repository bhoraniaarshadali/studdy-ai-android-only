package com.arshad.studdy_app_android_only.ui.teacher.dashboard;

import android.content.Intent;
import com.arshad.studdy_app_android_only.util.ErrorMessageMapper;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.Exam;
import com.arshad.studdy_app_android_only.data.remote.api.ExamApi;
import com.arshad.studdy_app_android_only.databinding.ActivityTeacherDashboardBinding;
import com.arshad.studdy_app_android_only.network.SupabaseClient;
import com.arshad.studdy_app_android_only.ui.BaseActivity;
import com.arshad.studdy_app_android_only.ui.home.RoleSelectionActivity;
import com.arshad.studdy_app_android_only.ui.teacher.create.CreateExamActivity;
import com.arshad.studdy_app_android_only.ui.teacher.papers.PaperListActivity;
import com.arshad.studdy_app_android_only.ui.teacher.results.ExamResultsActivity;
import com.arshad.studdy_app_android_only.util.SessionManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Teacher Dashboard.
 * Lists exams created by the teacher, and provides navigations to create online exams,
 * generate printable papers, or view exam results.
 */
public class TeacherDashboardActivity extends BaseActivity implements TeacherExamAdapter.OnExamClickListener {

    private ActivityTeacherDashboardBinding binding;
    private SessionManager sessionManager;
    private TeacherExamAdapter adapter;
    private ExamApi examApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTeacherDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = SessionManager.getInstance(this);
        if (!sessionManager.hasTeacherSession()) {
            navigateToRoleSelection();
            return;
        }

        setupFacultyInfo();
        setupRecyclerView();
        setupClickListeners();

        // Setup API service with current teacher access token
        String token = sessionManager.getAccessToken();
        examApi = SupabaseClient.createAuthService(ExamApi.class, token);

        loadTeacherExams(true);
    }

    private void setupFacultyInfo() {
        String teacherName = sessionManager.getTeacherName();
        if (teacherName != null && !teacherName.isEmpty()) {
            binding.tvTeacherName.setText(teacherName);
        } else {
            binding.tvTeacherName.setText("Faculty Member");
        }
    }

    private void setupRecyclerView() {
        adapter = new TeacherExamAdapter(this);
        binding.rvTeacherExams.setLayoutManager(new LinearLayoutManager(this));
        binding.rvTeacherExams.setAdapter(adapter);
    }

    private void setupClickListeners() {
        binding.swipeRefresh.setOnRefreshListener(() -> loadTeacherExams(false));

        binding.ivMenu.setOnClickListener(v -> showSettingsMenu());

        binding.cardCreateExam.setOnClickListener(v -> {
            startActivity(new Intent(this, CreateExamActivity.class));
        });

        binding.cardGeneratePaper.setOnClickListener(v -> {
            startActivity(new Intent(this, PaperListActivity.class));
        });

        binding.fabCreateExam.setOnClickListener(v -> showCreationOptionsDialog());
    }

    private void showSettingsMenu() {
        String[] options = {"Logout"};
        new MaterialAlertDialogBuilder(this, R.style.Studdy_Dialog)
                .setTitle("Account Settings")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        new MaterialAlertDialogBuilder(TeacherDashboardActivity.this, R.style.Studdy_Dialog)
                                .setTitle("Logout")
                                .setMessage("Are you sure you want to logout?")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Logout", (d, w) -> {
                                    sessionManager.clearTeacherSession();
                                    navigateToRoleSelection();
                                })
                                .show();
                    }
                })
                .show();
    }

    private void showCreationOptionsDialog() {
        CreateNewBottomSheet.newInstance().show(getSupportFragmentManager(), "CreateNewBottomSheet");
    }

    private void loadTeacherExams(boolean showLoader) {
        if (showLoader) {
            binding.swipeRefresh.setRefreshing(true);
        }

        String teacherId = sessionManager.getTeacherId();
        examApi.getTeacherExams("eq." + teacherId, "*", "created_at.desc").enqueue(new Callback<List<Exam>>() {
            @Override
            public void onResponse(Call<List<Exam>> call, Response<List<Exam>> response) {
                binding.swipeRefresh.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    List<Exam> exams = response.body();
                    if (exams.isEmpty()) {
                        binding.rvTeacherExams.setVisibility(View.GONE);
                        binding.layoutEmptyState.setVisibility(View.VISIBLE);
                    } else {
                        binding.layoutEmptyState.setVisibility(View.GONE);
                        binding.rvTeacherExams.setVisibility(View.VISIBLE);
                        adapter.setExams(exams);
                    }
                } else {
                    String friendly = ErrorMessageMapper.toUserMessage("TeacherDashboardActivity", "Failed to load exams", response.code());
                    Toast.makeText(TeacherDashboardActivity.this, friendly, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<Exam>> call, Throwable t) {
                binding.swipeRefresh.setRefreshing(false);
                String friendly = ErrorMessageMapper.toUserMessage("TeacherDashboardActivity", "Network error loading exams", t);
                Toast.makeText(TeacherDashboardActivity.this, friendly, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onExamClick(Exam exam) {
        // Tapping an exam goes to results overview
        Intent intent = new Intent(this, ExamResultsActivity.class);
        intent.putExtra("exam_code", exam.code);
        intent.putExtra("exam_title", exam.title);
        intent.putExtra("exam_id", exam.id);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh dashboard content on return
        if (sessionManager.hasTeacherSession()) {
            loadTeacherExams(false);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Overflow Menu
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_teacher, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_sign_out) {
            sessionManager.clearTeacherSession();
            navigateToRoleSelection();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void navigateToRoleSelection() {
        Intent intent = new Intent(this, RoleSelectionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

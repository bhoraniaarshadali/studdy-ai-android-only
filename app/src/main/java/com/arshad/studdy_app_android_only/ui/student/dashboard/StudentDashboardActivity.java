package com.arshad.studdy_app_android_only.ui.student.dashboard;

import android.content.Intent;
import com.arshad.studdy_app_android_only.util.ErrorMessageMapper;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.Exam;
import com.arshad.studdy_app_android_only.data.model.ExamSession;
import com.arshad.studdy_app_android_only.data.model.Student;
import com.arshad.studdy_app_android_only.data.repository.StudentRepository;
import com.arshad.studdy_app_android_only.databinding.ActivityStudentDashboardBinding;
import com.arshad.studdy_app_android_only.ui.BaseActivity;
import com.arshad.studdy_app_android_only.ui.home.RoleSelectionActivity;
import com.arshad.studdy_app_android_only.ui.student.entry.StudentEntryActivity;
import com.arshad.studdy_app_android_only.ui.student.exam.PreExamInstructionsActivity;
import com.arshad.studdy_app_android_only.ui.student.result.StudentResultActivity;
import com.arshad.studdy_app_android_only.util.DateTimeUtils;
import com.arshad.studdy_app_android_only.util.SessionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Student Dashboard.
 * Lists all available exams, displays student info, handles resumption or starting of exams,
 * and handles routing to results if already submitted.
 */
public class StudentDashboardActivity extends BaseActivity implements ExamCardAdapter.OnExamClickListener {

    private ActivityStudentDashboardBinding binding;
    private StudentRepository studentRepository;
    private ExamCardAdapter adapter;
    
    private String enrollmentNumber;
    private String studentName = "";

    private final List<Exam> allExams = new ArrayList<>();
    private final Set<String> attemptedExamCodes = new HashSet<>();
    private int selectedTabPosition = 0; // 0 = Ongoing, 1 = Upcoming
    private String selectedFilter = "All"; // "All", "Not Attempted", "Attempted"
    private boolean isStartingExam = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudentDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        studentRepository = new StudentRepository();

        enrollmentNumber = SessionManager.getInstance(this).getStudentEnrollment();
        if (enrollmentNumber == null || enrollmentNumber.isEmpty()) {
            // No student session saved, go back to Role Selection
            navigateToRoleSelection();
            return;
        }

        setupRecyclerView();
        setupTabLayout();
        setupFilterMenu();
        setupClickListeners();
        
        loadStudentDetails();
        loadExams(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isStartingExam = false;
    }

    private void setupRecyclerView() {
        adapter = new ExamCardAdapter(this);
        binding.rvExams.setLayoutManager(new LinearLayoutManager(this));
        binding.rvExams.setAdapter(adapter);
    }

    private void setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedTabPosition = tab.getPosition();
                if (selectedTabPosition == 0) {
                    binding.scrollFilterChips.setVisibility(View.VISIBLE);
                } else {
                    binding.scrollFilterChips.setVisibility(View.GONE);
                }
                filterAndDisplayExams();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupFilterMenu() {
        binding.btnFilter.setOnClickListener(v -> {
            androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, v);
            popup.getMenu().add("All");
            popup.getMenu().add("Not Attempted");
            popup.getMenu().add("Attempted");
            popup.setOnMenuItemClickListener(item -> {
                selectedFilter = item.getTitle().toString();
                filterAndDisplayExams();
                return true;
            });
            popup.show();
        });
    }

    private void setupClickListeners() {
        binding.btnQrScan.setOnClickListener(v -> {
            Intent intent = new Intent(this, StudentEntryActivity.class);
            startActivity(intent);
        });

        binding.cardMyResults.setOnClickListener(v -> {
            Intent intent = new Intent(this, MyResultsActivity.class);
            startActivity(intent);
        });

        binding.btnRefresh.setOnClickListener(v -> {
            loadStudentDetails();
            loadExams(true);
        });

        binding.swipeRefresh.setOnRefreshListener(() -> {
            loadStudentDetails();
            loadExams(false);
        });

        binding.btnLogout.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this, R.style.Studdy_Dialog)
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to logout?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Logout", (dialog, which) -> {
                        SessionManager.getInstance(this).clearStudentSession();
                        navigateToRoleSelection();
                    })
                    .show();
        });
    }

    private void loadStudentDetails() {
        // Pre-populate immediately from session
        String savedName = SessionManager.getInstance(this).getStudentName();
        studentName = savedName;
        binding.tvStudentGreeting.setText("Hello, " + savedName);
        binding.tvStudentInfo.setText("Enrollment: " + enrollmentNumber);

        studentRepository.getStudentByEnrollment(enrollmentNumber, new StudentRepository.Callback<Student>() {
            @Override
            public void onSuccess(Student student) {
                studentName = student.name;
                binding.tvStudentGreeting.setText("Hello, " + student.name);
            }

            @Override
            public void onFailure(String errorMessage) {
                // Keep the placeholder greeting
            }
        });
    }

    private void loadExams(boolean showLoader) {
        if (showLoader) {
            binding.swipeRefresh.setRefreshing(true);
        }
        
        studentRepository.getAllExams(new StudentRepository.Callback<List<Exam>>() {
            @Override
            public void onSuccess(List<Exam> exams) {
                allExams.clear();
                if (exams != null) {
                    allExams.addAll(exams);
                }
                loadStudentResults();
            }

            @Override
            public void onFailure(String errorMessage) {
                binding.swipeRefresh.setRefreshing(false);
                String friendlyMsg = ErrorMessageMapper.toUserMessage("StudentDashboardActivity", "Failed to load exams", errorMessage);
                Toast.makeText(StudentDashboardActivity.this, friendlyMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadStudentResults() {
        studentRepository.getStudentResults(enrollmentNumber, new StudentRepository.Callback<List<com.arshad.studdy_app_android_only.data.model.Result>>() {
            @Override
            public void onSuccess(List<com.arshad.studdy_app_android_only.data.model.Result> results) {
                binding.swipeRefresh.setRefreshing(false);
                attemptedExamCodes.clear();
                int completedCount = 0;
                if (results != null) {
                    completedCount = results.size();
                    for (com.arshad.studdy_app_android_only.data.model.Result r : results) {
                        attemptedExamCodes.add(r.examCode);
                    }
                }
                binding.tvCompletedCount.setText(completedCount + (completedCount == 1 ? " exam completed" : " exams completed"));
                filterAndDisplayExams();
            }

            @Override
            public void onFailure(String errorMessage) {
                binding.swipeRefresh.setRefreshing(false);
                String friendlyMsg = ErrorMessageMapper.toUserMessage("StudentDashboardActivity", "Failed to load attempts", errorMessage);
                Toast.makeText(StudentDashboardActivity.this, friendlyMsg, Toast.LENGTH_SHORT).show();
                filterAndDisplayExams();
            }
        });
    }

    private void filterAndDisplayExams() {
        List<Exam> filteredList = new ArrayList<>();
        
        for (Exam exam : allExams) {
            boolean isUpcoming = exam.isTimerWindow() && DateTimeUtils.isBeforeWindow(exam.windowStart);
            boolean isOngoing = !isUpcoming;
            
            if (selectedTabPosition == 0) {
                // Ongoing
                if (isOngoing) {
                    boolean attempted = attemptedExamCodes.contains(exam.code);
                    if ("All".equals(selectedFilter)) {
                        filteredList.add(exam);
                    } else if ("Not Attempted".equals(selectedFilter) && !attempted) {
                        filteredList.add(exam);
                    } else if ("Attempted".equals(selectedFilter) && attempted) {
                        filteredList.add(exam);
                    }
                }
            } else {
                // Upcoming
                if (isUpcoming) {
                    filteredList.add(exam);
                }
            }
        }
        
        if (filteredList.isEmpty()) {
            binding.rvExams.setVisibility(View.GONE);
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
            if (selectedTabPosition == 0) {
                binding.ivEmptyState.setImageResource(R.drawable.ic_book);
                binding.tvEmptyState.setText("No exams available right now");
            } else {
                binding.ivEmptyState.setImageResource(R.drawable.ic_calendar);
                binding.tvEmptyState.setText("No upcoming exams scheduled");
            }
        } else {
            binding.layoutEmptyState.setVisibility(View.GONE);
            binding.rvExams.setVisibility(View.VISIBLE);
            adapter.setExams(filteredList, attemptedExamCodes, selectedTabPosition == 1);
        }
    }

    @Override
    public void onExamClick(Exam exam) {
        if (isStartingExam) return;

        // 1. If upcoming, show not started toast
        if (exam.isTimerWindow() && DateTimeUtils.isBeforeWindow(exam.windowStart)) {
            Toast.makeText(this, R.string.err_exam_not_started, Toast.LENGTH_LONG).show();
            return;
        }

        // 2. Check if already attempted
        boolean attempted = attemptedExamCodes.contains(exam.code);
        if (attempted) {
            if (exam.isInstantResult() || exam.resultsPublished) {
                Intent intent = new Intent(StudentDashboardActivity.this, StudentResultActivity.class);
                intent.putExtra("exam_code", exam.code);
                intent.putExtra("student_name", studentName);
                intent.putExtra("enrollment_number", enrollmentNumber);
                startActivity(intent);
            } else {
                Toast.makeText(StudentDashboardActivity.this, R.string.err_already_attempted, Toast.LENGTH_LONG).show();
            }
            return;
        }

        // 3. Check if expired
        if (exam.isTimerWindow() && DateTimeUtils.isAfterWindow(exam.windowEnd)) {
            Toast.makeText(this, "This exam's window has closed", Toast.LENGTH_LONG).show();
            return;
        }

        // 4. Check for active session (resumption)
        isStartingExam = true;
        binding.swipeRefresh.setRefreshing(true);
        checkAndJoinSession(exam);
    }

    private void checkAndJoinSession(Exam exam) {
        studentRepository.getActiveSession(exam.code, enrollmentNumber, new StudentRepository.Callback<ExamSession>() {
            @Override
            public void onSuccess(ExamSession activeSession) {
                if (activeSession != null) {
                    // Resume existing active session
                    binding.swipeRefresh.setRefreshing(false);
                    isStartingExam = false;
                    startExamActivity(exam, activeSession.id);
                } else {
                    // Create new active session
                    createNewSession(exam);
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                // If check fails, try creating session directly
                createNewSession(exam);
            }
        });
    }

    private void createNewSession(Exam exam) {
        ExamSession session = new ExamSession();
        session.examCode = exam.code;
        session.enrollmentNumber = enrollmentNumber;
        session.studentName = studentName;
        session.joinedAt = DateTimeUtils.formatIso8601Now();
        session.status = "active";
        session.warnings = 0;
        session.warningsLog = new ArrayList<>();
        session.currentAnswers = new ArrayList<>(Collections.nCopies(exam.getQuestionCount(), -1));

        studentRepository.createExamSession(session, new StudentRepository.Callback<ExamSession>() {
            @Override
            public void onSuccess(ExamSession createdSession) {
                binding.swipeRefresh.setRefreshing(false);
                isStartingExam = false;
                startExamActivity(exam, createdSession.id);
            }

            @Override
            public void onFailure(String errorMessage) {
                binding.swipeRefresh.setRefreshing(false);
                isStartingExam = false;
                String friendlyMsg = ErrorMessageMapper.toUserMessage("StudentDashboardActivity", "Failed to start exam session", errorMessage);
                Toast.makeText(StudentDashboardActivity.this, friendlyMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startExamActivity(Exam exam, long sessionId) {
        Intent intent = new Intent(StudentDashboardActivity.this, PreExamInstructionsActivity.class);
        intent.putExtra("exam_code", exam.code);
        intent.putExtra("student_name", studentName);
        intent.putExtra("enrollment_number", enrollmentNumber);
        intent.putExtra("session_id", sessionId);
        startActivity(intent);
    }

    private void navigateToRoleSelection() {
        Intent intent = new Intent(this, RoleSelectionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

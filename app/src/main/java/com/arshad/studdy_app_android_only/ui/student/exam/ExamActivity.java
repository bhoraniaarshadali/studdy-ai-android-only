package com.arshad.studdy_app_android_only.ui.student.exam;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.Exam;
import com.arshad.studdy_app_android_only.data.model.ExamSession;
import com.arshad.studdy_app_android_only.data.model.Question;
import com.arshad.studdy_app_android_only.data.model.Result;
import com.arshad.studdy_app_android_only.data.repository.StudentRepository;
import com.arshad.studdy_app_android_only.databinding.ActivityExamBinding;
import com.arshad.studdy_app_android_only.ui.BaseActivity;
import com.arshad.studdy_app_android_only.ui.student.dashboard.StudentDashboardActivity;
import com.arshad.studdy_app_android_only.ui.student.proctoring.FaceDetectionManager;
import com.arshad.studdy_app_android_only.ui.student.result.StudentResultActivity;
import com.arshad.studdy_app_android_only.util.DateTimeUtils;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.activity.OnBackPressedCallback;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.arshad.studdy_app_android_only.util.ErrorMessageMapper;

import com.arshad.studdy_app_android_only.data.local.AppDatabase;
import com.arshad.studdy_app_android_only.data.local.dao.LocalExamAttemptDao;
import com.arshad.studdy_app_android_only.data.local.entity.LocalExamAttempt;
import com.arshad.studdy_app_android_only.data.local.entity.SyncStatus;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main online exam environment screen.
 * Handles questions, answers state, count-down timers, periodic auto-saves,
 * ML Kit front-camera proctoring warnings, app switch counts, and result submission.
 */
public class ExamActivity extends BaseActivity implements QuestionPagerAdapter.OnAnswerSelectedListener, FaceDetectionManager.ProctoringListener {

    private static final String TAG = "ExamActivity";

    private ActivityExamBinding binding;
    private StudentRepository studentRepository;
    private QuestionPagerAdapter pagerAdapter;
    
    // Room local cache for offline resilience (Phase 2)
    private LocalExamAttemptDao localAttemptDao;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    
    private Exam exam;
    private long sessionId;
    private String examCode;
    private String studentName;
    private String enrollmentNumber;

    // Timer & Autosave
    private CountDownTimer examTimer;
    private final Handler autoSaveHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoSaveRunnable = this::triggerAutoSave;
    private static final long AUTOSAVE_INTERVAL_MS = 10000; // 10 seconds

    // Proctoring
    private FaceDetectionManager proctoringManager;
    private int warningCount = 0;
    private int appSwitches = 0;
    private final List<ExamSession.WarningEvent> warningsLog = new ArrayList<>();
    private boolean isWarningVisible = false;
    private boolean isSubmitting = false;

    // Lock Task (Screen Pinning) state tracking
    private boolean isLockTaskActive = false;
    private Runnable pendingPostLockTaskAction = null;

    // Proctoring Cooldown
    private long lastWarningDismissedAt = 0;
    private static final long WARNING_COOLDOWN_MS = 10000; // 10 seconds

    // DND Interruption Filter State caching
    private int previousInterruptionFilter = -1;

    // Wall clock dynamic updater
    private final Handler wallClockHandler = new Handler(Looper.getMainLooper());
    private final Runnable wallClockRunnable = new Runnable() {
        @Override
        public void run() {
            updateWallClock();
            wallClockHandler.postDelayed(this, 1000);
        }
    };

    private void updateWallClock() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault());
        String currentTime = sdf.format(new java.util.Date());
        binding.tvWallClock.setText("Time: " + currentTime);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityExamBinding.inflate(getLayoutInflater());
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

        setupViewPager();
        setupClickListeners();

        // Initialize Room DAO off the main thread — Room.build() does synchronous
        // disk I/O on the first call which would block the main thread for seconds.
        initRoomThenLoad();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitConfirmationDialog();
            }
        });
    }

    private void setupViewPager() {
        pagerAdapter = new QuestionPagerAdapter(this);
        binding.viewPager.setAdapter(pagerAdapter);
        binding.viewPager.setUserInputEnabled(false); // only navigate via buttons for control

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateProgressHeader(position);
                updateNavigationButtons(position);
            }
        });
    }

    private void setupClickListeners() {
        binding.btnPrevious.setOnClickListener(v -> {
            int current = binding.viewPager.getCurrentItem();
            if (current > 0) {
                binding.viewPager.setCurrentItem(current - 1, true);
            }
        });

        binding.btnNext.setOnClickListener(v -> {
            int current = binding.viewPager.getCurrentItem();
            if (current < pagerAdapter.getItemCount() - 1) {
                binding.viewPager.setCurrentItem(current + 1, true);
            }
        });

        binding.btnSubmit.setOnClickListener(v -> {
            showSubmitConfirmationDialog();
        });

        binding.btnDismissWarning.setOnClickListener(v -> {
            dismissProctoringWarning();
        });
    }

    private void updateProgressHeader(int position) {
        int total = pagerAdapter.getItemCount();
        binding.tvQuestionProgress.setText(getString(R.string.question_counter, (position + 1), total));
    }

    private void updateNavigationButtons(int position) {
        int total = pagerAdapter.getItemCount();
        
        binding.btnPrevious.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
        
        if (position == total - 1) {
            binding.btnNext.setVisibility(View.GONE);
            binding.btnSubmit.setVisibility(View.VISIBLE);
        } else {
            binding.btnNext.setVisibility(View.VISIBLE);
            binding.btnSubmit.setVisibility(View.GONE);
        }
    }

    /**
     * Open/create Room DB on a background thread (first-time open does disk I/O
     * that would freeze the main thread for 5-7 seconds if called synchronously).
     * Only after the DAO is ready do we kick off the network fetch.
     */
    private void initRoomThenLoad() {
        dbExecutor.execute(() -> {
            localAttemptDao = AppDatabase.getInstance(getApplicationContext()).localExamAttemptDao();
            // Post back to main thread to start the network calls
            new Handler(Looper.getMainLooper()).post(this::loadExamDetails);
        });
    }

    private void loadExamDetails() {
        studentRepository.getExamByCode(examCode, new StudentRepository.Callback<Exam>() {
            @Override
            public void onSuccess(Exam fetchedExam) {
                exam = fetchedExam;
                binding.tvExamTitle.setText(exam.title);
                
                // Fetch the active session to restore state (answers, warnings, logs, question index)
                restoreSessionState();
            }

            @Override
            public void onFailure(String errorMessage) {
                binding.layoutInitLoader.setVisibility(View.GONE);
                String friendlyMsg = ErrorMessageMapper.toUserMessage(TAG, "Failed to load exam questions", errorMessage);
                Toast.makeText(ExamActivity.this, friendlyMsg, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
     }

    private void restoreSessionState() {
        studentRepository.getActiveSession(examCode, enrollmentNumber, new StudentRepository.Callback<ExamSession>() {
            @Override
            public void onSuccess(ExamSession session) {
                if (session != null) {
                    warningCount = session.warnings;
                    if (session.warningsLog != null) {
                        warningsLog.addAll(session.warningsLog);
                    }
                    
                    // Bind questions and saved answers
                    pagerAdapter.setData(exam.questions, session.currentAnswers);
                    
                    // Restore last viewed question index
                    if (session.lastQuestionIndex >= 0 && session.lastQuestionIndex < exam.questions.size()) {
                        binding.viewPager.setCurrentItem(session.lastQuestionIndex, false);
                    }

                    // Start timer immediately (CountDownTimer, no I/O)
                    startExamTimer(session.joinedAt);

                    // Defer CameraX init to AFTER the first frame is drawn.
                    // ProcessCameraProvider.bindToLifecycle() competes with the
                    // layout pass and starves the GPU buffer queue (6–12s jank).
                    final String joinedAt = session.joinedAt;
                    binding.getRoot().post(() -> {
                        startProctoringCamera();
                        try {
                            startLockTask();
                            isLockTaskActive = true;
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to start lock task mode", e);
                        }
                    });

                    // Start periodic autosave loop
                    autoSaveHandler.postDelayed(autoSaveRunnable, AUTOSAVE_INTERVAL_MS);
                } else {
                    // Fail-safe: populate clean options if session wasn't found
                    pagerAdapter.setData(exam.questions, null);
                    startExamTimer(DateTimeUtils.formatIso8601Now());

                    // Defer CameraX init to after first frame
                    binding.getRoot().post(() -> {
                        startProctoringCamera();
                        try {
                            startLockTask();
                            isLockTaskActive = true;
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to start lock task mode", e);
                        }
                    });
                }
                binding.layoutInitLoader.setVisibility(View.GONE);
            }

            @Override
            public void onFailure(String errorMessage) {
                // Fail-safe loading
                pagerAdapter.setData(exam.questions, null);
                startExamTimer(DateTimeUtils.formatIso8601Now());
                binding.layoutInitLoader.setVisibility(View.GONE);
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // Timers
    // ══════════════════════════════════════════════════════════════════════

    private void startExamTimer(String joinedAtIso) {
        long remainingMillis = Long.MAX_VALUE;

        if (exam.isTimerDuration() && exam.durationMinutes != null) {
            Date joinedDate = DateTimeUtils.parseIso8601(joinedAtIso);
            long startMillis = joinedDate != null ? joinedDate.getTime() : System.currentTimeMillis();
            long durationMillis = exam.durationMinutes * 60 * 1000;
            long elapsed = System.currentTimeMillis() - startMillis;
            remainingMillis = durationMillis - elapsed;
        } else if (exam.isTimerWindow() && exam.windowEnd != null) {
            Date endDate = DateTimeUtils.parseIso8601(exam.windowEnd);
            remainingMillis = endDate != null ? endDate.getTime() - System.currentTimeMillis() : Long.MAX_VALUE;
        }

        if (remainingMillis <= 0) {
            showToast("Time has already expired! Auto-submitting...");
            autoSubmitExam();
            return;
        }

        if (exam.isTimerNone()) {
            binding.tvTimer.setVisibility(View.GONE);
            return;
        }

        examTimer = new CountDownTimer(remainingMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                binding.tvTimer.setText(getString(R.string.time_remaining, DateTimeUtils.formatRemainingTime(millisUntilFinished)));
                
                // If window passes while student is mid-exam
                if (exam.isTimerWindow() && DateTimeUtils.isAfterWindow(exam.windowEnd)) {
                    cancel();
                    showToast("Exam window has expired! Auto-submitting...");
                    autoSubmitExam();
                }
            }

            @Override
            public void onFinish() {
                binding.tvTimer.setText("Time Out!");
                showToast("Time limit reached! Auto-submitting...");
                autoSubmitExam();
            }
        }.start();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Proctoring Warnings & Telemetry
    // ══════════════════════════════════════════════════════════════════════

    private void startProctoringCamera() {
        if (exam == null || !exam.proctoringEnabled) {
            binding.cardProctorCamera.setVisibility(View.GONE);
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            proctoringManager = new FaceDetectionManager(this, this, binding.proctorPreview, this);
            proctoringManager.startProctoring();
        } else {
            // If no permission, we hide the proctoring card but telemetry logs will report it
            binding.cardProctorCamera.setVisibility(View.GONE);
        }
    }

    @Override
    public void onNoFaceDetected() {
        triggerProctoringWarning(ExamSession.WARN_FACE_NOT_DETECTED, getString(R.string.proctor_face_missing));
    }

    @Override
    public void onMultipleFacesDetected() {
        triggerProctoringWarning(ExamSession.WARN_MULTIPLE_FACES, getString(R.string.proctor_multiple_faces));
    }

    @Override
    public void onLookingAwayDetected() {
        triggerProctoringWarning(ExamSession.WARN_LOOKING_AWAY, getString(R.string.proctor_looking_away));
    }

    @Override
    public void onNormalState() {
        // Safe state, no alerts
    }

    private void triggerProctoringWarning(String warningType, String userMessage) {
        if (isWarningVisible || isSubmitting) return;
        if (System.currentTimeMillis() - lastWarningDismissedAt < WARNING_COOLDOWN_MS) return;

        isWarningVisible = true;
        warningCount++;

        // Add to log list
        warningsLog.add(new ExamSession.WarningEvent(warningType, DateTimeUtils.formatIso8601Now()));

        // Display warning modal overlay
        runOnUiThread(() -> {
            binding.tvWarningMessage.setText(userMessage);
            binding.layoutWarningOverlay.setVisibility(View.VISIBLE);
        });

        // Sync warning log immediately to supabase session table
        syncWarningToDatabase(warningType);
    }

    private void dismissProctoringWarning() {
        binding.layoutWarningOverlay.setVisibility(View.GONE);
        isWarningVisible = false;
        lastWarningDismissedAt = System.currentTimeMillis();
    }

    private void syncWarningToDatabase(String warningType) {
        Map<String, Object> patch = new HashMap<>();
        patch.put("warnings", warningCount);
        patch.put("warnings_log", warningsLog);
        patch.put("last_warning_type", warningType);
        patch.put("last_warning_at", DateTimeUtils.formatIso8601Now());

        studentRepository.updateExamSession(sessionId, patch, new StudentRepository.Callback<Void>() {
            @Override public void onSuccess(Void r) { Log.d(TAG, "Warning telemetry successfully updated."); }
            @Override public void onFailure(String error) { Log.e(TAG, "Failed updating warning logs: " + error); }
        });
    }

    // App Switch Detection
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // Triggered when student presses Home button or leaves the activity
        appSwitches++;
        triggerProctoringWarning(ExamSession.WARN_APP_SWITCH, getString(R.string.proctor_app_switch));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Autosaves & Local Cache (Phase 2 — Offline Resilience)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Saves the current answer state to Room (sync_status=PENDING), then
     * immediately attempts a server PATCH. On server success the Room row
     * is marked SYNCED; on failure it stays PENDING for Phase 3 retry.
     */
    private void saveAnswersToRoomThenServer() {
        if (isSubmitting) return;

        // Snapshot current state on the UI thread
        final List<Integer> answersSnapshot = new ArrayList<>(pagerAdapter.getStudentAnswers());
        final int currentQuestionIndex = binding.viewPager.getCurrentItem();
        final String answersJson = answersSnapshot.toString(); // e.g. "[-1, 2, 0, 1]"
        final long now = System.currentTimeMillis();

        // 1. Immediately write to Room on background thread (instant, no network)
        dbExecutor.execute(() -> {
            try {
                LocalExamAttempt attempt = new LocalExamAttempt();
                attempt.examId = examCode;
                attempt.enrollmentNumber = enrollmentNumber;
                attempt.currentAnswers = answersJson;
                attempt.lastQuestionIndex = currentQuestionIndex;
                attempt.localUpdatedAt = now;
                attempt.syncStatus = SyncStatus.PENDING;
                attempt.serverSessionId = sessionId;

                localAttemptDao.upsert(attempt);
                Log.d(TAG, "Room: answers cached locally (PENDING). Q" + currentQuestionIndex);
            } catch (Exception e) {
                Log.e(TAG, "Room: failed to cache answers locally", e);
            }
        });

        // 2. Immediately attempt server PATCH (existing online path)
        Map<String, Object> patch = new HashMap<>();
        patch.put("current_answers", answersSnapshot);
        patch.put("last_question_index", currentQuestionIndex);
        patch.put("last_heartbeat", DateTimeUtils.formatIso8601Now());

        studentRepository.updateExamSession(sessionId, patch, new StudentRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Log.d(TAG, "Server: answers synced successfully.");
                // 3. Mark Room row as SYNCED on background thread
                dbExecutor.execute(() -> {
                    try {
                        localAttemptDao.updateSyncStatus(
                                examCode, enrollmentNumber,
                                SyncStatus.SYNCED.name(), System.currentTimeMillis());
                        Log.d(TAG, "Room: sync_status updated to SYNCED.");
                    } catch (Exception e) {
                        Log.e(TAG, "Room: failed to update sync_status to SYNCED", e);
                    }
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                // 4. Leave sync_status=PENDING silently — Phase 3 WorkManager will retry
                Log.w(TAG, "Server: answer sync failed (will retry later): " + errorMessage);

                // Enqueue background worker as a fallback (Phase 3)
                try {
                    androidx.work.Constraints constraints = new androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                            .build();
                    androidx.work.OneTimeWorkRequest workRequest = new androidx.work.OneTimeWorkRequest.Builder(
                            com.arshad.studdy_app_android_only.work.SyncExamAttemptWorker.class)
                            .setConstraints(constraints)
                            .build();
                    androidx.work.WorkManager.getInstance(getApplicationContext()).enqueueUniqueWork(
                            "SyncExamAttemptWork",
                            androidx.work.ExistingWorkPolicy.REPLACE,
                            workRequest
                    );
                    Log.d(TAG, "WorkManager: enqueued SyncExamAttemptWorker on sync failure.");
                } catch (Exception e) {
                    Log.e(TAG, "WorkManager: failed to enqueue SyncExamAttemptWorker", e);
                }
            }
        });
    }

    /**
     * Periodic autosave: delegates to the Room-first-then-server pattern
     * and re-schedules itself.
     */
    private void triggerAutoSave() {
        if (isSubmitting) return;
        saveAnswersToRoomThenServer();
        autoSaveHandler.postDelayed(autoSaveRunnable, AUTOSAVE_INTERVAL_MS);
    }

    @Override
    public void onAnswerSelected(int questionIndex, int optionIndex) {
        // Trigger Room-first save instantly on every answer selection
        saveAnswersToRoomThenServer();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Submission & Calculation
    // ══════════════════════════════════════════════════════════════════════

    private void showSubmitConfirmationDialog() {
        new MaterialAlertDialogBuilder(this, R.style.Studdy_Dialog)
                .setTitle("Submit Exam?")
                .setMessage("Are you sure you want to submit? You cannot change your answers after this.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Submit", (d, w) -> submitExam())
                .show();
    }

    private void autoSubmitExam() {
        submitExam();
    }

    private void submitExam() {
        if (isSubmitting) return;
        isSubmitting = true;

        // Stop all timers and proctoring
        if (examTimer != null) examTimer.cancel();
        autoSaveHandler.removeCallbacks(autoSaveRunnable);
        if (proctoringManager != null) proctoringManager.stopProctoring();

        binding.layoutSubmissionLoader.setVisibility(View.VISIBLE);

        // Calculate Grade Score
        List<Question> questionsList = exam.questions;
        List<Integer> studentAnswers = pagerAdapter.getStudentAnswers();
        int score = 0;
        int total = questionsList.size();

        for (int i = 0; i < total; i++) {
            int selected = studentAnswers.get(i);
            int correct = questionsList.get(i).correctIndex;
            if (selected == correct) {
                score++;
            }
        }

        // Submit to Results table
        Result result = new Result();
        result.examCode = examCode;
        result.studentName = studentName;
        result.enrollmentNumber = enrollmentNumber;
        result.score = score;
        result.total = total;
        result.answers = studentAnswers;
        result.warnings = warningCount;
        result.appSwitches = appSwitches;
        result.createdAt = DateTimeUtils.formatIso8601Now();

        final int finalScore = score;

        studentRepository.submitResult(result, new StudentRepository.Callback<Result>() {
            @Override
            public void onSuccess(Result submittedResult) {
                // Update session state as submitted
                updateSessionAsSubmitted();
            }

            @Override
            public void onFailure(String errorMessage) {
                binding.layoutSubmissionLoader.setVisibility(View.GONE);
                isSubmitting = false;

                if ("DUPLICATE_ATTEMPT".equals(errorMessage)) {
                    // Gracefully show "already attempted" message
                    new MaterialAlertDialogBuilder(ExamActivity.this, R.style.Studdy_Dialog)
                            .setTitle("Already Attempted")
                            .setMessage(R.string.err_already_attempted)
                            .setPositiveButton(R.string.ok, (d, w) -> {
                                navigateToDashboard();
                            })
                            .setCancelable(false)
                            .show();
                } else {
                    String friendlyMsg = ErrorMessageMapper.toUserMessage(TAG, "Submission failed", errorMessage);
                    Toast.makeText(ExamActivity.this, friendlyMsg + " Retrying locally...", Toast.LENGTH_LONG).show();
                    // Allow retry
                }
            }
        });
    }

    private void updateSessionAsSubmitted() {
        Map<String, Object> patch = new HashMap<>();
        patch.put("status", "submitted");
        patch.put("submitted_at", DateTimeUtils.formatIso8601Now());

        studentRepository.updateExamSession(sessionId, patch, new StudentRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                navigateToResults();
            }

            @Override
            public void onFailure(String errorMessage) {
                // Navigate anyway since the result row has successfully committed
                navigateToResults();
            }
        });
    }

    /**
     * Safely stops lock task mode (screen pinning) and executes the given action
     * only AFTER the system confirms the window is fully un-pinned via
     * onWindowFocusChanged. This eliminates the race where finish()/startActivity()
     * fires while the window is still in a "stopped" pinned state, which causes
     * the system to kill the app process on vivo/Android 11+.
     *
     * If lock task was never started (e.g. permission not granted), the action
     * runs immediately to avoid a hang.
     */
    private void stopLockTaskAndExecute(Runnable action) {
        if (!isLockTaskActive) {
            // Never entered lock task — run action directly, no unlock needed
            action.run();
            return;
        }
        // Store action; onWindowFocusChanged will fire it once the system
        // confirms the activity window is fully unfrozen/unpinned.
        pendingPostLockTaskAction = action;
        isLockTaskActive = false;
        try {
            stopLockTask();
        } catch (Exception e) {
            Log.e(TAG, "stopLockTask failed", e);
            // Fallback: run action directly if stopLockTask itself threw
            pendingPostLockTaskAction = null;
            action.run();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // After stopLockTask() the system returns window focus to this activity
        // exactly once — that is our signal that the pin has been fully released
        // and it is safe to navigate away without a process kill.
        if (hasFocus && pendingPostLockTaskAction != null) {
            Runnable action = pendingPostLockTaskAction;
            pendingPostLockTaskAction = null;
            action.run();
        }
    }

    private void navigateToResults() {
        stopLockTaskAndExecute(() -> {
            Intent intent = new Intent(this, StudentResultActivity.class);
            intent.putExtra("exam_code", examCode);
            intent.putExtra("student_name", studentName);
            intent.putExtra("enrollment_number", enrollmentNumber);
            // Do NOT use CLEAR_TASK while still in lock task mode — it causes
            // the system to treat it as an illegal pinned-app exit and kill the process.
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void navigateToDashboard() {
        stopLockTaskAndExecute(() -> {
            Intent intent = new Intent(this, StudentDashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void showToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableDND();
        wallClockHandler.post(wallClockRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        wallClockHandler.removeCallbacks(wallClockRunnable);
        // Sync one last time on pause
        triggerAutoSave();
        restoreDND();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (examTimer != null) {
            examTimer.cancel();
        }
        autoSaveHandler.removeCallbacks(autoSaveRunnable);
        wallClockHandler.removeCallbacks(wallClockRunnable);
        if (proctoringManager != null) {
            proctoringManager.stopProctoring();
        }
        // Shut down the Room DB executor to prevent thread leaks
        dbExecutor.shutdown();
    }

    private void enableDND() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm.isNotificationPolicyAccessGranted()) {
            try {
                if (previousInterruptionFilter == -1) {
                    previousInterruptionFilter = nm.getCurrentInterruptionFilter();
                }
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
            } catch (Exception e) {
                Log.e(TAG, "Failed to enable DND", e);
            }
        }
    }

    private void restoreDND() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm.isNotificationPolicyAccessGranted()) {
            try {
                if (previousInterruptionFilter != -1) {
                    nm.setInterruptionFilter(previousInterruptionFilter);
                    previousInterruptionFilter = -1;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to restore DND", e);
            }
        }
    }

    private void showExitConfirmationDialog() {
        new MaterialAlertDialogBuilder(this, R.style.Studdy_Dialog)
                .setTitle("Exit Exam?")
                .setMessage("Are you sure you want to leave? Your progress is auto-saved, and you can resume the exam later as long as it remains active.")
                .setNegativeButton("Stay", null)
                .setPositiveButton("Exit", (d, w) -> {
                    stopLockTaskAndExecute(this::finish);
                })
                .show();
    }
}

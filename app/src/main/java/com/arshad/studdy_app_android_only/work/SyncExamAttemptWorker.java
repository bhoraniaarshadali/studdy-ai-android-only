package com.arshad.studdy_app_android_only.work;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.arshad.studdy_app_android_only.data.local.AppDatabase;
import com.arshad.studdy_app_android_only.data.local.dao.LocalExamAttemptDao;
import com.arshad.studdy_app_android_only.data.local.entity.LocalExamAttempt;
import com.arshad.studdy_app_android_only.data.local.entity.SyncStatus;
import com.arshad.studdy_app_android_only.data.model.ExamSession;
import com.arshad.studdy_app_android_only.data.repository.StudentRepository;
import com.arshad.studdy_app_android_only.util.DateTimeUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Response;

/**
 * System-level background sync worker.
 * Queries Room for PENDING/SUBMIT_PENDING attempts and updates Supabase.
 */
public class SyncExamAttemptWorker extends Worker {

    private static final String TAG = "SyncExamAttemptWorker";

    public SyncExamAttemptWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "SyncWorker: Starting background sync...");
        LocalExamAttemptDao dao = AppDatabase.getInstance(getApplicationContext()).localExamAttemptDao();
        StudentRepository repo = new StudentRepository();

        List<LocalExamAttempt> unsynced = dao.getUnsyncedAttempts();
        if (unsynced.isEmpty()) {
            Log.d(TAG, "SyncWorker: No unsynced attempts found.");
            return Result.success();
        }

        boolean hasError = false;
        Gson gson = new Gson();

        for (LocalExamAttempt attempt : unsynced) {
            Log.d(TAG, "SyncWorker: Syncing attempt for exam: " + attempt.examId 
                    + ", student: " + attempt.enrollmentNumber + " (status: " + attempt.syncStatus + ")");

            if (attempt.serverSessionId == null || attempt.serverSessionId == -1) {
                Log.w(TAG, "SyncWorker: serverSessionId is invalid for exam " + attempt.examId + ". Skipping.");
                continue;
            }

            try {
                // Deserialize answers from JSON string
                List<Integer> answersList = gson.fromJson(
                        attempt.currentAnswers,
                        new TypeToken<List<Integer>>() {}.getType()
                );

                // Build request payload
                Map<String, Object> patch = new HashMap<>();
                patch.put("current_answers", answersList);
                patch.put("last_question_index", attempt.lastQuestionIndex);
                patch.put("last_heartbeat", DateTimeUtils.formatIso8601(new Date(attempt.localUpdatedAt)));

                // Sync synchronously
                Response<List<ExamSession>> response = repo.updateExamSessionSync(attempt.serverSessionId, patch);

                if (response.isSuccessful()) {
                    dao.updateSyncStatus(
                            attempt.examId,
                            attempt.enrollmentNumber,
                            SyncStatus.SYNCED.name(),
                            System.currentTimeMillis()
                    );
                    Log.i(TAG, "SyncWorker: Successfully synced exam " + attempt.examId);
                } else {
                    int code = response.code();
                    if (code == 409 || code == 410) {
                        // Terminal rejection (exam closed or conflict)
                        dao.updateSyncStatus(
                                attempt.examId,
                                attempt.enrollmentNumber,
                                SyncStatus.CLOSED_REJECTED.name(),
                                System.currentTimeMillis()
                        );
                        Log.w(TAG, "SyncWorker: Sync rejected (terminal HTTP " + code + ") for exam " + attempt.examId);
                    } else {
                        // Transient network or server failure
                        hasError = true;
                        Log.w(TAG, "SyncWorker: Transient failure (HTTP " + code + ") for exam " + attempt.examId + ". Will retry.");
                        break;
                    }
                }
            } catch (Exception e) {
                hasError = true;
                Log.e(TAG, "SyncWorker: Error syncing exam " + attempt.examId, e);
                break;
            }
        }

        if (hasError) {
            return Result.retry();
        } else {
            return Result.success();
        }
    }
}

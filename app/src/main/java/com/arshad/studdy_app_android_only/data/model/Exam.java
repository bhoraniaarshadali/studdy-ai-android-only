package com.arshad.studdy_app_android_only.data.model;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps to a row in {@code public.exams}.
 *
 * The {@code questions} column is JSONB — Supabase returns it as a JSON array
 * directly inside the row object, so Gson deserialises it automatically into
 * {@code List<Question>}.
 */
public class Exam {

    @SerializedName("id")
    public String id;

    @SerializedName("code")
    public String code;

    @SerializedName("title")
    public String title;

    /** Populated automatically by Gson from the JSONB column. */
    @SerializedName("questions")
    public List<Question> questions;

    @SerializedName("created_at")
    public String createdAt;

    /** "instant" or "manual" */
    @SerializedName("result_mode")
    public String resultMode;

    @SerializedName("results_published")
    public boolean resultsPublished;

    /** "none", "duration", or "window" */
    @SerializedName("timer_mode")
    public String timerMode;

    @SerializedName("duration_minutes")
    public Integer durationMinutes;

    @SerializedName("window_start")
    public String windowStart;

    @SerializedName("window_end")
    public String windowEnd;

    @SerializedName("teacher_id")
    public String teacherId;

    @SerializedName("proctoring_enabled")
    public boolean proctoringEnabled = true;

    // ── Convenience helpers ────────────────────────────────────────────────

    public boolean isInstantResult() {
        return "instant".equals(resultMode);
    }

    public boolean isTimerNone() {
        return timerMode == null || "none".equals(timerMode);
    }

    public boolean isTimerDuration() {
        return "duration".equals(timerMode);
    }

    public boolean isTimerWindow() {
        return "window".equals(timerMode);
    }

    public int getQuestionCount() {
        return questions != null ? questions.size() : 0;
    }

    /**
     * Parses {@code questionsJson} manually — used when the API returns the
     * questions column as a raw JSON string rather than a parsed array.
     */
    public static List<Question> parseQuestions(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            Type listType = new TypeToken<List<Question>>() {}.getType();
            return new Gson().fromJson(json, listType);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}

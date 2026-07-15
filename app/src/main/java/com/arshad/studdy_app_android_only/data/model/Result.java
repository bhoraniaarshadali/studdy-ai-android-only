package com.arshad.studdy_app_android_only.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Maps to a row in {@code public.results}.
 *
 * NOTE: Live DB has a UNIQUE constraint on (exam_code, enrollment_number).
 * An HTTP 409 on POST means the student has already submitted.
 */
public class Result {

    @SerializedName("id")
    public String id;

    @SerializedName("exam_code")
    public String examCode;

    @SerializedName("student_name")
    public String studentName;

    @SerializedName("enrollment_number")
    public String enrollmentNumber;

    @SerializedName("score")
    public int score;

    @SerializedName("total")
    public int total;

    /** Student's selected answer indices — one per question, -1 = unanswered */
    @SerializedName("answers")
    public List<Integer> answers;

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("warnings")
    public int warnings;

    @SerializedName("app_switches")
    public int appSwitches;

    // ── Convenience ────────────────────────────────────────────────────────

    public double getPercentage() {
        if (total == 0) return 0;
        return (score * 100.0) / total;
    }
}

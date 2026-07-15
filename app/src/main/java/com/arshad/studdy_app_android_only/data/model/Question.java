package com.arshad.studdy_app_android_only.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Maps to a single MCQ question inside {@code exams.questions} (JSONB array).
 *
 * Live DB format:
 * {
 *   "id": "0",
 *   "questionText": "...",
 *   "options": ["A", "B", "C", "D"],
 *   "correctIndex": 0
 * }
 */
public class Question {

    @SerializedName("id")
    public String id;

    @SerializedName("questionText")
    public String questionText;

    @SerializedName("options")
    public List<String> options;

    @SerializedName("correctIndex")
    public Integer correctIndex;

    public Question() {}

    public Question(String id, String questionText, List<String> options, Integer correctIndex) {
        this.id = id;
        this.questionText = questionText;
        this.options = options;
        this.correctIndex = correctIndex;
    }
}

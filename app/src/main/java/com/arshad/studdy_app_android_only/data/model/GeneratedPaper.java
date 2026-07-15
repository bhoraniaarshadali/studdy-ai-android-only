package com.arshad.studdy_app_android_only.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Maps to a row in {@code public.generated_papers}.
 */
public class GeneratedPaper {

    @SerializedName("id")
    public Long id;                 // bigint — use Long so it is omitted when null

    @SerializedName("title")
    public String title;

    @SerializedName("total_marks")
    public int totalMarks;

    @SerializedName("template")
    public String template;

    @SerializedName("difficulty")
    public String difficulty;

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("teacher_id")
    public String teacherId;

    @SerializedName("pdf_url")
    public String pdfUrl;

    // JSONB columns can be stored as raw strings or objects
    @SerializedName("sections")
    public Object sections;

    @SerializedName("questions")
    public Object questions;

    @SerializedName("answer_key")
    public Object answerKey;
}

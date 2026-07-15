package com.arshad.studdy_app_android_only.data.model;

import java.util.List;

/**
 * Represents a section within an exam paper.
 * Each section defines question type, count, marks, and difficulty.
 */
public class PaperSection {
    public String id;                  // Unique ID for the section (e.g., "A", "B", "C")
    public String title;               // Display title (e.g., "Section A - MCQ")
    public String questionType;        // "MCQ", "SHORT_ANSWER", "LONG_ANSWER", "TRUE_FALSE", "FILL_BLANK"
    public int questionCount;          // Number of questions in this section
    public int marksPerQuestion;       // Marks allocated per question
    public int totalMarks;             // Total marks for this section (questionCount * marksPerQuestion)
    public String difficulty;          // "Easy", "Medium", "Hard"
    public List<Question> questions;   // Generated questions for this section

    public PaperSection() {}

    public PaperSection(String id, String title, String questionType, int questionCount, 
                       int marksPerQuestion, String difficulty) {
        this.id = id;
        this.title = title;
        this.questionType = questionType;
        this.questionCount = questionCount;
        this.marksPerQuestion = marksPerQuestion;
        this.totalMarks = questionCount * marksPerQuestion;
        this.difficulty = difficulty;
    }

    public void recalculateTotalMarks() {
        this.totalMarks = questionCount * marksPerQuestion;
    }
}

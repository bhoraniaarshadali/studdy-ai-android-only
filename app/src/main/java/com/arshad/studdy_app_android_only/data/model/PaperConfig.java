package com.arshad.studdy_app_android_only.data.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the complete configuration of a paper before generation.
 * Holds all sections, template, difficulty, and marks information.
 */
public class PaperConfig {
    public String title;                      // Paper title
    public String template;                   // Template type (e.g., "College Internal", "School Exam", "MCQ Only Test", "Unit Test")
    public String overallDifficulty;          // Overall difficulty: "Easy", "Balanced", "Tough"
    public int totalMarks;                    // Total marks for the entire paper
    public List<PaperSection> sections;       // List of paper sections
    public String pdfSource;                  // Source: "PDF" or "TEXT"
    public String pdfBase64;                  // Base64 encoded PDF content

    public PaperConfig() {
        this.sections = new ArrayList<>();
    }

    public int calculateTotalMarks() {
        int total = 0;
        for (PaperSection section : sections) {
            total += section.totalMarks;
        }
        return total;
    }

    public int getTotalQuestionCount() {
        int count = 0;
        for (PaperSection section : sections) {
            count += section.questionCount;
        }
        return count;
    }

    public void addSection(PaperSection section) {
        sections.add(section);
        updateTotalMarks();
    }

    public void removeSection(int index) {
        if (index >= 0 && index < sections.size()) {
            sections.remove(index);
            updateTotalMarks();
        }
    }

    public void updateTotalMarks() {
        this.totalMarks = calculateTotalMarks();
    }
}

package com.arshad.studdy_app_android_only.ui.teacher.papers;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.arshad.studdy_app_android_only.databinding.ActivityPaperDetailBinding;
import com.arshad.studdy_app_android_only.ui.BaseActivity;

/**
 * Printable paper details screen.
 * Shows metadata summary and triggers browser/PDF viewer intents.
 */
public class PaperDetailActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityPaperDetailBinding binding = ActivityPaperDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String title = getIntent().getStringExtra("paper_title");
        String pdfUrl = getIntent().getStringExtra("pdf_url");
        int marks = getIntent().getIntExtra("total_marks", 100);
        String template = getIntent().getStringExtra("template");
        String difficulty = getIntent().getStringExtra("difficulty");
        String dateStr = getIntent().getStringExtra("created_at");

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.tvPaperTitle.setText(title != null ? title : "Printable Exam Paper");
        binding.tvDetailMarks.setText("Total Marks: " + marks);
        binding.tvDetailTemplate.setText("Template: " + (template != null ? template : "Standard"));
        binding.tvDetailDifficulty.setText("Difficulty: " + (difficulty != null ? difficulty : "General"));
        
        if (dateStr != null) {
            String date = dateStr.substring(0, Math.min(dateStr.length(), 10));
            binding.tvDetailDate.setText("Created On: " + date);
        } else {
            binding.tvDetailDate.setText("Created On: N/A");
        }

        binding.btnOpenPdf.setOnClickListener(v -> {
            if (pdfUrl == null || pdfUrl.trim().isEmpty()) {
                Toast.makeText(this, "PDF URL is not available.", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(pdfUrl));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "No browser or PDF reader app found to open this document.", Toast.LENGTH_LONG).show();
            }
        });
    }
}

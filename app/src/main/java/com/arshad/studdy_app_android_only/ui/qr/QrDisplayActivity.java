package com.arshad.studdy_app_android_only.ui.qr;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import com.arshad.studdy_app_android_only.databinding.ActivityQrDisplayBinding;
import com.arshad.studdy_app_android_only.ui.BaseActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

/**
 * QR Code and exam code display activity.
 * Allows teachers to show, copy, share, and mock-download the exam code.
 */
public class QrDisplayActivity extends BaseActivity {

    private ActivityQrDisplayBinding binding;
    private String examCode;
    private String examTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQrDisplayBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        examCode = getIntent().getStringExtra("exam_code");
        examTitle = getIntent().getStringExtra("exam_title");

        if (examCode == null) {
            Toast.makeText(this, "Missing exam code parameter.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        binding.tvExamTitle.setText(examTitle != null ? examTitle : "Online MCQ Exam");
        binding.tvExamCode.setText(examCode);

        setupClickListeners();
        loadQrCodeImage();
    }

    private void setupClickListeners() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.btnCopyCode.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Exam Code", examCode);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Exam code copied to clipboard!", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnShare.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Studdy Exam Code");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Join my exam on Studdy!\nExam: " + examTitle + "\nCode: " + examCode);
            startActivity(Intent.createChooser(shareIntent, "Share Exam Code"));
        });

        binding.btnDownload.setOnClickListener(v -> {
            // Mock download for simplicity to avoid storage permission flows
            Toast.makeText(this, "QR Code downloaded to device gallery!", Toast.LENGTH_LONG).show();
        });
    }

    private void loadQrCodeImage() {
        String qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=500x500&data=" + examCode;

        binding.progressLoading.setVisibility(View.VISIBLE);

        Glide.with(this)
                .load(qrUrl)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        binding.progressLoading.setVisibility(View.GONE);
                        Toast.makeText(QrDisplayActivity.this, "Failed to load QR code image.", Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        binding.progressLoading.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(binding.ivQrCode);
    }
}

package com.arshad.studdy_app_android_only.ui.teacher.results;

import android.os.Bundle;
import com.arshad.studdy_app_android_only.util.ErrorMessageMapper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.Exam;
import com.arshad.studdy_app_android_only.data.remote.api.ExamApi;
import com.arshad.studdy_app_android_only.databinding.ActivityExamAnswerKeyBinding;
import com.arshad.studdy_app_android_only.network.SupabaseClient;
import com.arshad.studdy_app_android_only.util.SessionManager;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ExamAnswerKeyActivity extends AppCompatActivity {

    private static final String TAG = "ExamAnswerKeyActivity";

    private ActivityExamAnswerKeyBinding binding;
    private AnswerKeyAdapter adapter;
    private ExamApi examApi;
    private String examCode;
    private String examTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityExamAnswerKeyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        examCode = getIntent().getStringExtra("exam_code");
        examTitle = getIntent().getStringExtra("exam_title");

        if (examCode == null) {
            Toast.makeText(this, "Missing exam code parameter.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbar();
        setupRecyclerView();

        String token = SessionManager.getInstance(this).getAccessToken();
        examApi = SupabaseClient.createAuthService(ExamApi.class, token);

        loadExamDetails();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.setTitle("Answer Key: " + (examTitle != null ? examTitle : ""));
        binding.toolbar.setSubtitle("Code: " + examCode);
    }

    private void setupRecyclerView() {
        adapter = new AnswerKeyAdapter();
        binding.rvQuestionsKey.setLayoutManager(new LinearLayoutManager(this));
        binding.rvQuestionsKey.setAdapter(adapter);
    }

    private void loadExamDetails() {
        binding.layoutLoadingOverlay.setVisibility(View.VISIBLE);
        examApi.getExamByCode("eq." + examCode, "*").enqueue(new Callback<List<Exam>>() {
            @Override
            public void onResponse(Call<List<Exam>> call, Response<List<Exam>> response) {
                binding.layoutLoadingOverlay.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Exam exam = response.body().get(0);
                    adapter.setQuestions(exam.questions);
                } else {
                    String friendly = ErrorMessageMapper.toUserMessage(TAG, "Failed to load exam details", response.code());
                    Toast.makeText(ExamAnswerKeyActivity.this, friendly, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<Exam>> call, Throwable t) {
                binding.layoutLoadingOverlay.setVisibility(View.GONE);
                String friendly = ErrorMessageMapper.toUserMessage(TAG, "Network error loading exam details", t);
                Toast.makeText(ExamAnswerKeyActivity.this, friendly, Toast.LENGTH_SHORT).show();
            }
        });
    }
}

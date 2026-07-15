package com.arshad.studdy_app_android_only.ui.teacher.papers;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.GeneratedPaper;
import com.arshad.studdy_app_android_only.data.remote.api.PaperApi;
import com.arshad.studdy_app_android_only.databinding.ActivityPaperListBinding;
import com.arshad.studdy_app_android_only.network.SupabaseClient;
import com.arshad.studdy_app_android_only.ui.BaseActivity;
import com.arshad.studdy_app_android_only.util.SessionManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Printable papers library screen.
 * Displays teacher-generated downloadable multi-section exam papers.
 */
public class PaperListActivity extends BaseActivity implements PapersAdapter.OnPaperClickListener {

    private ActivityPaperListBinding binding;
    private PapersAdapter adapter;
    private PaperApi paperApi;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPaperListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = SessionManager.getInstance(this);

        setupToolbar();
        setupRecyclerView();

        String token = sessionManager.getAccessToken();
        paperApi = SupabaseClient.createAuthService(PaperApi.class, token);

        loadPapers(true);
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new PapersAdapter(this);
        binding.rvPapers.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPapers.setAdapter(adapter);

        binding.swipeRefresh.setOnRefreshListener(() -> loadPapers(false));
    }

    private void loadPapers(boolean showLoader) {
        if (showLoader) {
            binding.progressLoading.setVisibility(View.VISIBLE);
        }

        paperApi.getPapers("*", "created_at.desc").enqueue(new Callback<List<GeneratedPaper>>() {
            @Override
            public void onResponse(Call<List<GeneratedPaper>> call, Response<List<GeneratedPaper>> response) {
                binding.progressLoading.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    List<GeneratedPaper> papers = response.body();
                    if (papers.isEmpty()) {
                        binding.rvPapers.setVisibility(View.GONE);
                        binding.layoutEmptyState.setVisibility(View.VISIBLE);
                    } else {
                        binding.layoutEmptyState.setVisibility(View.GONE);
                        binding.rvPapers.setVisibility(View.VISIBLE);
                        adapter.setPapers(papers);
                    }
                } else {
                    Toast.makeText(PaperListActivity.this, "Failed to load papers: HTTP " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<GeneratedPaper>> call, Throwable t) {
                binding.progressLoading.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);
                Toast.makeText(PaperListActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onPaperClick(GeneratedPaper paper) {
        // Navigate to details
        Intent intent = new Intent(this, PaperDetailActivity.class);
        intent.putExtra("paper_title", paper.title);
        intent.putExtra("pdf_url", paper.pdfUrl);
        intent.putExtra("total_marks", paper.totalMarks);
        intent.putExtra("template", paper.template);
        intent.putExtra("difficulty", paper.difficulty);
        intent.putExtra("created_at", paper.createdAt);
        startActivity(intent);
    }

    @Override
    public void onPaperDelete(GeneratedPaper paper, int position) {
        new MaterialAlertDialogBuilder(this, R.style.Studdy_Dialog)
                .setTitle("Delete Paper")
                .setMessage("Are you sure you want to delete this printable paper record? This action cannot be undone.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Delete", (dialog, which) -> deletePaperRecord(paper.id, position))
                .show();
    }

    private void deletePaperRecord(long paperId, int position) {
        binding.progressLoading.setVisibility(View.VISIBLE);

        paperApi.deletePaper("eq." + paperId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                binding.progressLoading.setVisibility(View.GONE);
                if (response.isSuccessful()) {
                    Toast.makeText(PaperListActivity.this, "Paper deleted successfully.", Toast.LENGTH_SHORT).show();
                    adapter.getPapersList().remove(position);
                    adapter.notifyItemRemoved(position);

                    if (adapter.getPapersList().isEmpty()) {
                        binding.rvPapers.setVisibility(View.GONE);
                        binding.layoutEmptyState.setVisibility(View.VISIBLE);
                    }
                } else {
                    Toast.makeText(PaperListActivity.this, "Failed to delete paper: HTTP " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                binding.progressLoading.setVisibility(View.GONE);
                Toast.makeText(PaperListActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}

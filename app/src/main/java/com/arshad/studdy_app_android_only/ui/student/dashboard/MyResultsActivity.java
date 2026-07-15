package com.arshad.studdy_app_android_only.ui.student.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.Exam;
import com.arshad.studdy_app_android_only.data.model.Result;
import com.arshad.studdy_app_android_only.data.repository.StudentRepository;
import com.arshad.studdy_app_android_only.databinding.ActivityMyResultsBinding;
import com.arshad.studdy_app_android_only.databinding.ItemMyResultBinding;
import com.arshad.studdy_app_android_only.ui.BaseActivity;
import com.arshad.studdy_app_android_only.ui.student.result.StudentResultActivity;
import com.arshad.studdy_app_android_only.util.DateTimeUtils;
import com.arshad.studdy_app_android_only.util.SessionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MyResultsActivity extends BaseActivity {

    private ActivityMyResultsBinding binding;
    private StudentRepository studentRepository;
    private ResultsAdapter adapter;
    private String enrollmentNumber;

    private final List<Result> allResults = new ArrayList<>();
    private final Map<String, Exam> examsMap = new HashMap<>();
    private String selectedFilter = "All"; // "All", "Published", "Pending"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMyResultsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        studentRepository = new StudentRepository();
        enrollmentNumber = SessionManager.getInstance(this).getStudentEnrollment();

        if (enrollmentNumber == null || enrollmentNumber.isEmpty()) {
            Toast.makeText(this, "Session expired. Please log in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupRecyclerView();
        setupToolbar();
        setupFilters();
        setupSwipeRefresh();

        loadData(true);
    }

    private void setupRecyclerView() {
        adapter = new ResultsAdapter();
        binding.rvResults.setLayoutManager(new LinearLayoutManager(this));
        binding.rvResults.setAdapter(adapter);
    }

    private void setupToolbar() {
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener(() -> loadData(false));
    }

    private void setupFilters() {
        binding.chipAll.setOnClickListener(v -> {
            selectedFilter = "All";
            filterAndDisplay();
        });
        binding.chipPublished.setOnClickListener(v -> {
            selectedFilter = "Published";
            filterAndDisplay();
        });
        binding.chipPending.setOnClickListener(v -> {
            selectedFilter = "Pending";
            filterAndDisplay();
        });
    }

    private void loadData(boolean showRefreshLoader) {
        if (showRefreshLoader) {
            binding.swipeRefresh.setRefreshing(true);
        }

        // Fetch all exams first to match titles and publication settings
        studentRepository.getAllExams(new StudentRepository.Callback<List<Exam>>() {
            @Override
            public void onSuccess(List<Exam> exams) {
                examsMap.clear();
                if (exams != null) {
                    for (Exam exam : exams) {
                        examsMap.put(exam.code, exam);
                    }
                }
                loadResults();
            }

            @Override
            public void onFailure(String errorMessage) {
                binding.swipeRefresh.setRefreshing(false);
                Toast.makeText(MyResultsActivity.this, "Failed to load exams: " + errorMessage, Toast.LENGTH_SHORT).show();
                loadResults(); // attempt to load results anyway
            }
        });
    }

    private void loadResults() {
        studentRepository.getStudentResults(enrollmentNumber, new StudentRepository.Callback<List<Result>>() {
            @Override
            public void onSuccess(List<Result> results) {
                binding.swipeRefresh.setRefreshing(false);
                allResults.clear();
                if (results != null) {
                    allResults.addAll(results);
                }
                calculateStats();
                filterAndDisplay();
            }

            @Override
            public void onFailure(String errorMessage) {
                binding.swipeRefresh.setRefreshing(false);
                Toast.makeText(MyResultsActivity.this, "Failed to load results: " + errorMessage, Toast.LENGTH_SHORT).show();
                calculateStats();
                filterAndDisplay();
            }
        });
    }

    private void calculateStats() {
        int totalAttempts = allResults.size();
        int passedCount = 0;
        double sumPercentages = 0;
        int publishedCount = 0;

        for (Result r : allResults) {
            Exam exam = examsMap.get(r.examCode);
            boolean isPublished = isResultPublished(r, exam);

            if (isPublished) {
                publishedCount++;
                double pct = r.getPercentage();
                sumPercentages += pct;
                if (pct >= 50.0) {
                    passedCount++;
                }
            }
        }

        double avgPercentage = publishedCount > 0 ? (sumPercentages / publishedCount) : 0;

        binding.tvStatAttempts.setText(String.valueOf(totalAttempts));
        binding.tvStatPassed.setText(String.valueOf(passedCount));
        binding.tvStatAvg.setText(String.format(Locale.getDefault(), "%.0f%%", avgPercentage));
    }

    private boolean isResultPublished(Result result, Exam exam) {
        if (exam == null) return false;
        return "instant".equals(exam.resultMode) || exam.resultsPublished;
    }

    private void filterAndDisplay() {
        List<Result> filtered = new ArrayList<>();

        for (Result r : allResults) {
            Exam exam = examsMap.get(r.examCode);
            boolean isPublished = isResultPublished(r, exam);

            if ("All".equals(selectedFilter)) {
                filtered.add(r);
            } else if ("Published".equals(selectedFilter) && isPublished) {
                filtered.add(r);
            } else if ("Pending".equals(selectedFilter) && !isPublished) {
                filtered.add(r);
            }
        }

        adapter.setResults(filtered);

        if (filtered.isEmpty()) {
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
            binding.rvResults.setVisibility(View.GONE);
        } else {
            binding.layoutEmptyState.setVisibility(View.GONE);
            binding.rvResults.setVisibility(View.VISIBLE);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // RecyclerView Adapter
    // ══════════════════════════════════════════════════════════════════════

    private class ResultsAdapter extends RecyclerView.Adapter<ResultsAdapter.ViewHolder> {

        private final List<Result> results = new ArrayList<>();

        public void setResults(List<Result> newResults) {
            results.clear();
            if (newResults != null) {
                results.addAll(newResults);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemMyResultBinding itemBinding = ItemMyResultBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Result result = results.get(position);
            Exam exam = examsMap.get(result.examCode);
            holder.bind(result, exam);
        }

        @Override
        public int getItemCount() {
            return results.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ItemMyResultBinding itemBinding;

            ViewHolder(ItemMyResultBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;
            }

            void bind(Result result, Exam exam) {
                String examTitle = exam != null ? exam.title : "Unknown Exam";
                String friendlyDate = DateTimeUtils.formatFriendlyDateTime(result.createdAt);
                
                itemBinding.tvExamTitle.setText(examTitle);
                itemBinding.tvExamDetails.setText(String.format("Code: %s | %s", result.examCode, friendlyDate));

                boolean isPublished = isResultPublished(result, exam);

                if (isPublished) {
                    double percentage = result.getPercentage();
                    itemBinding.tvScorePercentage.setText(String.format(Locale.getDefault(), "%.0f%%", percentage));
                    itemBinding.scoreProgress.setProgress((int) percentage);

                    if (percentage >= 50.0) {
                        itemBinding.tvStatusBadge.setText("Passed");
                        itemBinding.tvStatusBadge.setBackgroundTintList(
                                getColorStateList(R.color.color_success_light));
                        itemBinding.tvStatusBadge.setTextColor(
                                getColor(R.color.color_success));
                        itemBinding.scoreProgress.setIndicatorColor(
                                getColor(R.color.color_success));
                    } else {
                        itemBinding.tvStatusBadge.setText("Failed");
                        itemBinding.tvStatusBadge.setBackgroundTintList(
                                getColorStateList(R.color.color_error_light));
                        itemBinding.tvStatusBadge.setTextColor(
                                getColor(R.color.color_error));
                        itemBinding.scoreProgress.setIndicatorColor(
                                getColor(R.color.color_error));
                    }
                    
                    itemBinding.btnViewDetails.setEnabled(true);
                    itemBinding.btnViewDetails.setOnClickListener(v -> {
                        Intent intent = new Intent(MyResultsActivity.this, StudentResultActivity.class);
                        intent.putExtra("exam_code", result.examCode);
                        intent.putExtra("student_name", result.studentName != null ? result.studentName : SessionManager.getInstance(MyResultsActivity.this).getStudentName());
                        intent.putExtra("enrollment_number", result.enrollmentNumber != null ? result.enrollmentNumber : enrollmentNumber);
                        startActivity(intent);
                    });
                } else {
                    itemBinding.tvScorePercentage.setText("Pending");
                    itemBinding.scoreProgress.setProgress(0);
                    itemBinding.scoreProgress.setIndicatorColor(getColor(R.color.color_warning));

                    itemBinding.tvStatusBadge.setText("Pending");
                    itemBinding.tvStatusBadge.setBackgroundTintList(
                            getColorStateList(R.color.color_warning_light));
                    itemBinding.tvStatusBadge.setTextColor(
                            getColor(R.color.color_warning));

                    itemBinding.btnViewDetails.setEnabled(false);
                    itemBinding.btnViewDetails.setOnClickListener(v -> 
                        Toast.makeText(MyResultsActivity.this, "Results are pending and not yet published.", Toast.LENGTH_SHORT).show()
                    );
                }
            }
        }
    }
}

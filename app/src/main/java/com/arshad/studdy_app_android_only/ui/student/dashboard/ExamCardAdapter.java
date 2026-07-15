package com.arshad.studdy_app_android_only.ui.student.dashboard;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.Exam;
import com.arshad.studdy_app_android_only.databinding.ItemStudentExamCardBinding;
import com.arshad.studdy_app_android_only.util.DateTimeUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExamCardAdapter extends RecyclerView.Adapter<ExamCardAdapter.ViewHolder> {

    public interface OnExamClickListener {
        void onExamClick(Exam exam);
    }

    private final List<Exam> examsList = new ArrayList<>();
    private final Set<String> attemptedExamCodes = new HashSet<>();
    private boolean isUpcomingTab = false;
    private final OnExamClickListener clickListener;

    public ExamCardAdapter(OnExamClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void setExams(List<Exam> exams, Set<String> attempted, boolean isUpcoming) {
        examsList.clear();
        if (exams != null) {
            examsList.addAll(exams);
        }
        attemptedExamCodes.clear();
        if (attempted != null) {
            attemptedExamCodes.addAll(attempted);
        }
        this.isUpcomingTab = isUpcoming;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStudentExamCardBinding binding = ItemStudentExamCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(examsList.get(position));
    }

    @Override
    public int getItemCount() {
        return examsList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemStudentExamCardBinding binding;

        ViewHolder(ItemStudentExamCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Exam exam) {
            binding.tvExamTitle.setText(exam.title);
            binding.tvExamCode.setText(exam.code);
            
            // Question count
            int qCount = exam.getQuestionCount();
            binding.tvQuestionCount.setText(binding.getRoot().getContext().getString(R.string.questions_count, qCount));

            // Timer display
            if (exam.isTimerDuration()) {
                binding.tvTimerInfo.setText(exam.durationMinutes + " mins");
            } else if (exam.isTimerWindow()) {
                binding.tvTimerInfo.setText("Window Active");
            } else {
                binding.tvTimerInfo.setText("No Timer");
            }

            // Attempt status checks
            boolean attempted = attemptedExamCodes.contains(exam.code);
            boolean isWindowClosed = exam.isTimerWindow() && DateTimeUtils.isAfterWindow(exam.windowEnd);

            // Styling card, left status strip, and status badge/action pill
            int colorStrip;
            int badgeBgColor;
            int badgeTextColor;
            String badgeText;
            boolean isBtnEnabled;

            if (isUpcomingTab) {
                // Upcoming exam (not attempted yet)
                colorStrip = ContextCompatColor(R.color.brand_primary);
                badgeBgColor = ContextCompatColor(R.color.brand_primary);
                badgeTextColor = ContextCompatColor(R.color.white);
                badgeText = "Start Exam";
                isBtnEnabled = true;
            } else {
                // Ongoing tab
                if (attempted) {
                    colorStrip = ContextCompatColor(R.color.text_hint);
                    badgeBgColor = ContextCompatColor(R.color.divider);
                    badgeTextColor = ContextCompatColor(R.color.text_secondary);
                    badgeText = "Submitted";
                    isBtnEnabled = false;
                } else if (isWindowClosed) {
                    colorStrip = ContextCompatColor(R.color.text_hint);
                    badgeBgColor = ContextCompatColor(R.color.divider);
                    badgeTextColor = ContextCompatColor(R.color.text_secondary);
                    badgeText = "Window Closed";
                    isBtnEnabled = false;
                } else {
                    colorStrip = ContextCompatColor(R.color.brand_primary);
                    badgeBgColor = ContextCompatColor(R.color.brand_primary);
                    badgeTextColor = ContextCompatColor(R.color.white);
                    badgeText = "Start Exam";
                    isBtnEnabled = true;
                }
            }

            binding.viewStatusStrip.setBackgroundColor(colorStrip);
            binding.tvStatusBadge.setText(badgeText);
            binding.tvStatusBadge.setEnabled(isBtnEnabled);
            binding.tvStatusBadge.setTextColor(badgeTextColor);
            binding.tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(badgeBgColor));

            if (attempted) {
                binding.getRoot().setOnClickListener(null);
                binding.getRoot().setClickable(false);
            } else {
                binding.getRoot().setClickable(true);
                binding.getRoot().setOnClickListener(v -> {
                    if (clickListener != null) {
                        clickListener.onExamClick(exam);
                    }
                });
            }
        }

        private int ContextCompatColor(int colorRes) {
            return ContextCompat.getColor(binding.getRoot().getContext(), colorRes);
        }
    }
}

package com.arshad.studdy_app_android_only.ui.teacher.dashboard;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.Exam;
import com.arshad.studdy_app_android_only.databinding.ItemTeacherExamCardBinding;
import com.arshad.studdy_app_android_only.util.DateTimeUtils;

import java.util.ArrayList;
import java.util.List;

public class TeacherExamAdapter extends RecyclerView.Adapter<TeacherExamAdapter.ViewHolder> {

    public interface OnExamClickListener {
        void onExamClick(Exam exam);
    }

    private final List<Exam> examsList = new ArrayList<>();
    private final OnExamClickListener clickListener;

    public TeacherExamAdapter(OnExamClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void setExams(List<Exam> exams) {
        examsList.clear();
        if (exams != null) {
            examsList.addAll(exams);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTeacherExamCardBinding binding = ItemTeacherExamCardBinding.inflate(
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
        private final ItemTeacherExamCardBinding binding;

        ViewHolder(ItemTeacherExamCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Exam exam) {
            binding.tvExamTitle.setText(exam.title);
            binding.tvExamCode.setText(exam.code);
            
            // Timer display
            if (exam.isTimerDuration()) {
                binding.tvTimerInfo.setText(exam.durationMinutes + " Min Limit");
            } else if (exam.isTimerWindow()) {
                binding.tvTimerInfo.setText("Window Active");
            } else {
                binding.tvTimerInfo.setText("No Timer");
            }

            // Result Mode Pill
            if (exam.isInstantResult()) {
                binding.tvResultMode.setText("INSTANT");
                binding.tvResultMode.setBackgroundTintList(ContextCompat.getColorStateList(binding.getRoot().getContext(), R.color.color_info_light));
                binding.tvResultMode.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.color_info));
            } else {
                binding.tvResultMode.setText("MANUAL");
                binding.tvResultMode.setBackgroundTintList(ContextCompat.getColorStateList(binding.getRoot().getContext(), R.color.color_warning_light));
                binding.tvResultMode.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.color_warning));
            }

            // Publish Status Pill
            if (exam.resultsPublished) {
                binding.tvPublishStatus.setText("PUBLISHED");
                binding.tvPublishStatus.setBackgroundTintList(ContextCompat.getColorStateList(binding.getRoot().getContext(), R.color.color_success_light));
                binding.tvPublishStatus.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.color_success));
            } else {
                binding.tvPublishStatus.setText("HIDDEN");
                binding.tvPublishStatus.setBackgroundTintList(ContextCompat.getColorStateList(binding.getRoot().getContext(), R.color.brand_primary_surface));
                binding.tvPublishStatus.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.text_secondary));
            }

            // Date
            if (exam.createdAt != null) {
                // Assuming createdAt is ISO8601
                binding.tvDate.setText(DateTimeUtils.formatDateOnly(exam.createdAt));
            } else {
                binding.tvDate.setText("N/A");
            }

            // Student Count (Mocked for now as it's not in the model)
            binding.tvStudentCount.setText("0 Students");

            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onExamClick(exam);
                }
            });
        }
    }
}

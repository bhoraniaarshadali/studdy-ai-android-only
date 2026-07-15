package com.arshad.studdy_app_android_only.ui.teacher.results;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.Result;
import com.arshad.studdy_app_android_only.databinding.ItemStudentResultBinding;

import java.util.ArrayList;
import java.util.List;

public class StudentResultsAdapter extends RecyclerView.Adapter<StudentResultsAdapter.ViewHolder> {

    public interface OnResultClickListener {
        void onResultClick(Result result);
    }

    private final List<Result> originalList = new ArrayList<>();
    private final List<Result> filteredList = new ArrayList<>();
    private OnResultClickListener clickListener;

    public void setOnResultClickListener(OnResultClickListener listener) {
        this.clickListener = listener;
    }

    public void setResults(List<Result> results) {
        originalList.clear();
        filteredList.clear();
        if (results != null) {
            originalList.addAll(results);
            filteredList.addAll(results);
        }
        notifyDataSetChanged();
    }

    public void filter(String query) {
        filteredList.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredList.addAll(originalList);
        } else {
            String lower = query.toLowerCase().trim();
            for (Result r : originalList) {
                if ((r.studentName != null && r.studentName.toLowerCase().contains(lower)) ||
                    (r.enrollmentNumber != null && r.enrollmentNumber.contains(lower))) {
                    filteredList.add(r);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStudentResultBinding binding = ItemStudentResultBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(filteredList.get(position));
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemStudentResultBinding binding;

        ViewHolder(ItemStudentResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        clickListener.onResultClick(filteredList.get(pos));
                    }
                }
            });
        }

        void bind(Result result) {
            binding.tvStudentName.setText(result.studentName);
            binding.tvStudentEnrollment.setText("Enrollment: " + result.enrollmentNumber);
            binding.tvStudentScore.setText(result.score + " / " + result.total);

            // Warning Badge styling
            if (result.warnings > 0) {
                binding.tvWarningBadge.setVisibility(View.VISIBLE);
                binding.tvWarningBadge.setText(result.warnings + " warnings");
            } else {
                binding.tvWarningBadge.setVisibility(View.GONE);
            }

            // Switch Badge styling
            if (result.appSwitches > 0) {
                binding.tvSwitchBadge.setVisibility(View.VISIBLE);
                binding.tvSwitchBadge.setText(result.appSwitches + " switches");
            } else {
                binding.tvSwitchBadge.setVisibility(View.GONE);
            }
        }
    }
}

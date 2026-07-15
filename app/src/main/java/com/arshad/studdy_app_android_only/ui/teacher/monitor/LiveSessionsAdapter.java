package com.arshad.studdy_app_android_only.ui.teacher.monitor;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.ExamSession;
import com.arshad.studdy_app_android_only.databinding.ItemLiveSessionBinding;
import com.arshad.studdy_app_android_only.util.DateTimeUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class LiveSessionsAdapter extends RecyclerView.Adapter<LiveSessionsAdapter.ViewHolder> {

    private final List<ExamSession> sessionsList = new ArrayList<>();

    public void setSessions(List<ExamSession> sessions) {
        sessionsList.clear();
        if (sessions != null) {
            sessionsList.addAll(sessions);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLiveSessionBinding binding = ItemLiveSessionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(sessionsList.get(position));
    }

    @Override
    public int getItemCount() {
        return sessionsList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemLiveSessionBinding binding;

        ViewHolder(ItemLiveSessionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ExamSession session) {
            binding.tvStudentName.setText(session.studentName);
            binding.tvStudentEnrollment.setText("Enrollment: " + session.enrollmentNumber);
            binding.tvProgressValue.setText("Q" + (session.lastQuestionIndex + 1));

            Context context = binding.getRoot().getContext();

            // Warnings count
            if (session.warnings > 0) {
                binding.tvWarningsValue.setVisibility(View.VISIBLE);
                binding.tvWarningsValue.setText(session.warnings + " warnings");
            } else {
                binding.tvWarningsValue.setVisibility(View.GONE);
            }

            // Determine active/submitted status coloring and descriptions
            if ("submitted".equals(session.status)) {
                binding.viewStatusDot.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.color_success)));
                binding.tvLastSeen.setText("Exam Submitted");
            } else {
                // Active session — calculate heartbeat timeout
                Date lastSeenDate = DateTimeUtils.parseIso8601(session.lastHeartbeat != null ? session.lastHeartbeat : session.joinedAt);
                long lastSeenMs = lastSeenDate != null ? lastSeenDate.getTime() : 0;
                long elapsed = System.currentTimeMillis() - lastSeenMs;

                if (elapsed <= 12000) { // Heartbeat every 10s, give 2s buffer
                    binding.viewStatusDot.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.brand_secondary)));
                    long seconds = elapsed / 1000;
                    binding.tvLastSeen.setText("Active " + (seconds <= 2 ? "now" : seconds + "s ago"));
                } else {
                    // Offline / inactive
                    binding.viewStatusDot.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_hint)));
                    long minutes = elapsed / (60 * 1000);
                    if (minutes < 1) {
                        binding.tvLastSeen.setText("Offline (Last active " + (elapsed / 1000) + "s ago)");
                    } else {
                        binding.tvLastSeen.setText("Offline (Last active " + minutes + "m ago)");
                    }
                }
            }
        }
    }
}

package com.arshad.studdy_app_android_only.ui.teacher.papers;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.arshad.studdy_app_android_only.data.model.GeneratedPaper;
import com.arshad.studdy_app_android_only.databinding.ItemPaperCardBinding;

import java.util.ArrayList;
import java.util.List;

public class PapersAdapter extends RecyclerView.Adapter<PapersAdapter.ViewHolder> {

    public interface OnPaperClickListener {
        void onPaperClick(GeneratedPaper paper);
        void onPaperDelete(GeneratedPaper paper, int position);
    }

    private final List<GeneratedPaper> papersList = new ArrayList<>();
    private final OnPaperClickListener listener;

    public PapersAdapter(OnPaperClickListener listener) {
        this.listener = listener;
    }

    public void setPapers(List<GeneratedPaper> papers) {
        papersList.clear();
        if (papers != null) {
            papersList.addAll(papers);
        }
        notifyDataSetChanged();
    }

    public List<GeneratedPaper> getPapersList() {
        return papersList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPaperCardBinding binding = ItemPaperCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(papersList.get(position), position);
    }

    @Override
    public int getItemCount() {
        return papersList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemPaperCardBinding binding;

        ViewHolder(ItemPaperCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(GeneratedPaper paper, int position) {
            binding.tvPaperTitle.setText(paper.title != null ? paper.title : "Untitled Paper");
            
            String diff = paper.difficulty != null ? paper.difficulty : "General";
            String temp = paper.template != null ? paper.template : "Standard";
            binding.tvPaperSubtitle.setText("Marks: " + paper.totalMarks + " • " + temp + " • " + diff);

            // Format date if present
            if (paper.createdAt != null) {
                // simple truncate to show date portion
                String date = paper.createdAt.substring(0, Math.min(paper.createdAt.length(), 10));
                binding.tvPaperDate.setText("Created: " + date);
            } else {
                binding.tvPaperDate.setText("Created: N/A");
            }

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPaperClick(paper);
                }
            });

            binding.btnDeletePaper.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPaperDelete(paper, getAdapterPosition());
                }
            });
        }
    }
}

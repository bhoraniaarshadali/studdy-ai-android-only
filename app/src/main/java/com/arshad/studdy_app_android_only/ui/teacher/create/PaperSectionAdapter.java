package com.arshad.studdy_app_android_only.ui.teacher.create;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.PaperSection;

import java.util.ArrayList;
import java.util.List;

public class PaperSectionAdapter extends RecyclerView.Adapter<PaperSectionAdapter.ViewHolder> {

    public interface SectionActions {
        void onDeleteSection(int position);
    }

    private final List<PaperSection> sections = new ArrayList<>();
    private final SectionActions actions;

    public PaperSectionAdapter(SectionActions actions) {
        this.actions = actions;
    }

    public void setSections(List<PaperSection> list) {
        sections.clear();
        if (list != null) {
            sections.addAll(list);
        }
        notifyDataSetChanged();
    }

    public List<PaperSection> getSections() {
        return sections;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_paper_section, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(sections.get(position), position);
    }

    @Override
    public int getItemCount() {
        return sections.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView subtitle;
        private final TextView typeTag;
        private final TextView difficultyTag;
        private final android.widget.ImageView icon;
        private final ImageButton deleteButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_section_title);
            subtitle = itemView.findViewById(R.id.tv_section_subtitle);
            typeTag = itemView.findViewById(R.id.tv_section_type_tag);
            difficultyTag = itemView.findViewById(R.id.tv_section_diff_tag);
            icon = itemView.findViewById(R.id.iv_section_icon);
            deleteButton = itemView.findViewById(R.id.btn_delete_section);
        }

        void bind(PaperSection section, int position) {
            title.setText(section.title);
            subtitle.setText(section.questionCount + " questions × " + section.marksPerQuestion + " marks = " + section.totalMarks + " marks");
            
            String typeDisplay = section.questionType.replace("_", " ");
            typeTag.setText(typeDisplay.toUpperCase());
            difficultyTag.setText(section.difficulty.toUpperCase());

            // Set icon and colors based on type
            if (section.questionType.contains("MCQ")) {
                icon.setImageResource(R.drawable.ic_mcq_exam);
                typeTag.setTextColor(android.graphics.Color.parseColor("#2196F3"));
                typeTag.getBackground().setTint(android.graphics.Color.parseColor("#E3F2FD"));
            } else if (section.questionType.contains("SHORT")) {
                icon.setImageResource(R.drawable.ic_grid);
                typeTag.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
                typeTag.getBackground().setTint(android.graphics.Color.parseColor("#F1F8E9"));
            } else {
                icon.setImageResource(R.drawable.ic_book);
                typeTag.setTextColor(android.graphics.Color.parseColor("#9C27B0"));
                typeTag.getBackground().setTint(android.graphics.Color.parseColor("#F3E5F5"));
            }

            // Set difficulty tag color
            if ("Easy".equalsIgnoreCase(section.difficulty)) {
                difficultyTag.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
                difficultyTag.getBackground().setTint(android.graphics.Color.parseColor("#F1F8E9"));
            } else if ("Hard".equalsIgnoreCase(section.difficulty) || "Tough".equalsIgnoreCase(section.difficulty)) {
                difficultyTag.setTextColor(android.graphics.Color.parseColor("#F44336"));
                difficultyTag.getBackground().setTint(android.graphics.Color.parseColor("#FFEBEE"));
            } else {
                difficultyTag.setTextColor(android.graphics.Color.parseColor("#FF9800"));
                difficultyTag.getBackground().setTint(android.graphics.Color.parseColor("#FFF3E0"));
            }

            deleteButton.setOnClickListener(v -> {
                if (actions != null) {
                    actions.onDeleteSection(getAdapterPosition());
                }
            });
        }
    }
}

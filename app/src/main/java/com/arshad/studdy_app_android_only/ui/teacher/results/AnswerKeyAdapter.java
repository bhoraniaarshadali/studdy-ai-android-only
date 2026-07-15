package com.arshad.studdy_app_android_only.ui.teacher.results;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.Question;

import java.util.ArrayList;
import java.util.List;

public class AnswerKeyAdapter extends RecyclerView.Adapter<AnswerKeyAdapter.ViewHolder> {

    private final List<Question> questions = new ArrayList<>();

    public void setQuestions(List<Question> newQuestions) {
        questions.clear();
        if (newQuestions != null) {
            questions.addAll(newQuestions);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_review_question, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Question q = questions.get(position);
        holder.bind(q, position);
    }

    @Override
    public int getItemCount() {
        return questions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvQuestionText;
        private final TextView tvOption0;
        private final TextView tvOption1;
        private final TextView tvOption2;
        private final TextView tvOption3;
        private final ImageView ivQuestionStatus;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvQuestionText = itemView.findViewById(R.id.tv_review_question_text);
            tvOption0 = itemView.findViewById(R.id.tv_review_option_0);
            tvOption1 = itemView.findViewById(R.id.tv_review_option_1);
            tvOption2 = itemView.findViewById(R.id.tv_review_option_2);
            tvOption3 = itemView.findViewById(R.id.tv_review_option_3);
            ivQuestionStatus = itemView.findViewById(R.id.iv_question_status);
        }

        public void bind(Question q, int index) {
            tvQuestionText.setText("Q" + (index + 1) + ". " + q.questionText);
            
            // Hide the status icon (correct/incorrect student icon)
            if (ivQuestionStatus != null) {
                ivQuestionStatus.setVisibility(View.GONE);
            }

            TextView[] optionViews = {tvOption0, tvOption1, tvOption2, tvOption3};

            for (int i = 0; i < optionViews.length; i++) {
                TextView optionView = optionViews[i];
                if (optionView == null) continue;

                if (q.options != null && i < q.options.size()) {
                    optionView.setVisibility(View.VISIBLE);
                    String label = String.valueOf((char) ('A' + i));
                    optionView.setText(label + ") " + q.options.get(i));

                    // Highlight correct answer in green, rest default background
                    if (i == q.correctIndex) {
                        optionView.setBackgroundResource(R.drawable.bg_option_correct);
                    } else {
                        optionView.setBackgroundResource(R.drawable.bg_option_default);
                    }
                } else {
                    optionView.setVisibility(View.GONE);
                }
            }
        }
    }
}

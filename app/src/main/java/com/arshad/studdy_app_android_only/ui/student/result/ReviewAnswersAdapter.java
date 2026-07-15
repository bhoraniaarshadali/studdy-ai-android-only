package com.arshad.studdy_app_android_only.ui.student.result;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.Question;
import com.arshad.studdy_app_android_only.databinding.ItemReviewQuestionBinding;

import java.util.List;

public class ReviewAnswersAdapter extends RecyclerView.Adapter<ReviewAnswersAdapter.ViewHolder> {

    private final List<Question> questions;
    private final List<Integer> studentAnswers;

    public ReviewAnswersAdapter(List<Question> questions, List<Integer> studentAnswers) {
        this.questions = questions;
        this.studentAnswers = studentAnswers;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReviewQuestionBinding binding = ItemReviewQuestionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(questions.get(position), position);
    }

    @Override
    public int getItemCount() {
        return questions != null ? questions.size() : 0;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemReviewQuestionBinding binding;
        private final TextView[] optionViews;

        ViewHolder(ItemReviewQuestionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            optionViews = new TextView[] {
                    binding.tvReviewOption0,
                    binding.tvReviewOption1,
                    binding.tvReviewOption2,
                    binding.tvReviewOption3
            };
        }

        void bind(Question question, int position) {
            Integer selectedAnswerObj = (studentAnswers != null && position < studentAnswers.size())
                    ? studentAnswers.get(position) : null;
            Integer correctIndexObj = question != null ? question.correctIndex : null;

            if (selectedAnswerObj == null || selectedAnswerObj == -1) {
                binding.tvReviewQuestionText.setText("Q" + (position + 1) + ". " + (question != null ? question.questionText : "") + " (Not Answered)");
            } else {
                binding.tvReviewQuestionText.setText("Q" + (position + 1) + ". " + (question != null ? question.questionText : ""));
            }

            Context context = binding.getRoot().getContext();

            if (correctIndexObj == null) {
                binding.ivQuestionStatus.setVisibility(ViewGroup.GONE);
            } else {
                binding.ivQuestionStatus.setVisibility(ViewGroup.VISIBLE);
                if (selectedAnswerObj != null && selectedAnswerObj.equals(correctIndexObj)) {
                    binding.ivQuestionStatus.setImageResource(R.drawable.ic_check);
                } else {
                    binding.ivQuestionStatus.setImageResource(R.drawable.ic_cross);
                }
            }

            for (int i = 0; i < 4; i++) {
                if (question != null && question.options != null && i < question.options.size()) {
                    optionViews[i].setVisibility(ViewGroup.VISIBLE);
                    String label = getOptionPrefix(i) + question.options.get(i);
                    optionViews[i].setText(label);

                    // Styling based on correctness
                    if (correctIndexObj != null && i == (int) correctIndexObj) {
                        // Highlight correct answer in green
                        optionViews[i].setBackgroundResource(R.drawable.bg_option_correct);
                        optionViews[i].setTextColor(ContextCompat.getColor(context, R.color.color_success));
                        if (selectedAnswerObj != null && i == (int) selectedAnswerObj) {
                            optionViews[i].setText(label + " ✓ (Your Answer)");
                        } else {
                            optionViews[i].setText(label + " ✓ (Correct)");
                        }
                    } else if (selectedAnswerObj != null && i == (int) selectedAnswerObj) {
                        // Highlight student's incorrect selection in red
                        optionViews[i].setBackgroundResource(R.drawable.bg_option_incorrect);
                        optionViews[i].setTextColor(ContextCompat.getColor(context, R.color.color_error));
                        optionViews[i].setText(label + " ✗ (Your Answer)");
                    } else {
                        // Default state
                        optionViews[i].setBackgroundResource(R.drawable.bg_option_default);
                        optionViews[i].setTextColor(ContextCompat.getColor(context, R.color.text_primary));
                    }
                } else {
                    optionViews[i].setVisibility(ViewGroup.GONE);
                }
            }
        }

        private String getOptionPrefix(int index) {
            switch (index) {
                case 0: return "A) ";
                case 1: return "B) ";
                case 2: return "C) ";
                case 3: return "D) ";
                default: return "";
            }
        }
    }
}

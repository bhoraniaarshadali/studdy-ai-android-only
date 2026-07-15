package com.arshad.studdy_app_android_only.ui.student.exam;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.Question;
import com.arshad.studdy_app_android_only.databinding.ItemQuestionBinding;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class QuestionPagerAdapter extends RecyclerView.Adapter<QuestionPagerAdapter.QuestionViewHolder> {

    public interface OnAnswerSelectedListener {
        void onAnswerSelected(int questionIndex, int optionIndex);
    }

    private final List<Question> questions = new ArrayList<>();
    private final List<Integer> studentAnswers = new ArrayList<>();
    private final OnAnswerSelectedListener answerListener;

    public QuestionPagerAdapter(OnAnswerSelectedListener answerListener) {
        this.answerListener = answerListener;
    }

    public void setData(List<Question> newQuestions, List<Integer> currentAnswers) {
        questions.clear();
        studentAnswers.clear();
        if (newQuestions != null) {
            questions.addAll(newQuestions);
        }
        if (currentAnswers != null) {
            studentAnswers.addAll(currentAnswers);
        } else {
            for (int i = 0; i < questions.size(); i++) {
                studentAnswers.add(-1);
            }
        }
        notifyDataSetChanged();
    }

    public List<Integer> getStudentAnswers() {
        return studentAnswers;
    }

    @NonNull
    @Override
    public QuestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemQuestionBinding binding = ItemQuestionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new QuestionViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull QuestionViewHolder holder, int position) {
        holder.bind(questions.get(position), position);
    }

    @Override
    public int getItemCount() {
        return questions.size();
    }

    class QuestionViewHolder extends RecyclerView.ViewHolder {
        private final ItemQuestionBinding binding;
        private final MaterialCardView[] optionCards;
        private final TextView[] optionLabels;
        private final TextView[] optionTexts;

        QuestionViewHolder(ItemQuestionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            optionCards = new MaterialCardView[] {
                    binding.cardOption0,
                    binding.cardOption1,
                    binding.cardOption2,
                    binding.cardOption3
            };

            optionLabels = new TextView[] {
                    binding.tvOptionLabel0,
                    binding.tvOptionLabel1,
                    binding.tvOptionLabel2,
                    binding.tvOptionLabel3
            };

            optionTexts = new TextView[] {
                    binding.tvOptionText0,
                    binding.tvOptionText1,
                    binding.tvOptionText2,
                    binding.tvOptionText3
            };
        }

        void bind(Question question, int questionIndex) {
            binding.tvQuestionText.setText(question.questionText);

            // Bind options text (MCQs have up to 4 options)
            for (int i = 0; i < 4; i++) {
                if (i < question.options.size()) {
                    optionCards[i].setVisibility(View.VISIBLE);
                    optionTexts[i].setText(question.options.get(i));
                    
                    final int optionIdx = i;
                    optionCards[i].setOnClickListener(v -> {
                        selectOption(questionIndex, optionIdx);
                    });
                } else {
                    optionCards[i].setVisibility(View.GONE);
                }
            }

            // Restore previously selected option styling
            int selectedAnswer = studentAnswers.get(questionIndex);
            updateOptionStyling(selectedAnswer);
        }

        private void selectOption(int questionIndex, int optionIdx) {
            // Update local memory list
            studentAnswers.set(questionIndex, optionIdx);
            
            // Update styling immediately
            updateOptionStyling(optionIdx);

            // Notify parent ExamActivity
            if (answerListener != null) {
                answerListener.onAnswerSelected(questionIndex, optionIdx);
            }
        }

        private void updateOptionStyling(int selectedIdx) {
            Context context = binding.getRoot().getContext();
            int colorPrimary = ContextCompat.getColor(context, R.color.brand_primary);
            int colorStrokeDefault = ContextCompat.getColor(context, R.color.stroke_light);
            int colorSurfaceDefault = ContextCompat.getColor(context, R.color.brand_primary_surface);

            for (int i = 0; i < 4; i++) {
                if (i == selectedIdx) {
                    // Selected state
                    optionCards[i].setStrokeColor(ColorStateList.valueOf(colorPrimary));
                    optionCards[i].setStrokeWidth(dpsToPixels(context, 2));
                    
                    optionLabels[i].setBackgroundResource(R.drawable.bg_circle_gradient); // fill with gradient
                    optionLabels[i].setTextColor(ContextCompat.getColor(context, R.color.white));
                } else {
                    // Default unselected state
                    optionCards[i].setStrokeColor(ColorStateList.valueOf(colorStrokeDefault));
                    optionCards[i].setStrokeWidth(dpsToPixels(context, 1));
                    
                    optionLabels[i].setBackgroundResource(R.drawable.bg_circle_primary_light);
                    optionLabels[i].setTextColor(colorPrimary);
                }
            }
        }

        private int dpsToPixels(Context context, int dps) {
            return (int) (dps * context.getResources().getDisplayMetrics().density + 0.5f);
        }
    }
}

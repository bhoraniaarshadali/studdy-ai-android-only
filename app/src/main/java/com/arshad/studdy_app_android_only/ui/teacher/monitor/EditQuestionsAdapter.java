package com.arshad.studdy_app_android_only.ui.teacher.monitor;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.Question;

import java.util.List;

public class EditQuestionsAdapter extends RecyclerView.Adapter<EditQuestionsAdapter.ViewHolder> {

    private final List<Question> questions;
    private final boolean isEditable;

    public EditQuestionsAdapter(List<Question> questions, boolean isEditable) {
        this.questions = questions;
        this.isEditable = isEditable;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_edit_question_correct_answer, parent, false);
        return new ViewHolder(view);
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
        private final TextView tvQuestionText;
        private final RadioGroup rgOptions;
        private final RadioButton[] radioButtons;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvQuestionText = itemView.findViewById(R.id.tv_question_text);
            rgOptions = itemView.findViewById(R.id.rg_options);
            radioButtons = new RadioButton[] {
                    itemView.findViewById(R.id.rb_option_0),
                    itemView.findViewById(R.id.rb_option_1),
                    itemView.findViewById(R.id.rb_option_2),
                    itemView.findViewById(R.id.rb_option_3)
            };
        }

        void bind(Question question, int position) {
            tvQuestionText.setText("Q" + (position + 1) + ". " + question.questionText);

            rgOptions.setOnCheckedChangeListener(null); // Clear listener before setting checked status

            for (int i = 0; i < 4; i++) {
                if (i < question.options.size()) {
                    radioButtons[i].setVisibility(View.VISIBLE);
                    String label = getOptionPrefix(i) + question.options.get(i);
                    radioButtons[i].setText(label);
                    radioButtons[i].setEnabled(isEditable);
                } else {
                    radioButtons[i].setVisibility(View.GONE);
                }
            }

            int checkedId = getRadioButtonId(question.correctIndex);
            if (checkedId != -1) {
                rgOptions.check(checkedId);
            } else {
                rgOptions.clearCheck();
            }

            rgOptions.setOnCheckedChangeListener((group, checkedId1) -> {
                int selectedIndex = getOptionIndex(checkedId1);
                if (selectedIndex != -1) {
                    question.correctIndex = selectedIndex;
                }
            });
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

        private int getRadioButtonId(int correctIndex) {
            switch (correctIndex) {
                case 0: return R.id.rb_option_0;
                case 1: return R.id.rb_option_1;
                case 2: return R.id.rb_option_2;
                case 3: return R.id.rb_option_3;
                default: return -1;
            }
        }

        private int getOptionIndex(int radioButtonId) {
            if (radioButtonId == R.id.rb_option_0) return 0;
            if (radioButtonId == R.id.rb_option_1) return 1;
            if (radioButtonId == R.id.rb_option_2) return 2;
            if (radioButtonId == R.id.rb_option_3) return 3;
            return -1;
        }
    }
}

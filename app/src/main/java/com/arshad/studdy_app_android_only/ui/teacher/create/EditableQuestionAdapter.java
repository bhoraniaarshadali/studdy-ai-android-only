package com.arshad.studdy_app_android_only.ui.teacher.create;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.Question;
import com.arshad.studdy_app_android_only.databinding.ItemEditableQuestionBinding;

import java.util.ArrayList;
import java.util.List;

public class EditableQuestionAdapter extends RecyclerView.Adapter<EditableQuestionAdapter.ViewHolder> {

    public interface OnQuestionDeletedListener {
        void onQuestionDeleted(int position);
    }

    private final List<Question> questionsList = new ArrayList<>();
    private final OnQuestionDeletedListener deleteListener;

    public EditableQuestionAdapter(OnQuestionDeletedListener deleteListener) {
        this.deleteListener = deleteListener;
    }

    public void setQuestions(List<Question> questions) {
        questionsList.clear();
        if (questions != null) {
            questionsList.addAll(questions);
        }
        notifyDataSetChanged();
    }

    public List<Question> getQuestions() {
        return questionsList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemEditableQuestionBinding binding = ItemEditableQuestionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(questionsList.get(position), position);
    }

    @Override
    public int getItemCount() {
        return questionsList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemEditableQuestionBinding binding;
        private final TextView[] optionLabels;
        private final EditText[] optionInputs;

        private TextWatcher questionWatcher;
        private final TextWatcher[] optionWatchers = new TextWatcher[4];

        ViewHolder(ItemEditableQuestionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            optionLabels = new TextView[] {
                    binding.tvLabelOption0,
                    binding.tvLabelOption1,
                    binding.tvLabelOption2,
                    binding.tvLabelOption3
            };

            optionInputs = new EditText[] {
                    binding.etOption0,
                    binding.etOption1,
                    binding.etOption2,
                    binding.etOption3
            };
        }

        void bind(Question question, int position) {
            // Remove previous watchers to prevent recycling leakage
            removeWatchers();

            binding.tvQuestionIndex.setText("Question " + (position + 1));
            binding.etQuestionText.setText(question.questionText);

            // Populate options
            for (int i = 0; i < 4; i++) {
                if (i < question.options.size()) {
                    optionInputs[i].setText(question.options.get(i));
                } else {
                    optionInputs[i].setText("");
                }
            }

            // Correct option highlighting
            updateLabelHighlight(question.correctIndex);

            // Click listener on label to select correct answer
            for (int i = 0; i < 4; i++) {
                final int idx = i;
                optionLabels[i].setOnClickListener(v -> {
                    question.correctIndex = idx;
                    updateLabelHighlight(idx);
                });
            }

            binding.btnDeleteQuestion.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onQuestionDeleted(getAdapterPosition());
                }
            });

            // Set up input change watchers to update model lists in-place
            setupWatchers(question);
        }

        private void setupWatchers(Question question) {
            questionWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                    question.questionText = s.toString();
                }
                @Override public void afterTextChanged(Editable s) {}
            };
            binding.etQuestionText.addTextChangedListener(questionWatcher);

            for (int i = 0; i < 4; i++) {
                final int idx = i;
                optionWatchers[i] = new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                    @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                        if (idx < question.options.size()) {
                            question.options.set(idx, s.toString());
                        } else {
                            // Expand list if needed
                            while (question.options.size() <= idx) {
                                question.options.add("");
                            }
                            question.options.set(idx, s.toString());
                        }
                    }
                    @Override public void afterTextChanged(Editable s) {}
                };
                optionInputs[i].addTextChangedListener(optionWatchers[i]);
            }
        }

        private void removeWatchers() {
            if (questionWatcher != null) {
                binding.etQuestionText.removeTextChangedListener(questionWatcher);
            }
            for (int i = 0; i < 4; i++) {
                if (optionWatchers[i] != null) {
                    optionInputs[i].removeTextChangedListener(optionWatchers[i]);
                }
            }
        }

        private void updateLabelHighlight(int correctIndex) {
            int colorPrimary = ContextCompat.getColor(binding.getRoot().getContext(), R.color.brand_primary);
            int colorSuccess = ContextCompat.getColor(binding.getRoot().getContext(), R.color.color_success);

            for (int i = 0; i < 4; i++) {
                if (i == correctIndex) {
                    optionLabels[i].setBackgroundResource(R.drawable.bg_option_correct);
                    optionLabels[i].setTextColor(colorSuccess);
                } else {
                    optionLabels[i].setBackgroundResource(R.drawable.bg_circle_primary_light);
                    optionLabels[i].setTextColor(colorPrimary);
                }
            }
        }
    }
}

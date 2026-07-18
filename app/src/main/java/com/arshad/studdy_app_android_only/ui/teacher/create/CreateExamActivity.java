package com.arshad.studdy_app_android_only.ui.teacher.create;

import com.arshad.studdy_app_android_only.data.local.AppDatabase;
import com.arshad.studdy_app_android_only.data.local.entity.ExamDraft;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import com.arshad.studdy_app_android_only.util.ErrorMessageMapper;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.arshad.studdy_app_android_only.BuildConfig;
import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.Exam;
import com.arshad.studdy_app_android_only.data.model.GeneratedPaper;
import com.arshad.studdy_app_android_only.data.model.Question;
import com.arshad.studdy_app_android_only.data.model.PaperConfig;
import com.arshad.studdy_app_android_only.data.model.PaperSection;
import com.arshad.studdy_app_android_only.data.remote.api.ExamApi;
import com.arshad.studdy_app_android_only.data.remote.api.GeminiApi;
import com.arshad.studdy_app_android_only.data.remote.api.PaperApi;
import com.arshad.studdy_app_android_only.data.remote.dto.GeminiRequest;
import com.arshad.studdy_app_android_only.data.remote.dto.GeminiResponse;
import com.arshad.studdy_app_android_only.databinding.ActivityCreateExamBinding;
import com.arshad.studdy_app_android_only.network.GeminiClient;
import com.arshad.studdy_app_android_only.network.SupabaseClient;
import com.arshad.studdy_app_android_only.ui.BaseActivity;
import com.arshad.studdy_app_android_only.ui.qr.QrDisplayActivity;
import com.arshad.studdy_app_android_only.ui.teacher.create.PaperSectionAdapter;
import com.arshad.studdy_app_android_only.util.DateTimeUtils;
import com.arshad.studdy_app_android_only.util.SessionManager;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Exam creation screen.
 * Leverages Google Gemini API to automatically generate MCQ questions from text notes.
 * Validates availability windows and saves/publishes to Supabase.
 */
public class CreateExamActivity extends BaseActivity {

    private static final String TAG = "CreateExamActivity";
    public static final String EXTRA_OPERATION_MODE = "extra_operation_mode";
    public static final String MODE_MCQ = "mode_mcq";
    public static final String MODE_PRINTABLE_PAPER = "mode_printable_paper";

    private ActivityCreateExamBinding binding;
    private SessionManager sessionManager;
    private EditableQuestionAdapter questionsAdapter;

    private String timerMode = "none";
    private String resultMode = "instant";
    private boolean loading = false;
    private boolean paperMode = false;
    private String difficulty = "Balanced";
    private PaperConfig paperConfig;
    private boolean promptRetryInProgress = false;
    private List<Question> generatedPaperQuestions = new ArrayList<>();

    private Calendar windowStartCalendar;
    private Calendar windowEndCalendar;

    private String windowStartIso = null;
    private String windowEndIso = null;

    private String selectedPdfBase64 = null;
    private byte[] selectedPdfBytes = null;
    private String selectedPdfFileName = null;
    private int questionCount = 10;
    private int currentDurationMins = 60;

    /** Single-thread executor for background PDF I/O. */
    private final ExecutorService pdfExecutor = Executors.newSingleThreadExecutor();
    /** Handler for posting results back to the main thread. */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** Guard flag: true while a PDF is being read in the background. */
    private boolean pdfLoading = false;

    private PaperSectionAdapter paperSectionAdapter;
    private String paperTemplate = "School Exam";
    private String paperDifficulty = "Balanced";

    private final ActivityResultLauncher<Intent> pdfPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        handleSelectedPdf(uri);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateExamBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = SessionManager.getInstance(this);
        if (!sessionManager.hasTeacherSession()) {
            finish();
            return;
        }

        setupMode();
        setupToolbar();
        setupToggleGroups();
        setupDatePickerFields();
        setupRecyclerView();
        setupPaperControls();
        setupClickListeners();
        setupQuestionCountStepper();
        setupDifficultyToggle();
        setupDurationStepper();
        setupTextWatchers();
        checkForDraft();
    }

    private void setupMode() {
        String mode = getIntent().getStringExtra(EXTRA_OPERATION_MODE);
        paperMode = MODE_PRINTABLE_PAPER.equals(mode);
        paperConfig = new PaperConfig();

        if (paperMode) {
            binding.toolbar.setTitle("Generate Exam Paper");
            binding.btnGenerate.setText(getString(R.string.btn_generate_paper));
            binding.btnPublish.setText(getString(R.string.btn_save_paper));
            binding.layoutPaperControls.setVisibility(View.VISIBLE);
            binding.layoutMcqControls.setVisibility(View.GONE);
        } else {
            binding.toolbar.setTitle(getString(R.string.create_exam));
            binding.btnGenerate.setText(getString(R.string.btn_generate_questions));
            binding.btnPublish.setText(getString(R.string.btn_publish_exam));
            binding.layoutPaperControls.setVisibility(View.GONE);
            binding.layoutMcqControls.setVisibility(View.VISIBLE);
        }
    }

    private void setupPaperControls() {
        binding.rvPaperSections.setLayoutManager(new LinearLayoutManager(this));
        paperSectionAdapter = new PaperSectionAdapter(position -> {
            paperConfig.removeSection(position);
            paperSectionAdapter.setSections(paperConfig.sections);
            updatePaperSummary();
        });
        binding.rvPaperSections.setAdapter(paperSectionAdapter);

        ArrayAdapter<CharSequence> templateAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.paper_templates,
                android.R.layout.simple_spinner_item);
        templateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerPaperTemplate.setAdapter(templateAdapter);
        binding.spinnerPaperTemplate.setSelection(0);
        binding.spinnerPaperTemplate.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                paperTemplate = parent.getItemAtPosition(position).toString();
                paperConfig.template = paperTemplate;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        binding.togglePaperDifficulty.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_paper_diff_easy) {
                paperDifficulty = "Easy";
            } else if (checkedId == R.id.btn_paper_diff_balanced) {
                paperDifficulty = "Balanced";
            } else if (checkedId == R.id.btn_paper_diff_tough) {
                paperDifficulty = "Tough";
            }
            paperConfig.overallDifficulty = paperDifficulty;
        });

        binding.btnAddSection.setOnClickListener(v -> showAddSectionDialog());
        updatePaperSummary();
    }

    private void updatePaperSummary() {
        paperConfig.updateTotalMarks();
        int sectionCount = paperConfig.sections.size();
        binding.tvPaperSectionCount.setText(String.valueOf(sectionCount));
        binding.tvPaperTotalMarks.setText(String.valueOf(paperConfig.calculateTotalMarks()));
        binding.tvPaperEmptyState.setVisibility(sectionCount == 0 ? View.VISIBLE : View.GONE);
    }

    private void showAddSectionDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_section, null);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setTitle("Add Section")
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            android.widget.EditText etSectionName = dialogView.findViewById(R.id.et_section_name);
            Spinner spinnerType = dialogView.findViewById(R.id.spinner_question_type);
            TextView tvCount = dialogView.findViewById(R.id.tv_section_count);
            TextView tvMarks = dialogView.findViewById(R.id.tv_marks_per_question);
            com.google.android.material.button.MaterialButton btnEasy = dialogView.findViewById(R.id.btn_section_diff_easy);
            com.google.android.material.button.MaterialButton btnMedium = dialogView.findViewById(R.id.btn_section_diff_medium);
            com.google.android.material.button.MaterialButton btnHard = dialogView.findViewById(R.id.btn_section_diff_hard);
            com.google.android.material.button.MaterialButtonToggleGroup toggleDifficulty = dialogView.findViewById(R.id.toggle_section_difficulty);
            ImageButton btnCountDecrement = dialogView.findViewById(R.id.btn_section_count_decrement);
            ImageButton btnCountIncrement = dialogView.findViewById(R.id.btn_section_count_increment);
            ImageButton btnMarksDecrement = dialogView.findViewById(R.id.btn_marks_decrement);
            ImageButton btnMarksIncrement = dialogView.findViewById(R.id.btn_marks_increment);

            ArrayAdapter<CharSequence> typeAdapter = ArrayAdapter.createFromResource(
                    CreateExamActivity.this,
                    R.array.section_question_types,
                    android.R.layout.simple_spinner_item);
            typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerType.setAdapter(typeAdapter);

            final int[] sectionCount = {5};
            final int[] marksPerQuestion = {2};
            final String[] sectionDifficulty = {"Medium"};

            tvCount.setText(String.valueOf(sectionCount[0]));
            tvMarks.setText(String.valueOf(marksPerQuestion[0]));
            toggleDifficulty.check(R.id.btn_section_diff_medium);

            btnCountDecrement.setOnClickListener(v -> {
                if (sectionCount[0] > 1) {
                    sectionCount[0]--;
                    tvCount.setText(String.valueOf(sectionCount[0]));
                }
            });

            btnCountIncrement.setOnClickListener(v -> {
                if (sectionCount[0] < 50) {
                    sectionCount[0]++;
                    tvCount.setText(String.valueOf(sectionCount[0]));
                }
            });

            btnMarksDecrement.setOnClickListener(v -> {
                if (marksPerQuestion[0] > 1) {
                    marksPerQuestion[0]--;
                    tvMarks.setText(String.valueOf(marksPerQuestion[0]));
                }
            });

            btnMarksIncrement.setOnClickListener(v -> {
                if (marksPerQuestion[0] < 20) {
                    marksPerQuestion[0]++;
                    tvMarks.setText(String.valueOf(marksPerQuestion[0]));
                }
            });

            toggleDifficulty.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                if (checkedId == R.id.btn_section_diff_easy) {
                    sectionDifficulty[0] = "Easy";
                } else if (checkedId == R.id.btn_section_diff_medium) {
                    sectionDifficulty[0] = "Medium";
                } else if (checkedId == R.id.btn_section_diff_hard) {
                    sectionDifficulty[0] = "Hard";
                }
            });

            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etSectionName.getText() != null ? etSectionName.getText().toString().trim() : "";
                if (name.isEmpty()) {
                    etSectionName.setError("Section name is required");
                    return;
                }

                String sectionLabel = name;
                String typeValue = spinnerType.getSelectedItem().toString();
                String typeId = typeValue.replace(" ", "_").toUpperCase();
                int count = sectionCount[0];
                int marks = marksPerQuestion[0];
                String difficultyValue = sectionDifficulty[0];

                String sectionId = String.valueOf((char) ('A' + paperConfig.sections.size()));
                PaperSection section = new PaperSection(
                        sectionId,
                        sectionLabel,
                        typeId,
                        count,
                        marks,
                        difficultyValue
                );
                paperConfig.addSection(section);
                paperSectionAdapter.setSections(paperConfig.sections);
                updatePaperSummary();
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void setupDurationStepper() {
        binding.btnDurationMinus.setOnClickListener(v -> {
            if (currentDurationMins > 5) {
                updateDuration(currentDurationMins - 5);
            }
        });

        binding.btnDurationPlus.setOnClickListener(v -> {
            if (currentDurationMins < 480) { // arbitrary limit
                updateDuration(currentDurationMins + 5);
            }
        });

        // Duration Chips
        binding.chipDur15.setOnClickListener(v -> updateDuration(15));
        binding.chipDur30.setOnClickListener(v -> updateDuration(30));
        binding.chipDur45.setOnClickListener(v -> updateDuration(45));
        binding.chipDur60.setOnClickListener(v -> updateDuration(60));
        binding.chipDur90.setOnClickListener(v -> updateDuration(90));

        // Initial UI sync
        updateDuration(currentDurationMins);
    }

    private void updateDuration(int mins) {
        currentDurationMins = mins;
        binding.tvDurationValue.setText(String.valueOf(mins));
        updateDurationChips(mins);
    }

    private void setupQuestionCountStepper() {
        binding.tvQuestionCount.setText(String.valueOf(questionCount));
        updateStepperButtons();

        binding.btnDecrementQuestions.setOnClickListener(v -> {
            if (questionCount > 10) {
                questionCount--;
                binding.tvQuestionCount.setText(String.valueOf(questionCount));
                updateStepperButtons();
            }
        });

        binding.btnIncrementQuestions.setOnClickListener(v -> {
            if (questionCount < 50) {
                questionCount++;
                binding.tvQuestionCount.setText(String.valueOf(questionCount));
                updateStepperButtons();
            }
        });
    }

    private void setupDifficultyToggle() {
        binding.toggleDifficulty.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_difficulty_easy) {
                difficulty = "Easy";
            } else if (checkedId == R.id.btn_difficulty_balanced) {
                difficulty = "Balanced";
            } else if (checkedId == R.id.btn_difficulty_tough) {
                difficulty = "Tough";
            }
        });
    }

    private void updateStepperButtons() {
        binding.btnDecrementQuestions.setEnabled(questionCount > 10);
        binding.btnIncrementQuestions.setEnabled(questionCount < 50);
    }

    private void setupTextWatchers() {
        binding.etExamTitle.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                binding.tilExamTitle.setError(null);
            }
        });

        binding.etExamContent.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                binding.tilExamContent.setError(null);
            }
        });
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> handleBackAction());
        binding.toolbar.inflateMenu(R.menu.menu_create_exam);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (loading) return false;
            if (item.getItemId() == R.id.action_add_question) {
                addNewManualQuestion();
                return true;
            }
            return false;
        });
    }

    private void setMenuAddQuestionVisible(boolean visible) {
        android.view.MenuItem addQuestionItem = binding.toolbar.getMenu().findItem(R.id.action_add_question);
        if (addQuestionItem != null) {
            addQuestionItem.setVisible(visible);
        }
    }

    private void setupToggleGroups() {
        // Result Mode Selection Cards
        binding.cardResultInstant.setOnClickListener(v -> {
            resultMode = "instant";
            binding.cardResultInstant.setChecked(true);
            binding.cardResultManual.setChecked(false);
        });

        binding.cardResultManual.setOnClickListener(v -> {
            resultMode = "manual";
            binding.cardResultManual.setChecked(true);
            binding.cardResultInstant.setChecked(false);
        });

        // Timer Mode Selection Cards
        binding.cardTimerNone.setOnClickListener(v -> {
            timerMode = "none";
            binding.cardTimerNone.setChecked(true);
            binding.cardTimerDuration.setChecked(false);
            binding.cardTimerWindow.setChecked(false);
            binding.layoutDurationConfig.setVisibility(View.GONE);
            binding.layoutWindowInputs.setVisibility(View.GONE);
        });

        binding.cardTimerDuration.setOnClickListener(v -> {
            timerMode = "duration";
            binding.cardTimerDuration.setChecked(true);
            binding.cardTimerNone.setChecked(false);
            binding.cardTimerWindow.setChecked(false);
            binding.layoutDurationConfig.setVisibility(View.VISIBLE);
            binding.layoutWindowInputs.setVisibility(View.GONE);
        });

        binding.cardTimerWindow.setOnClickListener(v -> {
            timerMode = "window";
            binding.cardTimerWindow.setChecked(true);
            binding.cardTimerNone.setChecked(false);
            binding.cardTimerDuration.setChecked(false);
            binding.layoutDurationConfig.setVisibility(View.GONE);
            binding.layoutWindowInputs.setVisibility(View.VISIBLE);
        });
    }


    private void updateDurationChips(int mins) {
        binding.chipDur15.setChecked(mins == 15);
        binding.chipDur30.setChecked(mins == 30);
        binding.chipDur45.setChecked(mins == 45);
        binding.chipDur60.setChecked(mins == 60);
        binding.chipDur90.setChecked(mins == 90);
    }

    private void setupDatePickerFields() {
        binding.etWindowStart.setOnClickListener(v -> showDateTimePickerDialog(true));
        binding.etWindowEnd.setOnClickListener(v -> showDateTimePickerDialog(false));
    }

    private void showDateTimePickerDialog(boolean isStart) {
        Calendar current = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            new TimePickerDialog(CreateExamActivity.this, (timeView, hourOfDay, minute) -> {
                Calendar selected = Calendar.getInstance();
                selected.set(year, month, dayOfMonth, hourOfDay, minute, 0);
                
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                String display = sdf.format(selected.getTime());
                String iso = DateTimeUtils.formatIso8601(selected.getTime());

                if (isStart) {
                    windowStartCalendar = selected;
                    windowStartIso = iso;
                    binding.etWindowStart.setText(display);
                } else {
                    windowEndCalendar = selected;
                    windowEndIso = iso;
                    binding.etWindowEnd.setText(display);
                }
            }, current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), true).show();
        }, current.get(Calendar.YEAR), current.get(Calendar.MONTH), current.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void setupRecyclerView() {
        questionsAdapter = new EditableQuestionAdapter(position -> {
            questionsAdapter.getQuestions().remove(position);
            questionsAdapter.notifyItemRemoved(position);
            // Re-index remaining questions
            for (int i = 0; i < questionsAdapter.getQuestions().size(); i++) {
                questionsAdapter.getQuestions().get(i).id = String.valueOf(i);
            }
            questionsAdapter.notifyItemRangeChanged(position, questionsAdapter.getItemCount() - position);
            updateQuestionCountHeader();
        });
        binding.rvGeneratedQuestions.setLayoutManager(new LinearLayoutManager(this));
        binding.rvGeneratedQuestions.setAdapter(questionsAdapter);
    }

    private void setupClickListeners() {
        binding.btnGenerate.setOnClickListener(v -> {
            if (paperMode) {
                attemptPaperGeneration();
            } else {
                attemptAiGeneration();
            }
        });

        binding.btnPublish.setOnClickListener(v -> {
            if (paperMode) {
                saveGeneratedPaper();
            } else {
                publishExam();
            }
        });

        binding.btnUploadPdf.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/pdf");
            pdfPickerLauncher.launch(intent);
        });

        binding.btnClearPdf.setOnClickListener(v -> {
            selectedPdfBase64 = null;
            selectedPdfBytes = null;
            selectedPdfFileName = null;
            binding.tvPdfName.setText("No PDF file selected");
            binding.btnClearPdf.setVisibility(View.GONE);
            binding.tilExamContent.setEnabled(true);
            binding.tilExamContent.setError(null);
        });
    }

    private void attemptAiGeneration() {
        String title = binding.etExamTitle.getText() != null ? binding.etExamTitle.getText().toString().trim() : "";
        String content = binding.etExamContent.getText() != null ? binding.etExamContent.getText().toString().trim() : "";

        if (title.isEmpty()) {
            binding.tilExamTitle.setError("Title is required");
            return;
        }
        binding.tilExamTitle.setError(null);

        if (content.length() < 20 && selectedPdfBase64 == null) {
            binding.tilExamContent.setError("Please paste a text notes (min 20 characters) OR upload a PDF file");
            return;
        }
        binding.tilExamContent.setError(null);

        // Duration timer is selected via slider, always valid

        // Validate Window timer
        if ("window".equals(timerMode)) {
            if (windowStartIso == null || windowEndIso == null) {
                Toast.makeText(this, "Please configure both window start and end times", Toast.LENGTH_SHORT).show();
                return;
            }
            if (windowStartCalendar.after(windowEndCalendar)) {
                Toast.makeText(this, "Start window must be before end window", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // If inputs are valid, trigger call
        setLoading(true, "Generating questions…");

        // Question count is read from the stepper member variable questionCount

        String prompt;
        if (selectedPdfBase64 != null) {
            prompt = "Generate exactly " + questionCount + " multiple choice questions based on the attached PDF document. " +
                    "The output MUST be a JSON array of objects. Each object must contain exactly: " +
                    "\"questionText\" (string), \"options\" (array of exactly 4 strings), and \"correctIndex\" (integer from 0 to 3 indicating the correct answer). " +
                    "Do not include any markdown format tags like ```json or wrappers, just output raw JSON text.";
        } else {
            prompt = "Generate exactly " + questionCount + " multiple choice questions based on the following text notes. " +
                    "The output MUST be a JSON array of objects. Each object must contain exactly: " +
                    "\"questionText\" (string), \"options\" (array of exactly 4 strings), and \"correctIndex\" (integer from 0 to 3 indicating the correct answer). " +
                    "Do not include any markdown format tags like ```json or wrappers, just output raw JSON text. " +
                    "Text Notes:\n" + content;
        }

        GeminiApi geminiApi = GeminiClient.getInstance().createService(GeminiApi.class);
        geminiApi.generateContent(BuildConfig.GEMINI_API_KEY, new GeminiRequest(prompt, selectedPdfBase64)).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    String rawText = response.body().getGeneratedText();
                    if (rawText != null) {
                        try {
                            // Clean json format in case model added markdown code block markers
                            String cleanJson = rawText.trim();
                            if (cleanJson.startsWith("```json")) {
                                cleanJson = cleanJson.substring(7);
                            }
                            if (cleanJson.endsWith("```")) {
                                cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
                            }
                            cleanJson = cleanJson.trim();

                            List<Question> questions = new Gson().fromJson(cleanJson, new TypeToken<List<Question>>(){}.getType());
                            
                            // Re-index questions just in case
                            for (int i = 0; i < questions.size(); i++) {
                                questions.get(i).id = String.valueOf(i);
                            }

                            if (!questions.isEmpty()) {
                                showStep2(questions);
                            } else {
                                Toast.makeText(CreateExamActivity.this, "AI generated an empty questions list. Please try again.", Toast.LENGTH_LONG).show();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed parsing generated JSON: " + rawText, e);
                            Toast.makeText(CreateExamActivity.this, "AI returned malformed data. Let's try again.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(CreateExamActivity.this, "AI generation failed: Empty response", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    if (response.code() == 413 || response.code() == 400) {
                        Toast.makeText(CreateExamActivity.this, "This file couldn't be processed — please try a smaller PDF", Toast.LENGTH_LONG).show();
                    } else {
                        String friendly = ErrorMessageMapper.toUserMessage(TAG, "AI generation failed", response.code());
                        Toast.makeText(CreateExamActivity.this, friendly, Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<GeminiResponse> call, Throwable t) {
                setLoading(false);
                String msg = t.getMessage();
                if (msg != null && (msg.contains("too large") || msg.contains("413") || msg.contains("400") || msg.contains("Length Required") || msg.contains("Request Entity Too Large"))) {
                    Toast.makeText(CreateExamActivity.this, "This file couldn't be processed — please try a smaller PDF", Toast.LENGTH_LONG).show();
                } else {
                    String friendly = ErrorMessageMapper.toUserMessage(TAG, "Network error during AI generation", t);
                    Toast.makeText(CreateExamActivity.this, friendly, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void attemptPaperGeneration() {
        if (paperConfig == null || paperConfig.sections.isEmpty()) {
            Toast.makeText(this, "Please configure paper structure before generating.", Toast.LENGTH_SHORT).show();
            return;
        }

        String title = binding.etExamTitle.getText() != null ? binding.etExamTitle.getText().toString().trim() : "";
        if (title.isEmpty()) {
            binding.tilExamTitle.setError("Title is required");
            return;
        }
        binding.tilExamTitle.setError(null);

        String content = binding.etExamContent.getText() != null ? binding.etExamContent.getText().toString().trim() : "";
        if (content.length() < 20 && selectedPdfBase64 == null) {
            binding.tilExamContent.setError("Please paste at least 20 characters of text OR upload a PDF file");
            return;
        }
        binding.tilExamContent.setError(null);

        setLoading(true, "Generating exam paper…");
        paperConfig.title = title;
        paperConfig.pdfSource = selectedPdfBase64 != null ? "PDF" : "TEXT";
        paperConfig.pdfBase64 = selectedPdfBase64;

        String prompt = buildGeminiPaperPrompt(content);

        GeminiApi geminiApi = GeminiClient.getInstance().createService(GeminiApi.class);
        geminiApi.generateContent(BuildConfig.GEMINI_API_KEY, new GeminiRequest(prompt, selectedPdfBase64)).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    handleGeminiPaperResponse(response.body().getGeneratedText(), prompt);
                } else {
                    setLoading(false);
                    String friendly = ErrorMessageMapper.toUserMessage(TAG, "AI generation failed", response.code());
                    Toast.makeText(CreateExamActivity.this, friendly, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<GeminiResponse> call, Throwable t) {
                setLoading(false);
                String friendly = ErrorMessageMapper.toUserMessage(TAG, "Network error during AI generation", t);
                Toast.makeText(CreateExamActivity.this, friendly, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String buildGeminiPaperPrompt(String content) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate exam questions STRICTLY from ").append(selectedPdfBase64 != null ? "the attached PDF" : "the following content");
        sb.append(". Use ONLY information present in the source. Do not invent anything.\n\n");

        sb.append("Paper template: ").append(paperConfig.template != null ? paperConfig.template : "Standard").append("\n");
        sb.append("Overall difficulty: ").append(paperConfig.overallDifficulty != null ? paperConfig.overallDifficulty : "Balanced").append("\n\n");
        sb.append("Sections to generate:\n");
        for (PaperSection section : paperConfig.sections) {
            sb.append("- Section ").append(section.id).append(" (Title: ").append(section.title).append("): ")
              .append(section.questionCount).append(" questions, each ")
              .append(section.marksPerQuestion).append(" marks, difficulty ")
              .append(section.difficulty).append(". Type: ").append(section.questionType).append(".\n");
        }

        sb.append("\nReturn ONLY valid JSON with the exact schema below, without markdown wrappers, explanation, or extra fields:\n");
        sb.append("{\n");
        sb.append("  \"sections\": [\n");
        for (int i = 0; i < paperConfig.sections.size(); i++) {
            PaperSection sec = paperConfig.sections.get(i);
            sb.append("    {\"id\":\"").append(sec.id).append("\",\"title\":\"").append(sec.title)
              .append("\",\"questionType\":\"").append(sec.questionType).append("\",\"questionCount\":")
              .append(sec.questionCount).append(",\"marksPerQuestion\":").append(sec.marksPerQuestion)
              .append(",\"difficulty\":\"").append(sec.difficulty).append("\"}");
            if (i < paperConfig.sections.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");
        sb.append("  \"questions\": [\n");
        sb.append("    {\"id\":\"1\",\"section\":\"A\",\"questionText\":\"...\",\"options\":[\"...\",\"...\",\"...\",\"...\"],\"correctIndex\":0,\"marks\":1}\n");
        sb.append("  ]\n");
        sb.append("}\n\n");
        sb.append("For non-MCQ sections, options may be an empty array and correctIndex may be null.\n");

        if (selectedPdfBase64 == null) {
            sb.append("Content to generate from:\n").append(content);
        }

        return sb.toString();
    }

    private void handleGeminiPaperResponse(String rawText, String originalPrompt) {
        setLoading(false);
        if (rawText == null) {
            Toast.makeText(this, "AI generation failed: Empty response", Toast.LENGTH_SHORT).show();
            return;
        }

        String cleanJson = rawText.trim();
        if (cleanJson.startsWith("```json")) cleanJson = cleanJson.substring(7);
        if (cleanJson.endsWith("```")) cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
        cleanJson = cleanJson.trim();

        try {
            // Parse the highest-confidence content into a structured paper response.
            PaperResponse paperResponse = new Gson().fromJson(cleanJson, PaperResponse.class);
            if (paperResponse == null || paperResponse.questions == null || paperResponse.questions.isEmpty()) {
                Log.e(TAG, "Parsed paper response but found no questions: " + cleanJson);
                Toast.makeText(this, "AI returned no questions. Please try again.", Toast.LENGTH_LONG).show();
                return;
            }

            generatedPaperQuestions.clear();
            for (PaperQuestion responseQuestion : paperResponse.questions) {
                if (responseQuestion.questionText == null || responseQuestion.questionText.trim().isEmpty()) continue;
                List<String> options = responseQuestion.options != null ? responseQuestion.options : new ArrayList<>();
                int correctIndex = responseQuestion.correctIndex != null ? responseQuestion.correctIndex : -1;
                Question question = new Question(
                        responseQuestion.id != null ? responseQuestion.id : String.valueOf(generatedPaperQuestions.size()),
                        responseQuestion.questionText.trim(),
                        options,
                        correctIndex >= 0 ? correctIndex : 0
                );
                generatedPaperQuestions.add(question);
            }

            if (generatedPaperQuestions.isEmpty()) {
                Log.e(TAG, "No valid questions parsed from Gemini response: " + cleanJson);
                Toast.makeText(this, "AI produced questions in an unexpected format. Please try again.", Toast.LENGTH_LONG).show();
                return;
            }

            Toast.makeText(this, "Paper generated successfully! Review questions before saving.", Toast.LENGTH_SHORT).show();
            showStep2(generatedPaperQuestions);
        } catch (Exception e) {
            Log.e(TAG, "Failed parsing paper response: " + cleanJson, e);
            Toast.makeText(this, "Failed to parse AI response. Please try again.", Toast.LENGTH_LONG).show();
        }
    }

    private void showStep2(List<Question> questions) {
        binding.layoutCreateStep1.setVisibility(View.GONE);
        binding.layoutCreateStep2.setVisibility(View.VISIBLE);
        binding.layoutPublishBar.setVisibility(View.VISIBLE);
        setMenuAddQuestionVisible(true);
        binding.toolbar.setTitle("Review AI Questions");

        questionsAdapter.setQuestions(questions);
        updateQuestionCountHeader();
    }

    private void publishExam() {
        // Validate that there's at least 1 question
        List<Question> list = questionsAdapter.getQuestions();
        if (list.isEmpty()) {
            Toast.makeText(this, "Please add at least one question", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate that question text and options are filled out
        for (int i = 0; i < list.size(); i++) {
            Question q = list.get(i);
            if (q.questionText.trim().isEmpty()) {
                Toast.makeText(this, "Question " + (i + 1) + " cannot have empty text.", Toast.LENGTH_LONG).show();
                return;
            }
            for (int o = 0; o < q.options.size(); o++) {
                if (q.options.get(o).trim().isEmpty()) {
                    Toast.makeText(this, "Question " + (i + 1) + " Option " + (o + 1) + " cannot be empty.", Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }

        setLoading(true, "Publishing exam…");

        // Generate random uppercase 6-digit code
        String randomCode = generateRandomCode();

        // Create exam model
        Exam exam = new Exam();
        exam.title = binding.etExamTitle.getText().toString().trim();
        exam.code = randomCode;
        exam.questions = list;
        exam.timerMode = timerMode;
        
        if ("duration".equals(timerMode)) {
            exam.durationMinutes = currentDurationMins;
        } else if ("window".equals(timerMode)) {
            exam.windowStart = windowStartIso;
            exam.windowEnd = windowEndIso;
        }

        exam.resultMode = resultMode;
        exam.resultsPublished = false;
        exam.teacherId = sessionManager.getTeacherId();
        exam.proctoringEnabled = binding.switchProctoring.isChecked();

        ExamApi examApi = SupabaseClient.createAuthService(ExamApi.class, sessionManager.getAccessToken());
        examApi.insertExam(exam).enqueue(new Callback<List<Exam>>() {
            @Override
            public void onResponse(Call<List<Exam>> call, Response<List<Exam>> response) {
                setLoading(false);
                if (response.isSuccessful()) {
                    discardDraft(sessionManager.getTeacherId());
                    // Redirect to QR display screen
                    Intent intent = new Intent(CreateExamActivity.this, QrDisplayActivity.class);
                    intent.putExtra("exam_code", randomCode);
                    intent.putExtra("exam_title", exam.title);
                    startActivity(intent);
                    finish();
                } else {
                    String friendly = ErrorMessageMapper.toUserMessage(TAG, "Failed to publish exam", response.code());
                    Toast.makeText(CreateExamActivity.this, friendly, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<List<Exam>> call, Throwable t) {
                setLoading(false);
                String friendly = ErrorMessageMapper.toUserMessage(TAG, "Network error publishing exam", t);
                Toast.makeText(CreateExamActivity.this, friendly, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveGeneratedPaper() {
        if (paperConfig == null || paperConfig.sections.isEmpty()) {
            Toast.makeText(this, "No paper configuration found.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedPdfBytes != null && selectedPdfFileName != null) {
            uploadPdfToSupabase(selectedPdfFileName, selectedPdfBytes, new PdfUploadCallback() {
                @Override
                public void onSuccess(String publicUrl) {
                    savePaperRecord(publicUrl);
                }

                @Override
                public void onFailure(String errorMessage) {
                    setLoading(false);
                    String friendly = ErrorMessageMapper.toUserMessage(TAG, "PDF upload failed", errorMessage);
                    Toast.makeText(CreateExamActivity.this, friendly, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            savePaperRecord("");
        }
    }

    private void savePaperRecord(String pdfUrl) {
        setLoading(true, "Saving paper…");

        GeneratedPaper paper = new GeneratedPaper();
        paper.title = paperConfig.title;
        paper.totalMarks = paperConfig.calculateTotalMarks();
        paper.template = paperConfig.template;
        paper.difficulty = paperConfig.overallDifficulty;
        paper.teacherId = sessionManager.getTeacherId();
        paper.pdfUrl = pdfUrl;
        paper.sections = paperConfig.sections;
        paper.questions = questionsAdapter.getQuestions();

        PaperApi paperApi = SupabaseClient.createAuthService(PaperApi.class, sessionManager.getAccessToken());
        paperApi.createPaper(paper).enqueue(new Callback<List<GeneratedPaper>>() {
            @Override
            public void onResponse(Call<List<GeneratedPaper>> call, Response<List<GeneratedPaper>> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Toast.makeText(CreateExamActivity.this, "Paper saved successfully!", Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    String friendly = ErrorMessageMapper.toUserMessage(TAG, "Failed to save paper", response.code());
                    Toast.makeText(CreateExamActivity.this, friendly, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<List<GeneratedPaper>> call, Throwable t) {
                setLoading(false);
                String friendly = ErrorMessageMapper.toUserMessage(TAG, "Network error saving paper", t);
                Toast.makeText(CreateExamActivity.this, friendly, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void uploadPdfToSupabase(String fileName, byte[] pdfBytes, PdfUploadCallback callback) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            callback.onFailure("PDF file is empty");
            return;
        }

        setLoading(true, "Uploading PDF…");
        String objectName = makeSafePdfObjectName(fileName);
        String uploadUrl = BuildConfig.SUPABASE_URL + "/storage/v1/object/exam-papers/" + objectName;

        RequestBody fileBody = RequestBody.create(pdfBytes, okhttp3.MediaType.parse("application/pdf"));

        Request request = new Request.Builder()
                .url(uploadUrl + "?upsert=true")
                .post(fileBody)
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + sessionManager.getAccessToken())
                .header("Accept", "application/json")
                .build();

        new OkHttpClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                mainHandler.post(() -> callback.onFailure(e.getMessage() != null ? e.getMessage() : "Network error"));
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "";
                    mainHandler.post(() -> callback.onFailure("HTTP " + response.code() + ": " + body));
                    return;
                }

                String publicUrl = BuildConfig.SUPABASE_URL + "/storage/v1/object/public/exam-papers/" + objectName;
                mainHandler.post(() -> callback.onSuccess(publicUrl));
            }
        });
    }

    private String makeSafePdfObjectName(String originalName) {
        if (originalName == null || originalName.trim().isEmpty()) {
            originalName = "paper.pdf";
        }

        String safe = originalName.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!safe.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            safe += ".pdf";
        }
        return "paper-" + System.currentTimeMillis() + "-" + safe;
    }

    private interface PdfUploadCallback {
        void onSuccess(String publicUrl);
        void onFailure(String errorMessage);
    }

    private String generateRandomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void handleBackAction() {
        if (binding.layoutCreateStep2.getVisibility() == View.VISIBLE) {
            showBackConfirmationDialog();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        handleBackAction();
    }

    /**
     * Validates size, then reads + Base64-encodes the selected PDF on a background thread.
     * Shows a spinner while working; posts the result back to the main thread.
     */
    private void handleSelectedPdf(Uri uri) {
        String fileName = getFileName(uri);

        // Show the filename immediately so the user has feedback.
        binding.tvPdfName.setText(fileName + " (reading…)");
        binding.btnClearPdf.setVisibility(View.GONE);
        binding.btnGenerate.setEnabled(false);
        binding.progressLoading.setVisibility(View.VISIBLE);
        pdfLoading = true;
        Log.d(TAG, "PDF accepted — " + fileName + ". Starting background encode.");

        // ── Background thread I/O with robust OOM protection ─────────────────────
        pdfExecutor.execute(() -> {
            byte[] bytes = null;
            String base64Result = null;
            Throwable readError = null;
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream != null) {
                    bytes = readAllBytes(inputStream);
                    inputStream.close();
                    base64Result = Base64.encodeToString(bytes, Base64.NO_WRAP);
                    Log.d(TAG, "PDF encoded successfully — base64 length: " + base64Result.length());
                }
            } catch (Throwable t) {
                readError = t;
                Log.e(TAG, "Background PDF read failed or out of memory", t);
            }

            final byte[] finalBytes = bytes;
            final String finalBase64 = base64Result;
            final Throwable finalError = readError;
            final String finalName = fileName;

            // ── Post result back to main thread ──────────────────────────────────
            mainHandler.post(() -> {
                pdfLoading = false;
                binding.progressLoading.setVisibility(View.GONE);
                binding.btnGenerate.setEnabled(true);

                if (finalError != null || finalBase64 == null || finalBytes == null) {
                    binding.tvPdfName.setText("No PDF file selected");
                    Toast.makeText(CreateExamActivity.this,
                            "This file couldn't be processed — please try a smaller PDF",
                            Toast.LENGTH_LONG).show();
                } else {
                    selectedPdfBase64 = finalBase64;
                    selectedPdfBytes = finalBytes;
                    selectedPdfFileName = finalName;
                    binding.tvPdfName.setText(finalName);
                    binding.btnClearPdf.setVisibility(View.VISIBLE);
                    binding.etExamContent.setText("");
                    binding.tilExamContent.setError(null);
                    binding.tilExamContent.setEnabled(false);
                    Log.d(TAG, "PDF ready for Gemini request.");
                }
            });
        });
    }

    /**
     * Returns the file size from the content resolver without opening the stream.
     * Returns 0 if the size cannot be determined (treat as safe to proceed).
     */
    private long getPdfFileSize(Uri uri) {
        long size = 0;
        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[]{OpenableColumns.SIZE},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not determine PDF size — allowing proceed.", e);
        }
        return size;
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx != -1) {
                        result = cursor.getString(idx);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "getFileName failed", e);
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            } else {
                result = "selected.pdf";
            }
        }
        return result;
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; // 8 KB chunks
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }

    private void updateQuestionCountHeader() {
        if (questionsAdapter != null) {
            binding.toolbar.setSubtitle(questionsAdapter.getItemCount() + " questions generated");
        }
    }

    private void setLoading(boolean loading) {
        setLoading(loading, "Processing...");
    }

    private void setLoading(boolean loading, String message) {
        binding.btnGenerate.setEnabled(!loading);
        binding.btnPublish.setEnabled(!loading);
        if (loading) {
            binding.layoutLoadingOverlay.setVisibility(View.VISIBLE);
            binding.tvLoadingLabel.setText(message != null ? message : "Processing...");
        } else {
            binding.layoutLoadingOverlay.setVisibility(View.GONE);
        }
        this.loading = loading;
    }

    private void showBackConfirmationDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Unsaved Changes")
                .setMessage("You have unsaved changes. Save as draft or discard?")
                .setPositiveButton("Save as Draft", (dialog, which) -> saveDraft())
                .setNegativeButton("Discard", (dialog, which) -> {
                    discardDraft(sessionManager.getTeacherId());
                    finish();
                })
                .setNeutralButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }

    private void saveDraft() {
        String teacherId = sessionManager.getTeacherId();
        if (teacherId == null) return;

        ExamDraft draft = new ExamDraft();
        draft.id = teacherId;
        draft.title = binding.etExamTitle.getText().toString().trim();
        draft.timerMode = timerMode;

        if ("duration".equals(timerMode)) {
            draft.durationMinutes = currentDurationMins;
        } else if ("window".equals(timerMode)) {
            draft.windowStart = windowStartIso;
            draft.windowEnd = windowEndIso;
        }

        draft.resultMode = resultMode;
        draft.questionsJson = new Gson().toJson(questionsAdapter.getQuestions());
        draft.createdAt = System.currentTimeMillis();
        draft.updatedAt = System.currentTimeMillis();

        new Thread(() -> {
            AppDatabase.getInstance(this).examDraftDao().insertDraft(draft);
            runOnUiThread(() -> {
                Toast.makeText(this, "Draft saved successfully", Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }

    private void discardDraft(String teacherId) {
        if (teacherId == null) return;
        new Thread(() -> {
            AppDatabase.getInstance(this).examDraftDao().deleteDraft(teacherId);
        }).start();
    }

    private void checkForDraft() {
        String teacherId = sessionManager.getTeacherId();
        if (teacherId == null) return;

        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            ExamDraft draft = db.examDraftDao().getDraft(teacherId);
            if (draft != null) {
                runOnUiThread(() -> showResumeDraftDialog(draft));
            }
        }).start();
    }

    private void showResumeDraftDialog(ExamDraft draft) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Resume Unsaved Draft")
                .setMessage("You have an unsaved draft from your previous session. Would you like to resume editing it?")
                .setPositiveButton("Resume", (dialog, which) -> resumeDraft(draft))
                .setNegativeButton("Discard Draft", (dialog, which) -> discardDraft(draft.id))
                .setCancelable(false)
                .show();
    }

    private void resumeDraft(ExamDraft draft) {
        binding.etExamTitle.setText(draft.title);

        timerMode = draft.timerMode;
        if ("duration".equals(timerMode)) {
            binding.cardTimerDuration.performClick();
            if (draft.durationMinutes != null) {
                updateDuration(draft.durationMinutes);
            }
        } else if ("window".equals(timerMode)) {
            binding.cardTimerWindow.performClick();
            windowStartIso = draft.windowStart;
            windowEndIso = draft.windowEnd;

            Date startDate = DateTimeUtils.parseIso8601(draft.windowStart);
            if (startDate != null) {
                windowStartCalendar = Calendar.getInstance();
                windowStartCalendar.setTime(startDate);
            }
            Date endDate = DateTimeUtils.parseIso8601(draft.windowEnd);
            if (endDate != null) {
                windowEndCalendar = Calendar.getInstance();
                windowEndCalendar.setTime(endDate);
            }

            binding.etWindowStart.setText(formatIsoToDisplay(draft.windowStart));
            binding.etWindowEnd.setText(formatIsoToDisplay(draft.windowEnd));
        } else {
            binding.cardTimerNone.performClick();
        }

        resultMode = draft.resultMode;
        if ("manual".equals(resultMode)) {
            binding.cardResultManual.performClick();
        } else {
            binding.cardResultInstant.performClick();
        }

        List<Question> questions = Exam.parseQuestions(draft.questionsJson);
        showStep2(questions);
    }

    private String formatIsoToDisplay(String isoString) {
        if (isoString == null || isoString.isEmpty()) return "";
        try {
            Date date = DateTimeUtils.parseIso8601(isoString);
            if (date != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                return sdf.format(date);
            }
        } catch (Exception ignored) {}
        return "";
    }

    private void addNewManualQuestion() {
        Question blank = new Question();
        blank.id = String.valueOf(questionsAdapter.getItemCount());
        blank.questionText = "";
        blank.options = new ArrayList<>(Arrays.asList("", "", "", ""));
        blank.correctIndex = 0;

        questionsAdapter.getQuestions().add(blank);
        questionsAdapter.notifyItemInserted(questionsAdapter.getItemCount() - 1);
        binding.rvGeneratedQuestions.smoothScrollToPosition(questionsAdapter.getItemCount() - 1);
        updateQuestionCountHeader();
    }

    private static class PaperResponse {
        @SerializedName("sections")
        public List<PaperSection> sections;

        @SerializedName("questions")
        public List<PaperQuestion> questions;
    }

    private static class PaperQuestion {
        @SerializedName("id")
        public String id;

        @SerializedName("section")
        public String section;

        @SerializedName("questionText")
        public String questionText;

        @SerializedName("options")
        public List<String> options;

        @SerializedName("correctIndex")
        public Integer correctIndex;

        @SerializedName("marks")
        public Integer marks;
    }
}

package com.arshad.studdy_app_android_only.ui.teacher.create;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
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
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.arshad.studdy_app_android_only.BuildConfig;
import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.data.model.PaperConfig;
import com.arshad.studdy_app_android_only.data.model.PaperSection;
import com.arshad.studdy_app_android_only.data.model.GeneratedPaper;
import com.arshad.studdy_app_android_only.data.model.Question;
import com.arshad.studdy_app_android_only.data.remote.api.GeminiApi;
import com.arshad.studdy_app_android_only.data.remote.api.PaperApi;
import com.arshad.studdy_app_android_only.data.remote.dto.GeminiRequest;
import com.arshad.studdy_app_android_only.data.remote.dto.GeminiResponse;
import com.arshad.studdy_app_android_only.databinding.ActivityPaperGeneratorBinding;
import com.arshad.studdy_app_android_only.network.GeminiClient;
import com.arshad.studdy_app_android_only.network.SupabaseClient;
import com.arshad.studdy_app_android_only.ui.BaseActivity;
import com.arshad.studdy_app_android_only.util.SessionManager;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PaperGeneratorActivity extends BaseActivity {

    private static final String TAG = "PaperGeneratorActivity";
    private static final int PDF_PAGE_WIDTH = 595;
    private static final int PDF_PAGE_HEIGHT = 842;
    private static final float PDF_MARGIN = 42f;
    private static final float PDF_BOTTOM_MARGIN = 48f;

    private ActivityPaperGeneratorBinding binding;
    private SessionManager sessionManager;
    private PaperConfig paperConfig;
    private PaperSectionAdapter adapter;
    private String selectedPdfBase64 = null;
    private boolean loading = false;

    private final ExecutorService pdfExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        binding = ActivityPaperGeneratorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = SessionManager.getInstance(this);
        paperConfig = new PaperConfig();
        paperConfig.overallDifficulty = "Balanced";

        setupToolbar();
        setupSpinner();
        setupRecyclerView();
        setupClickListeners();
        updatePaperSummary();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupSpinner() {
        ArrayAdapter<CharSequence> templateAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.paper_templates,
                android.R.layout.simple_spinner_item);
        templateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerTemplate.setAdapter(templateAdapter);
        binding.spinnerTemplate.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                paperConfig.template = parent.getItemAtPosition(position).toString();
                updateStepper(2);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupRecyclerView() {
        adapter = new PaperSectionAdapter(position -> {
            paperConfig.removeSection(position);
            adapter.setSections(paperConfig.sections);
            updatePaperSummary();
        });
        binding.rvSections.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSections.setAdapter(adapter);
    }

    private void setupClickListeners() {
        binding.btnChoosePdf.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/pdf");
            pdfPickerLauncher.launch(intent);
        });

        binding.toggleOverallDifficulty.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_diff_easy) paperConfig.overallDifficulty = "Easy";
            else if (checkedId == R.id.btn_diff_balanced) paperConfig.overallDifficulty = "Balanced";
            else if (checkedId == R.id.btn_diff_tough) paperConfig.overallDifficulty = "Tough";
            updateStepper(2);
        });

        binding.btnAddSection.setOnClickListener(v -> showAddSectionDialog());
        binding.btnAddSectionHeader.setOnClickListener(v -> showAddSectionDialog());

        binding.btnGenerateWithAi.setOnClickListener(v -> attemptPaperGeneration());
    }

    private void showAddSectionDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_section, null);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        dialog.show();

        // Initialize dialog views
        com.google.android.material.textfield.TextInputEditText etSectionName = dialogView.findViewById(R.id.et_section_name);
        Spinner spinnerType = dialogView.findViewById(R.id.spinner_question_type);
        TextView tvCount = dialogView.findViewById(R.id.tv_section_count);
        TextView tvMarks = dialogView.findViewById(R.id.tv_marks_per_question);
        Spinner spinnerDiff = dialogView.findViewById(R.id.spinner_section_difficulty);
        ImageButton btnCountDec = dialogView.findViewById(R.id.btn_section_count_decrement);
        ImageButton btnCountInc = dialogView.findViewById(R.id.btn_section_count_increment);
        ImageButton btnMarksDec = dialogView.findViewById(R.id.btn_marks_decrement);
        ImageButton btnMarksInc = dialogView.findViewById(R.id.btn_marks_increment);
        View btnAdd = dialogView.findViewById(R.id.btn_add_section_dialog);

        ArrayAdapter<CharSequence> typeAdapter = ArrayAdapter.createFromResource(
                this, R.array.section_question_types, android.R.layout.simple_spinner_item);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(typeAdapter);

        String[] difficulties = {"Easy", "Medium", "Hard"};
        ArrayAdapter<String> diffAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, difficulties);
        diffAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDiff.setAdapter(diffAdapter);
        spinnerDiff.setSelection(1); // Medium

        final int[] count = {5};
        final int[] marks = {2};

        btnCountDec.setOnClickListener(v -> { if (count[0] > 1) { count[0]--; tvCount.setText(String.valueOf(count[0])); } });
        btnCountInc.setOnClickListener(v -> { if (count[0] < 50) { count[0]++; tvCount.setText(String.valueOf(count[0])); } });
        btnMarksDec.setOnClickListener(v -> { if (marks[0] > 1) { marks[0]--; tvMarks.setText(String.valueOf(marks[0])); } });
        btnMarksInc.setOnClickListener(v -> { if (marks[0] < 20) { marks[0]++; tvMarks.setText(String.valueOf(marks[0])); } });

        btnAdd.setOnClickListener(v -> {
            String name = etSectionName.getText().toString().trim();
            if (name.isEmpty()) { etSectionName.setError("Name required"); return; }

            String type = spinnerType.getSelectedItem().toString();
            String diff = spinnerDiff.getSelectedItem().toString();
            PaperSection section = new PaperSection(
                    String.valueOf((char)('A' + paperConfig.sections.size())),
                    name, type, count[0], marks[0], diff);
            
            paperConfig.addSection(section);
            adapter.setSections(paperConfig.sections);
            updatePaperSummary();
            updateStepper(3);
            dialog.dismiss();
        });
    }

    private void updatePaperSummary() {
        binding.tvTotalMarks.setText(paperConfig.calculateTotalMarks() + " marks");
        binding.rvSections.setVisibility(paperConfig.sections.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void updateStepper(int step) {
        if (step >= 1) {
            binding.tvStep1Number.setBackgroundResource(R.drawable.bg_circle_primary);
            binding.tvStep1Number.setTextColor(getResources().getColor(R.color.white));
            binding.tvStep1Label.setTextColor(getResources().getColor(R.color.text_primary));
        }
        if (step >= 2) {
            binding.tvStep2Number.setBackgroundResource(R.drawable.bg_circle_primary);
            binding.tvStep2Number.setTextColor(getResources().getColor(R.color.white));
            binding.tvStep2Label.setTextColor(getResources().getColor(R.color.text_primary));
        }
        if (step >= 3 && !paperConfig.sections.isEmpty()) {
            binding.tvStep2Number.setVisibility(View.GONE);
            binding.ivStep2Check.setVisibility(View.VISIBLE);
        }
    }

    private void handleSelectedPdf(Uri uri) {
        binding.tvPdfName.setVisibility(View.VISIBLE);
        binding.tvPdfName.setText("Reading PDF...");
        setLoading(true, "Processing PDF...");

        pdfExecutor.execute(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                byte[] bytes = readAllBytes(is);
                selectedPdfBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                mainHandler.post(() -> {
                    setLoading(false, null);
                    binding.tvPdfName.setText(getFileName(uri));
                    updateStepper(2);
                });
            } catch (IOException e) {
                mainHandler.post(() -> {
                    setLoading(false, null);
                    Toast.makeText(this, "Failed to read PDF", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void attemptPaperGeneration() {
        paperConfig.title = binding.etPaperTitle.getText().toString().trim();
        if (paperConfig.title.isEmpty()) {
            binding.tilPaperTitle.setError("Title required");
            return;
        }
        if (selectedPdfBase64 == null) {
            Toast.makeText(this, "Please upload a syllabus PDF first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (paperConfig.sections.isEmpty()) {
            Toast.makeText(this, "Please add at least one section", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true, "AI is generating your exam paper...");
        
        String prompt = buildPrompt();
        GeminiApi api = GeminiClient.getInstance().createService(GeminiApi.class);
        api.generateContent(BuildConfig.GEMINI_API_KEY, new GeminiRequest(prompt, selectedPdfBase64))
                .enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    handleAiResponse(response.body().getGeneratedText());
                } else {
                    setLoading(false, null);
                    Toast.makeText(PaperGeneratorActivity.this, "Generation failed", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<GeminiResponse> call, Throwable t) {
                setLoading(false, null);
                Toast.makeText(PaperGeneratorActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleAiResponse(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            setLoading(false, null);
            Toast.makeText(this, "Empty response from AI", Toast.LENGTH_SHORT).show();
            return;
        }

        String cleanJson = rawText.trim();
        if (cleanJson.startsWith("```json")) cleanJson = cleanJson.substring(7);
        if (cleanJson.endsWith("```")) cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
        cleanJson = cleanJson.trim();

        try {
            PaperResponse response = new Gson().fromJson(cleanJson, PaperResponse.class);
            if (response != null && response.sections != null) {
                // Map generated questions to our sections
                for (PaperSection section : paperConfig.sections) {
                    section.questions = new ArrayList<>();
                    for (SectionResponse sr : response.sections) {
                        if (sr.id != null && sr.id.equalsIgnoreCase(section.id)) {
                            if (sr.questions != null) {
                                for (int i = 0; i < sr.questions.size(); i++) {
                                    QuestionResponse qr = sr.questions.get(i);
                                    Question q = new Question(
                                            String.valueOf(i + 1),
                                            qr.questionText,
                                            qr.options != null ? qr.options : new ArrayList<>(),
                                            qr.correctIndex != null ? qr.correctIndex : 0
                                    );
                                    section.questions.add(q);
                                }
                            }
                        }
                    }
                }
                createUploadAndSavePaperPdf();
            } else {
                setLoading(false, null);
                Toast.makeText(this, "AI response format mismatch", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse AI response", e);
            setLoading(false, null);
            Toast.makeText(this, "Error parsing AI response", Toast.LENGTH_SHORT).show();
        }
    }

    private String buildPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate an exam paper based on the attached PDF document. ");
        sb.append("Use ONLY information present in the source.\n\n");
        sb.append("Overall Paper Difficulty: ").append(paperConfig.overallDifficulty).append("\n\n");
        
        sb.append("Generate questions for the following sections:\n");
        for (PaperSection section : paperConfig.sections) {
            sb.append("- Section ").append(section.id).append(" (").append(section.title).append("): ");
            sb.append(section.questionCount).append(" questions of type ").append(section.questionType);
            sb.append(", each for ").append(section.marksPerQuestion).append(" marks. ");
            sb.append("Difficulty: ").append(section.difficulty).append(".\n");
        }

        sb.append("\nReturn ONLY valid JSON, matching this structure:\n");
        sb.append("{\n");
        sb.append("  \"sections\": [\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"A\",\n");
        sb.append("      \"questions\": [\n");
        sb.append("        {\n");
        sb.append("          \"questionText\": \"Question text here\",\n");
        sb.append("          \"options\": [\"Option 1\", \"Option 2\", \"Option 3\", \"Option 4\"],\n");
        sb.append("          \"correctIndex\": 0\n");
        sb.append("        }\n");
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        sb.append("For non-MCQ types, 'options' should be an empty array and 'correctIndex' can be 0.");

        return sb.toString();
    }

    private void createUploadAndSavePaperPdf() {
        setLoading(true, "Creating paper PDF...");
        pdfExecutor.execute(() -> {
            try {
                byte[] pdfBytes = renderGeneratedPaperPdf();
                String fileName = makeSafePdfObjectName(paperConfig.title);
                mainHandler.post(() -> uploadPaperPdfToSupabase(fileName, pdfBytes));
            } catch (IOException e) {
                Log.e(TAG, "Failed to create paper PDF", e);
                mainHandler.post(() -> {
                    setLoading(false, null);
                    Toast.makeText(this, "Failed to create paper PDF", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private byte[] renderGeneratedPaperPdf() throws IOException {
        PdfDocument document = new PdfDocument();
        PdfRenderState state = new PdfRenderState(document);
        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTextSize(20f);
        titlePaint.setFakeBoldText(true);

        Paint headingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headingPaint.setTextSize(14f);
        headingPaint.setFakeBoldText(true);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(11f);

        try {
            startPdfPage(state);
            drawPdfWrappedText(state, paperConfig.title, titlePaint, 0f, 26f);
            drawPdfWrappedText(
                    state,
                    "Template: " + safeText(paperConfig.template)
                            + "    Difficulty: " + safeText(paperConfig.overallDifficulty)
                            + "    Total Marks: " + paperConfig.calculateTotalMarks(),
                    textPaint,
                    0f,
                    17f);
            state.y += 10f;

            for (PaperSection section : paperConfig.sections) {
                drawPdfWrappedText(
                        state,
                        "Section " + safeText(section.id) + ": " + safeText(section.title)
                                + " (" + section.questionCount + " questions × "
                                + section.marksPerQuestion + " marks)",
                        headingPaint,
                        0f,
                        20f);

                if (section.questions == null || section.questions.isEmpty()) {
                    drawPdfWrappedText(state, "No questions generated for this section.", textPaint, 14f, 16f);
                    continue;
                }

                for (int i = 0; i < section.questions.size(); i++) {
                    Question question = section.questions.get(i);
                    drawPdfWrappedText(
                            state,
                            (i + 1) + ". " + safeText(question.questionText),
                            textPaint,
                            0f,
                            16f);
                    if (question.options != null) {
                        for (int optionIndex = 0; optionIndex < question.options.size(); optionIndex++) {
                            String optionLabel = String.valueOf((char) ('A' + optionIndex));
                            drawPdfWrappedText(
                                    state,
                                    optionLabel + ". " + safeText(question.options.get(optionIndex)),
                                    textPaint,
                                    18f,
                                    16f);
                        }
                    }
                    state.y += 8f;
                }
                state.y += 8f;
            }

            // Finish active content page
            finishPdfPage(state);

            // Start a new page for Answer Key
            startPdfPage(state);
            drawPdfWrappedText(state, "Answer Key", titlePaint, 0f, 26f);
            state.y += 10f;

            for (PaperSection section : paperConfig.sections) {
                drawPdfWrappedText(
                        state,
                        "Section " + safeText(section.id) + ": " + safeText(section.title),
                        headingPaint,
                        0f,
                        20f);

                if (section.questions == null || section.questions.isEmpty()) {
                    continue;
                }

                for (int i = 0; i < section.questions.size(); i++) {
                    Question question = section.questions.get(i);
                    String answerText = "";
                    if (question.options != null && !question.options.isEmpty() && question.correctIndex >= 0 && question.correctIndex < question.options.size()) {
                        String optionLabel = String.valueOf((char) ('A' + question.correctIndex));
                        answerText = optionLabel + ". " + question.options.get(question.correctIndex);
                    } else {
                        answerText = "Model Answer Index: " + question.correctIndex;
                    }
                    drawPdfWrappedText(
                            state,
                            "Q" + (i + 1) + ". " + safeText(question.questionText),
                            textPaint,
                            0f,
                            16f);
                    drawPdfWrappedText(
                            state,
                            "Correct Answer: " + answerText,
                            textPaint,
                            14f,
                            16f);
                    state.y += 6f;
                }
                state.y += 8f;
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            finishPdfPage(state);
            document.writeTo(output);
            return output.toByteArray();
        } finally {
            document.close();
        }
    }

    private void startPdfPage(PdfRenderState state) {
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                PDF_PAGE_WIDTH, PDF_PAGE_HEIGHT, state.pageNumber++).create();
        state.page = state.document.startPage(pageInfo);
        state.canvas = state.page.getCanvas();
        state.y = PDF_MARGIN;
    }

    private void finishPdfPage(PdfRenderState state) {
        if (state.page != null) {
            state.document.finishPage(state.page);
            state.page = null;
            state.canvas = null;
        }
    }

    private void ensurePdfLineSpace(PdfRenderState state, float lineHeight) {
        if (state.y + lineHeight > PDF_PAGE_HEIGHT - PDF_BOTTOM_MARGIN) {
            finishPdfPage(state);
            startPdfPage(state);
        }
    }

    private void drawPdfWrappedText(
            PdfRenderState state,
            String text,
            Paint paint,
            float indent,
            float lineHeight) {
        float availableWidth = PDF_PAGE_WIDTH - PDF_MARGIN - indent - PDF_MARGIN;
        String[] paragraphs = safeText(text).split("\\r?\\n", -1);
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                ensurePdfLineSpace(state, lineHeight);
                state.y += lineHeight;
                continue;
            }

            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split("\\s+")) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (line.length() > 0 && paint.measureText(candidate) > availableWidth) {
                    drawPdfLine(state, line.toString(), paint, indent, lineHeight);
                    line.setLength(0);
                    line.append(word);
                } else {
                    line.setLength(0);
                    line.append(candidate);
                }
            }
            if (line.length() > 0) {
                drawPdfLine(state, line.toString(), paint, indent, lineHeight);
            }
        }
    }

    private void drawPdfLine(
            PdfRenderState state,
            String line,
            Paint paint,
            float indent,
            float lineHeight) {
        ensurePdfLineSpace(state, lineHeight);
        state.canvas.drawText(line, PDF_MARGIN + indent, state.y, paint);
        state.y += lineHeight;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private void uploadPaperPdfToSupabase(String fileName, byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            setLoading(false, null);
            Toast.makeText(this, "Generated paper PDF is empty", Toast.LENGTH_LONG).show();
            return;
        }

        setLoading(true, "Uploading paper PDF...");
        String uploadUrl = BuildConfig.SUPABASE_URL + "/storage/v1/object/exam-papers/" + fileName;
        RequestBody fileBody = RequestBody.create(pdfBytes, MediaType.parse("application/pdf"));

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
                Log.e(TAG, "Paper PDF upload failed", e);
                mainHandler.post(() -> {
                    setLoading(false, null);
                    Toast.makeText(
                            PaperGeneratorActivity.this,
                            "Paper PDF upload failed: " + safeText(e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    Log.e(TAG, "Paper PDF upload failed: HTTP " + response.code() + " " + errorBody);
                    mainHandler.post(() -> {
                        setLoading(false, null);
                        Toast.makeText(
                                PaperGeneratorActivity.this,
                                "Paper PDF upload failed: HTTP " + response.code(),
                                Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                String publicUrl = BuildConfig.SUPABASE_URL
                        + "/storage/v1/object/public/exam-papers/" + fileName;
                mainHandler.post(() -> savePaperToSupabase(publicUrl));
            }
        });
    }

    private String makeSafePdfObjectName(String title) {
        String baseName = safeText(title).trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (baseName.isEmpty()) baseName = "exam-paper";
        return "paper-" + System.currentTimeMillis() + "-" + baseName + ".pdf";
    }

    private void savePaperToSupabase(String pdfUrl) {
        setLoading(true, "Saving paper...");
        GeneratedPaper paper = new GeneratedPaper();
        paper.title = paperConfig.title;
        paper.template = paperConfig.template;
        paper.difficulty = paperConfig.overallDifficulty;
        paper.totalMarks = paperConfig.calculateTotalMarks();
        paper.teacherId = sessionManager.getTeacherId();
        paper.pdfUrl = pdfUrl;
        paper.sections = paperConfig.sections;
        
        // Extract all questions for the 'questions' JSONB field
        List<Question> allQuestions = new ArrayList<>();
        for (PaperSection section : paperConfig.sections) {
            if (section.questions != null) {
                allQuestions.addAll(section.questions);
            }
        }
        paper.questions = allQuestions;

        PaperApi api = SupabaseClient.createAuthService(PaperApi.class, sessionManager.getAccessToken());
        api.createPaper(paper).enqueue(new Callback<List<GeneratedPaper>>() {
            @Override
            public void onResponse(Call<List<GeneratedPaper>> call, Response<List<GeneratedPaper>> response) {
                setLoading(false, null);
                if (response.isSuccessful()) {
                    showSuccessDialog(paper);
                } else {
                    Toast.makeText(PaperGeneratorActivity.this, "Failed to save paper: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<GeneratedPaper>> call, Throwable t) {
                setLoading(false, null);
                Toast.makeText(PaperGeneratorActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showSuccessDialog(GeneratedPaper paper) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_paper_success, null);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        TextView tvTitle = dialogView.findViewById(R.id.tv_success_paper_title);
        TextView tvCount = dialogView.findViewById(R.id.tv_success_question_count);
        TextView tvMarks = dialogView.findViewById(R.id.tv_success_total_marks);
        View btnViewPdf = dialogView.findViewById(R.id.btn_view_pdf);
        View btnDone = dialogView.findViewById(R.id.btn_done);

        tvTitle.setText(paper.title);
        
        int questionCount = 0;
        if (paper.questions instanceof List) {
            questionCount = ((List<?>) paper.questions).size();
        }
        tvCount.setText("Questions: " + questionCount);
        tvMarks.setText("Total Marks: " + paper.totalMarks);

        btnViewPdf.setOnClickListener(v -> {
            if (paper.pdfUrl != null && !paper.pdfUrl.isEmpty()) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(paper.pdfUrl));
                startActivity(intent);
            } else {
                Toast.makeText(this, "PDF URL is empty", Toast.LENGTH_SHORT).show();
            }
        });

        btnDone.setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });

        dialog.show();
    }

    private void setLoading(boolean loading, String message) {
        this.loading = loading;
        binding.layoutLoadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.tvLoadingLabel.setText(message);
    }

    private static final class PdfRenderState {
        final PdfDocument document;
        int pageNumber = 1;
        PdfDocument.Page page;
        Canvas canvas;
        float y;

        PdfRenderState(PdfDocument document) {
            this.document = document;
        }
    }

    private long getPdfFileSize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getLong(0);
        } catch (Exception ignored) {}
        return 0;
    }

    private String getFileName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) {}
        return "syllabus.pdf";
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) baos.write(buffer, 0, len);
        return baos.toByteArray();
    }

    // Response Classes for parsing
    private static class PaperResponse {
        @SerializedName("sections")
        List<SectionResponse> sections;
    }

    private static class SectionResponse {
        @SerializedName("id")
        String id;
        @SerializedName("questions")
        List<QuestionResponse> questions;
    }

    private static class QuestionResponse {
        @SerializedName("questionText")
        String questionText;
        @SerializedName("options")
        List<String> options;
        @SerializedName("correctIndex")
        Integer correctIndex;
    }
}

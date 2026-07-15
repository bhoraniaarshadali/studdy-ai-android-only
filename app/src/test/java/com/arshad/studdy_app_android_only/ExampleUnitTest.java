package com.arshad.studdy_app_android_only;

import org.junit.Test;

import static org.junit.Assert.*;

import com.arshad.studdy_app_android_only.data.model.Exam;
import com.arshad.studdy_app_android_only.data.model.ExamSession;
import com.arshad.studdy_app_android_only.data.model.GeneratedPaper;
import com.arshad.studdy_app_android_only.data.model.Question;
import com.arshad.studdy_app_android_only.data.remote.api.AuthApi;
import com.arshad.studdy_app_android_only.data.remote.api.ExamApi;
import com.arshad.studdy_app_android_only.data.remote.api.ExamSessionApi;
import com.arshad.studdy_app_android_only.data.remote.api.GeminiApi;
import com.arshad.studdy_app_android_only.data.remote.api.PaperApi;
import com.arshad.studdy_app_android_only.data.remote.dto.AuthResponse;
import com.arshad.studdy_app_android_only.data.remote.dto.GeminiRequest;
import com.arshad.studdy_app_android_only.data.remote.dto.GeminiResponse;
import com.arshad.studdy_app_android_only.data.remote.dto.SignUpRequest;
import com.arshad.studdy_app_android_only.network.GeminiClient;
import com.arshad.studdy_app_android_only.network.SupabaseClient;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Response;

/**
 * End-to-End QA Integration Tests executing live network transactions.
 */
public class ExampleUnitTest {

    private static String sessionAccessToken = null;
    private static String sessionUserId = null;
    private static String testExamCode = null;
    private static String testExamId = null;

    @Test
    public void testA1_TeacherSignUpAndAuth() throws IOException {
        System.out.println("--- E2E TEST: A1. Teacher Sign-Up & Token Retrieval ---");
        
        String uniqueEmail = "qa_teacher_" + System.currentTimeMillis() + "@studdytest.com";
        String password = "Password123!";
        String name = "QA Automated Test Teacher";

        SignUpRequest request = new SignUpRequest(uniqueEmail, password, name);
        AuthApi authApi = SupabaseClient.getAuthApiClient().create(AuthApi.class);
        
        Response<AuthResponse> response = authApi.signUp(request).execute();
        
        assertTrue("Teacher sign-up request failed: HTTP " + response.code(), response.isSuccessful());
        assertNotNull("AuthResponse body is null", response.body());
        assertNotNull("access_token is null", response.body().accessToken);
        
        sessionAccessToken = response.body().accessToken;
        sessionUserId = response.body().user.id;
        
        System.out.println("Teacher signed up successfully. UID: " + sessionUserId);
    }

    @Test
    public void testA2_TeacherSignUpWeakPassword() throws IOException {
        System.out.println("--- E2E TEST: A2. Sign-Up Weak Password Validation ---");
        
        String uniqueEmail = "qa_weak_" + System.currentTimeMillis() + "@studdytest.com";
        String weakPassword = "123"; // Below 6 characters min requirement in Supabase
        
        SignUpRequest request = new SignUpRequest(uniqueEmail, weakPassword, "Weak Tester");
        AuthApi authApi = SupabaseClient.getAuthApiClient().create(AuthApi.class);
        
        Response<AuthResponse> response = authApi.signUp(request).execute();
        assertFalse("Sign-up with weak password should have failed", response.isSuccessful());
        assertEquals("Expected HTTP 422 for weak password registration", 422, response.code());
        
        String errorJson = response.errorBody() != null ? response.errorBody().string() : "";
        AuthResponse.AuthError authError = new Gson().fromJson(errorJson, AuthResponse.AuthError.class);
        assertNotNull(authError);
        System.out.println("Supabase Auth rejected weak password: " + authError.getBestMessage());
    }

    @Test
    public void testB_GeminiMCQGeneration() throws IOException {
        System.out.println("--- E2E TEST: B. Gemini MCQ Generation (REST structure check) ---");
        
        String sampleText = "Photosynthesis is a process used by plants and other organisms to convert light energy into chemical energy that, through cellular respiration, can later be released to fuel the organism's activities. This chemical energy is stored in carbohydrate molecules, such as sugars, which are synthesized from carbon dioxide and water.";
        
        String prompt = "Generate exactly 3 multiple choice questions based on the following text. " +
                "The output MUST be a JSON array of objects. Each object must contain exactly: " +
                "\"questionText\" (string), \"options\" (array of exactly 4 strings), and \"correctIndex\" (integer from 0 to 3 indicating the correct answer). " +
                "Do not include any markdown format tags like ```json or wrappers, just output raw JSON text. " +
                "Text:\n" + sampleText;

        GeminiApi api = GeminiClient.getInstance().createService(GeminiApi.class);
        Response<GeminiResponse> response = api.generateContent(BuildConfig.GEMINI_API_KEY, new GeminiRequest(prompt)).execute();
        
        System.out.println("Gemini MCQ response code: " + response.code());
        if (!response.isSuccessful() && response.errorBody() != null) {
            System.out.println("Gemini error body: " + response.errorBody().string());
        }
        assertTrue(response.isSuccessful());
        assertNotNull(response.body());
        String rawText = response.body().getGeneratedText();
        assertNotNull(rawText);
        System.out.println("Gemini MCQ generation succeeded: " + rawText);
    }

    @Test
    public void test_ListModels() throws IOException {
        System.out.println("--- LIST MODELS TEST ---");
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?key=" + BuildConfig.GEMINI_API_KEY)
                .build();
        okhttp3.Response response = client.newCall(request).execute();
        System.out.println("List Models Response code: " + response.code());
        if (response.body() != null) {
            System.out.println("List Models Response body: " + response.body().string());
        }
    }

    @Test
    public void testC_SupabaseExamsFlow() throws IOException {
        System.out.println("--- E2E TEST: C. Supabase Exams PostgREST RLS Flow ---");
        
        if (sessionAccessToken == null) {
            testA1_TeacherSignUpAndAuth();
        }

        testExamCode = "QA" + (System.currentTimeMillis() % 10000);

        Exam exam = new Exam();
        exam.title = "QA E2E Verified Exam";
        exam.code = testExamCode;
        exam.timerMode = "none";
        exam.resultMode = "instant";
        exam.resultsPublished = false;
        exam.teacherId = sessionUserId;
        
        List<Question> questionsList = new ArrayList<>();
        Question q = new Question();
        q.id = "0";
        q.questionText = "Test Question?";
        q.options = Arrays.asList("A", "B", "C", "D");
        q.correctIndex = 0;
        questionsList.add(q);
        exam.questions = questionsList;

        ExamApi api = SupabaseClient.createAuthService(ExamApi.class, sessionAccessToken);
        
        // 1. Insert exam
        Response<List<Exam>> insertResponse = api.insertExam(exam).execute();
        assertTrue("Authenticated insert exam failed: HTTP " + insertResponse.code(), insertResponse.isSuccessful());
        assertNotNull(insertResponse.body());
        assertFalse(insertResponse.body().isEmpty());
        testExamId = insertResponse.body().get(0).id;
        System.out.println("Exam inserted successfully. ID: " + testExamId + " | Code: " + testExamCode);
    }

    @Test
    public void testD1_StudentJoinExamAnon() throws IOException {
        System.out.println("--- E2E TEST: D1. Student Join / Fetch Exam by Code (Anonymous) ---");
        
        if (testExamCode == null) {
            testC_SupabaseExamsFlow();
        }

        ExamApi anonApi = SupabaseClient.getInstance().createAnonService(ExamApi.class);
        Response<List<Exam>> response = anonApi.getExamByCode("eq." + testExamCode, "*").execute();
        
        assertTrue("Student fetch exam failed: HTTP " + response.code(), response.isSuccessful());
        assertNotNull(response.body());
        assertFalse(response.body().isEmpty());
        assertEquals("Exam code mismatch", testExamCode, response.body().get(0).code);
        System.out.println("Student fetched exam details successfully using code: " + testExamCode);
    }

    @Test
    public void testD3_StudentExamSessionAndWarnings() throws IOException {
        System.out.println("--- E2E TEST: D3. Student Session Auto-Saves & Proctoring Warnings Log ---");
        
        if (testExamCode == null) {
            testC_SupabaseExamsFlow();
        }

        // Setup mock Exam Session
        ExamSession session = new ExamSession();
        session.examCode = testExamCode;
        session.studentName = "QA Student";
        session.enrollmentNumber = "QAENR" + System.currentTimeMillis();
        session.status = "active";
        session.lastQuestionIndex = 0;
        session.warnings = 0;
        
        List<Integer> answers = new ArrayList<>();
        answers.add(1); // answer to question 0 is index 1
        session.currentAnswers = answers;

        List<ExamSession.WarningEvent> warningLogs = new ArrayList<>();
        warningLogs.add(new ExamSession.WarningEvent(ExamSession.WARN_FACE_NOT_DETECTED, "2026-07-04T05:00:00Z"));
        session.warningsLog = warningLogs;

        ExamSessionApi anonApi = SupabaseClient.getInstance().createAnonService(ExamSessionApi.class);
        
        // 1. Insert session
        Response<List<ExamSession>> insertResponse = anonApi.createSession(session).execute();
        assertTrue("Create exam session failed: HTTP " + insertResponse.code(), insertResponse.isSuccessful());
        assertNotNull(insertResponse.body());
        assertFalse(insertResponse.body().isEmpty());
        long sessionId = insertResponse.body().get(0).id;
        System.out.println("Exam session created successfully with ID: " + sessionId);

        // 2. Perform updates (mock auto-save answers & warnings)
        Map<String, Object> updates = new HashMap<>();
        
        List<Integer> newAnswers = new ArrayList<>();
        newAnswers.add(2); // Change answer to index 2
        updates.put("current_answers", newAnswers);
        
        warningLogs.add(new ExamSession.WarningEvent(ExamSession.WARN_LOOKING_AWAY, "2026-07-04T05:00:10Z"));
        updates.put("warnings_log", warningLogs);
        updates.put("warnings", 2);

        Response<List<ExamSession>> updateResponse = anonApi.updateSession("eq." + sessionId, updates).execute();
        assertTrue("Update/Auto-save session failed: HTTP " + updateResponse.code(), updateResponse.isSuccessful());
        assertNotNull(updateResponse.body());
        assertFalse(updateResponse.body().isEmpty());
        
        ExamSession updated = updateResponse.body().get(0);
        assertEquals("Warnings count mismatch after update", 2, updated.warnings);
        System.out.println("Mock auto-save and warnings logged successfully for session ID: " + sessionId);

        // Clean up session
        Response<Void> deleteSessionResponse = anonApi.deleteSession("eq." + sessionId).execute();
        assertTrue(deleteSessionResponse.isSuccessful());
        System.out.println("Exam session cleaned up successfully.");

        // Clean up exam
        ExamApi examApi = SupabaseClient.createAuthService(ExamApi.class, sessionAccessToken);
        Response<Void> deleteExamResponse = examApi.deleteExam("eq." + testExamId).execute();
        assertTrue(deleteExamResponse.isSuccessful());
        System.out.println("Exam cleaned up successfully.");
    }

    @Test
    public void testE_SupabasePapersFlow() throws IOException {
        System.out.println("--- E2E TEST: E. Supabase Printable Papers RLS Flow ---");
        
        if (sessionAccessToken == null) {
            testA1_TeacherSignUpAndAuth();
        }

        GeneratedPaper paper = new GeneratedPaper();
        paper.title = "QA E2E Printable Paper";
        paper.totalMarks = 100;
        paper.template = "Standard";
        paper.difficulty = "Medium";
        paper.teacherId = sessionUserId;
        paper.pdfUrl = "https://odbycjunebfncpkkbbew.supabase.co/storage/v1/object/public/exam-papers/mock.pdf";

        PaperApi api = SupabaseClient.createAuthService(PaperApi.class, sessionAccessToken);

        // 1. Insert Paper
        Response<List<GeneratedPaper>> insertResponse = api.createPaper(paper).execute();
        assertTrue("Authenticated insert paper failed: HTTP " + insertResponse.code(), insertResponse.isSuccessful());
        assertNotNull(insertResponse.body());
        assertFalse(insertResponse.body().isEmpty());
        long insertedId = insertResponse.body().get(0).id;
        System.out.println("Printable paper created successfully with ID: " + insertedId);

        // 2. Fetch list of papers
        Response<List<GeneratedPaper>> queryResponse = api.getPapers("*", "created_at.desc").execute();
        assertTrue("Fetch papers list failed", queryResponse.isSuccessful());
        assertNotNull(queryResponse.body());
        System.out.println("Fetched " + queryResponse.body().size() + " papers.");

        // 3. Delete Paper
        Response<Void> deleteResponse = api.deletePaper("eq." + insertedId).execute();
        assertTrue(deleteResponse.isSuccessful());
        System.out.println("Printable paper cleaned up successfully.");
    }
}
package com.arshad.studdy_app_android_only.data.repository;

import com.arshad.studdy_app_android_only.data.model.Exam;
import com.arshad.studdy_app_android_only.data.model.ExamSession;
import com.arshad.studdy_app_android_only.data.model.Result;
import com.arshad.studdy_app_android_only.data.model.Student;
import com.arshad.studdy_app_android_only.data.remote.api.ExamApi;
import com.arshad.studdy_app_android_only.data.remote.api.ExamSessionApi;
import com.arshad.studdy_app_android_only.data.remote.api.ResultApi;
import com.arshad.studdy_app_android_only.data.remote.api.StudentApi;
import com.arshad.studdy_app_android_only.network.SupabaseClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Repository for student-related network operations.
 * Communicates with PostgREST endpoints using the anonymous client.
 */
public class StudentRepository {

    public interface Callback<T> {
        void onSuccess(T result);
        void onFailure(String errorMessage);
    }

    private final ExamApi examApi;
    private final StudentApi studentApi;
    private final ExamSessionApi sessionApi;
    private final ResultApi resultApi;

    public StudentRepository() {
        SupabaseClient client = SupabaseClient.getInstance();
        this.examApi = client.createAnonService(ExamApi.class);
        this.studentApi = client.createAnonService(StudentApi.class);
        this.sessionApi = client.createAnonService(ExamSessionApi.class);
        this.resultApi = client.createAnonService(ResultApi.class);
    }

    /**
     * Check if exam exists for a given code.
     */
    public void getExamByCode(String code, Callback<Exam> callback) {
        examApi.getExamByCode("eq." + code, "*").enqueue(new retrofit2.Callback<List<Exam>>() {
            @Override
            public void onResponse(Call<List<Exam>> call, Response<List<Exam>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Exam> list = response.body();
                    if (!list.isEmpty()) {
                        callback.onSuccess(list.get(0));
                    } else {
                        callback.onFailure("EXAM_NOT_FOUND");
                    }
                } else {
                    callback.onFailure("HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<Exam>> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Get all exams (e.g. for student dashboard).
     */
    public void getAllExams(Callback<List<Exam>> callback) {
        examApi.getAllExams("*", "created_at.desc").enqueue(new retrofit2.Callback<List<Exam>>() {
            @Override
            public void onResponse(Call<List<Exam>> call, Response<List<Exam>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure("HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<Exam>> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Check if student exists. If so, updates name (if different); otherwise creates student.
     */
    public void checkOrCreateStudent(String enrollment, String name, Callback<Student> callback) {
        studentApi.getByEnrollment("eq." + enrollment, "*").enqueue(new retrofit2.Callback<List<Student>>() {
            @Override
            public void onResponse(Call<List<Student>> call, Response<List<Student>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Student> list = response.body();
                    if (!list.isEmpty()) {
                        Student existing = list.get(0);
                        if (!name.equals(existing.name)) {
                            // Update name
                            updateStudentName(enrollment, name, callback);
                        } else {
                            callback.onSuccess(existing);
                        }
                    } else {
                        // Create student
                        insertStudent(enrollment, name, callback);
                    }
                } else {
                    callback.onFailure("HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<Student>> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }

    private void insertStudent(String enrollment, String name, Callback<Student> callback) {
        Student newStudent = new Student(enrollment, name);
        studentApi.insertStudent(newStudent).enqueue(new retrofit2.Callback<List<Student>>() {
            @Override
            public void onResponse(Call<List<Student>> call, Response<List<Student>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    callback.onSuccess(response.body().get(0));
                } else {
                    callback.onFailure("Failed to insert student. HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<Student>> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Get student details by enrollment number.
     */
    public void getStudentByEnrollment(String enrollment, Callback<Student> callback) {
        studentApi.getByEnrollment("eq." + enrollment, "*").enqueue(new retrofit2.Callback<List<Student>>() {
            @Override
            public void onResponse(Call<List<Student>> call, Response<List<Student>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Student> list = response.body();
                    if (!list.isEmpty()) {
                        callback.onSuccess(list.get(0));
                    } else {
                        callback.onFailure("STUDENT_NOT_FOUND");
                    }
                } else {
                    callback.onFailure("HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<Student>> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }

    private void updateStudentName(String enrollment, String name, Callback<Student> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("name", name);
        studentApi.updateName("eq." + enrollment, body).enqueue(new retrofit2.Callback<List<Student>>() {
            @Override
            public void onResponse(Call<List<Student>> call, Response<List<Student>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    callback.onSuccess(response.body().get(0));
                } else {
                    callback.onFailure("Failed to update student name. HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<Student>> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Check if a student has already submitted results for an exam.
     */
    public void checkExistingResult(String examCode, String enrollmentNumber, Callback<Boolean> callback) {
        resultApi.checkExistingResult("eq." + examCode, "eq." + enrollmentNumber, "id").enqueue(new retrofit2.Callback<List<Result>>() {
            @Override
            public void onResponse(Call<List<Result>> call, Response<List<Result>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(!response.body().isEmpty());
                } else {
                    callback.onFailure("HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<Result>> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Get all exam results for a given student enrollment.
     */
    public void getStudentResults(String enrollmentNumber, Callback<List<Result>> callback) {
        resultApi.getResultsForStudent("eq." + enrollmentNumber, "*").enqueue(new retrofit2.Callback<List<Result>>() {
            @Override
            public void onResponse(Call<List<Result>> call, Response<List<Result>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure("HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<Result>> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Get active exam session if exists for resumption.
     */
    public void getActiveSession(String examCode, String enrollmentNumber, Callback<ExamSession> callback) {
        sessionApi.getActiveSession("eq." + examCode, "eq." + enrollmentNumber, "eq.active", "*").enqueue(new retrofit2.Callback<List<ExamSession>>() {
            @Override
            public void onResponse(Call<List<ExamSession>> call, Response<List<ExamSession>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<ExamSession> list = response.body();
                    if (!list.isEmpty()) {
                        callback.onSuccess(list.get(0));
                    } else {
                        callback.onSuccess(null); // No active session
                    }
                } else {
                    callback.onFailure("HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<ExamSession>> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Create a new exam session.
     */
    public void createExamSession(ExamSession session, Callback<ExamSession> callback) {
        sessionApi.createSession(session).enqueue(new retrofit2.Callback<List<ExamSession>>() {
            @Override
            public void onResponse(Call<List<ExamSession>> call, Response<List<ExamSession>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    callback.onSuccess(response.body().get(0));
                } else {
                    callback.onFailure("Failed to create exam session. HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<ExamSession>> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Update an active exam session (patch request).
     */
    public void updateExamSession(long sessionId, Map<String, Object> patch, Callback<Void> callback) {
        sessionApi.updateSession("eq." + sessionId, patch).enqueue(new retrofit2.Callback<List<ExamSession>>() {
            @Override
            public void onResponse(Call<List<ExamSession>> call, Response<List<ExamSession>> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(null);
                } else {
                    callback.onFailure("Failed to update session. HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<ExamSession>> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Update an active exam session synchronously (blocking network call).
     */
    public retrofit2.Response<List<ExamSession>> updateExamSessionSync(long sessionId, Map<String, Object> patch) throws Exception {
        return sessionApi.updateSession("eq." + sessionId, patch).execute();
    }


    /**
     * Submit final exam result.
     * Triggers HTTP 409 if already submitted (handled in calling activity).
     */
    public void submitResult(Result result, Callback<Result> callback) {
        resultApi.submitResult(result).enqueue(new retrofit2.Callback<List<Result>>() {
            @Override
            public void onResponse(Call<List<Result>> call, Response<List<Result>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    callback.onSuccess(response.body().get(0));
                } else if (response.code() == 409) {
                    callback.onFailure("DUPLICATE_ATTEMPT");
                } else {
                    callback.onFailure("HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<Result>> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Fetch complete student result details.
     */
    public void getResult(String examCode, String enrollmentNumber, Callback<Result> callback) {
        resultApi.checkExistingResult("eq." + examCode, "eq." + enrollmentNumber, "*").enqueue(new retrofit2.Callback<List<Result>>() {
            @Override
            public void onResponse(Call<List<Result>> call, Response<List<Result>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Result> list = response.body();
                    if (!list.isEmpty()) {
                        callback.onSuccess(list.get(0));
                    } else {
                        callback.onFailure("RESULT_NOT_FOUND");
                    }
                } else {
                    callback.onFailure("HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<Result>> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }
}

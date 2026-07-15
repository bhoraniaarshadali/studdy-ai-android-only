package com.arshad.studdy_app_android_only.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Manages all session state for the Studdy app.
 *
 * Teacher session:
 *   Stored in EncryptedSharedPreferences (AES-256-GCM encrypted at rest).
 *   Holds: access_token, refresh_token, user_id, email, display_name.
 *
 * Student session:
 *   Stored in plain SharedPreferences (matching original Flutter app behavior —
 *   enrollment number is not a secret).
 *   Holds: enrollment_number (String).
 */
public class SessionManager {

    private static final String TAG = "SessionManager";

    // ── Encrypted prefs for teacher JWT ──────────────────────────────────
    private static final String ENCRYPTED_PREFS_FILE = "studdy_teacher_session";
    private static final String KEY_ACCESS_TOKEN   = "access_token";
    private static final String KEY_REFRESH_TOKEN  = "refresh_token";
    private static final String KEY_TEACHER_ID     = "teacher_id";
    private static final String KEY_TEACHER_EMAIL  = "teacher_email";
    private static final String KEY_TEACHER_NAME   = "teacher_name";

    // ── Plain prefs for student enrollment ───────────────────────────────
    private static final String PLAIN_PREFS_FILE   = "studdy_student_session";
    private static final String KEY_ENROLLMENT     = "enrollment_number";
    private static final String KEY_STUDENT_NAME   = "student_name";
    private static final String KEY_STUDENT_ACCESS_TOKEN = "student_access_token";
    private static final String KEY_STUDENT_REFRESH_TOKEN = "student_refresh_token";

    private static SessionManager instance;

    private SharedPreferences encryptedPrefs;
    private SharedPreferences plainPrefs;

    private SessionManager() { /* use getInstance(context) */ }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager();
        }
        instance.init(context.getApplicationContext());
        return instance;
    }

    public static synchronized SessionManager getInstance() {
        return instance;
    }

    private void init(Context context) {
        if (encryptedPrefs == null) {
            try {
                MasterKey masterKey = new MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();

                encryptedPrefs = EncryptedSharedPreferences.create(
                        context,
                        ENCRYPTED_PREFS_FILE,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            } catch (Exception e) {
                Log.e(TAG, "Failed to create EncryptedSharedPreferences", e);
                // Fallback to plain prefs in catastrophic error cases
                encryptedPrefs = context.getSharedPreferences(
                        ENCRYPTED_PREFS_FILE + "_fallback", Context.MODE_PRIVATE);
            }
        }

        if (plainPrefs == null) {
            plainPrefs = context.getSharedPreferences(PLAIN_PREFS_FILE, Context.MODE_PRIVATE);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Teacher session
    // ══════════════════════════════════════════════════════════════════════

    public void saveTeacherSession(String accessToken, String refreshToken,
                                   String userId, String email, String name) {
        encryptedPrefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_TEACHER_ID, userId)
                .putString(KEY_TEACHER_EMAIL, email)
                .putString(KEY_TEACHER_NAME, name != null ? name : "")
                .apply();
    }

    public boolean hasTeacherSession() {
        String token = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null);
        return token != null && !token.isEmpty();
    }

    public String getAccessToken() {
        return encryptedPrefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return encryptedPrefs.getString(KEY_REFRESH_TOKEN, null);
    }

    public String getTeacherId() {
        return encryptedPrefs.getString(KEY_TEACHER_ID, null);
    }

    public String getTeacherEmail() {
        return encryptedPrefs.getString(KEY_TEACHER_EMAIL, null);
    }

    public String getTeacherName() {
        return encryptedPrefs.getString(KEY_TEACHER_NAME, null);
    }

    public void clearTeacherSession() {
        encryptedPrefs.edit().clear().apply();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Student session
    // ══════════════════════════════════════════════════════════════════════

    public void saveStudentEnrollment(String enrollmentNumber) {
        plainPrefs.edit()
                .putString(KEY_ENROLLMENT, enrollmentNumber)
                .apply();
    }

    public void saveStudentSession(String enrollmentNumber, String name, String accessToken, String refreshToken) {
        plainPrefs.edit()
                .putString(KEY_ENROLLMENT, enrollmentNumber)
                .putString(KEY_STUDENT_NAME, name != null ? name : "")
                .putString(KEY_STUDENT_ACCESS_TOKEN, accessToken)
                .putString(KEY_STUDENT_REFRESH_TOKEN, refreshToken)
                .apply();
    }

    public boolean hasStudentSession() {
        String enrollment = plainPrefs.getString(KEY_ENROLLMENT, null);
        return enrollment != null && !enrollment.isEmpty();
    }

    public String getStudentEnrollment() {
        return plainPrefs.getString(KEY_ENROLLMENT, null);
    }

    public String getStudentName() {
        return plainPrefs.getString(KEY_STUDENT_NAME, "Student");
    }

    public String getStudentAccessToken() {
        return plainPrefs.getString(KEY_STUDENT_ACCESS_TOKEN, null);
    }

    public void clearStudentSession() {
        plainPrefs.edit()
                .remove(KEY_ENROLLMENT)
                .remove(KEY_STUDENT_NAME)
                .remove(KEY_STUDENT_ACCESS_TOKEN)
                .remove(KEY_STUDENT_REFRESH_TOKEN)
                .apply();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Full sign-out
    // ══════════════════════════════════════════════════════════════════════

    public void clearAll() {
        clearTeacherSession();
        clearStudentSession();
    }
}

package com.arshad.studdy_app_android_only.util;

/**
 * Centralised validation rules matching the exact spec requirements.
 * All error messages reference strings that are also in strings.xml.
 */
public final class ValidationUtils {

    private ValidationUtils() { /* utility class */ }

    // ── Email ──────────────────────────────────────────────────────────────

    /** Spec: email required, must contain '@'. No stricter format check. */
    public static boolean isEmailValid(String email) {
        return email != null && !email.trim().isEmpty() && email.contains("@");
    }

    // ── Password ───────────────────────────────────────────────────────────

    /** Spec: required and length at least 6. */
    public static boolean isPasswordValid(String password) {
        return password != null && password.length() >= 6;
    }

    // ── Name ──────────────────────────────────────────────────────────────

    /** Spec: name required (sign-up only). */
    public static boolean isNameValid(String name) {
        return name != null && !name.trim().isEmpty();
    }

    // ── Exam code ─────────────────────────────────────────────────────────

    /** Spec: exactly 6 characters (uppercase alpha + numeric). */
    public static boolean isExamCodeValid(String code) {
        return code != null && code.trim().length() == 6;
    }

    // ── Enrollment number ─────────────────────────────────────────────────

    /** Spec: at least 5 trimmed characters; no numeric-only validation. */
    public static boolean isEnrollmentValid(String enrollment) {
        return enrollment != null && enrollment.trim().length() >= 5;
    }

    // ── Error message normalization for auth errors ────────────────────────

    /**
     * Normalizes Supabase Auth error messages to the three categories
     * described in the spec:
     *   • Invalid credentials
     *   • Already registered
     *   • Password too short
     *   • Generic fallback
     */
    public static AuthErrorType normalizeAuthError(String rawError) {
        if (rawError == null) return AuthErrorType.GENERIC;
        String lower = rawError.toLowerCase();

        if (lower.contains("invalid login") ||
                lower.contains("invalid credentials") ||
                lower.contains("user not found") ||
                lower.contains("invalid email or password")) {
            return AuthErrorType.INVALID_CREDENTIALS;
        }

        if (lower.contains("already registered") ||
                lower.contains("user already exists") ||
                lower.contains("email already in use")) {
            return AuthErrorType.ALREADY_REGISTERED;
        }

        if (lower.contains("password should be at least") ||
                lower.contains("password must be") ||
                lower.contains("weak password")) {
            return AuthErrorType.PASSWORD_TOO_SHORT;
        }

        return AuthErrorType.GENERIC;
    }

    public enum AuthErrorType {
        INVALID_CREDENTIALS,
        ALREADY_REGISTERED,
        PASSWORD_TOO_SHORT,
        GENERIC
    }
}

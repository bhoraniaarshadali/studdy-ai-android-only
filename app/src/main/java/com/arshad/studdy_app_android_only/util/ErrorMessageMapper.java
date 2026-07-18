package com.arshad.studdy_app_android_only.util;

import android.util.Log;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public final class ErrorMessageMapper {

    private static final String TAG = "ErrorMessageMapper";

    private ErrorMessageMapper() {
        // Prevent instantiation
    }

    /**
     * Map a Throwable/Exception to a friendly user-facing message, while logging the real cause.
     */
    public static String toUserMessage(String contextTag, String contextMessage, Throwable throwable) {
        Log.e(contextTag, contextMessage, throwable);
        if (throwable instanceof UnknownHostException || throwable instanceof IOException) {
            if (throwable instanceof SocketTimeoutException) {
                return "Connection timeout. Please check your network and try again.";
            }
            return "No internet connection. Please check your network and try again.";
        }
        return "Something went wrong. Please try again.";
    }

    /**
     * Map an HTTP status code to a friendly user-facing message, while logging the details.
     */
    public static String toUserMessage(String contextTag, String contextMessage, int httpStatusCode) {
        Log.w(contextTag, contextMessage + " | HTTP Code: " + httpStatusCode);
        if (httpStatusCode >= 500) {
            return "Something went wrong on our end. Please try again in a moment.";
        }
        if (httpStatusCode == 401 || httpStatusCode == 403) {
            return "Incorrect email/enrollment number or password. Please try again.";
        }
        if (httpStatusCode == 409) {
            return "This action conflicts with existing data. Please try again.";
        }
        return "Something went wrong. Please try again.";
    }

    /**
     * Map a raw backend error message string to a friendly user-facing message, while logging the details.
     */
    public static String toUserMessage(String contextTag, String contextMessage, String rawError) {
        Log.w(contextTag, contextMessage + " | Raw Error: " + rawError);
        if (rawError == null || rawError.trim().isEmpty()) {
            return "Something went wrong. Please try again.";
        }
        
        String lowerError = rawError.toLowerCase();

        // Network or socket issues described in strings
        if (lowerError.contains("network") || lowerError.contains("timeout") || lowerError.contains("unreachable") || lowerError.contains("failed to connect") || lowerError.contains("socket")) {
            return "No internet connection. Please check your network and try again.";
        }

        // Auth failures
        if (lowerError.contains("invalid credentials") || lowerError.contains("invalid_credentials") || lowerError.contains("incorrect password") || lowerError.contains("wrong password") || lowerError.contains("generic auth") || lowerError.contains("invalid grant") || lowerError.contains("invalid_grant")) {
            return "Incorrect email/enrollment number or password. Please try again.";
        }

        // 409 Conflicts or already exist
        if (lowerError.contains("duplicate") || lowerError.contains("already exists") || lowerError.contains("conflict") || lowerError.contains("duplicate_attempt") || lowerError.contains("already attempted")) {
            return "This has already been completed or exists.";
        }

        // Server errors
        if (lowerError.contains("500") || lowerError.contains("internal server error") || lowerError.contains("bad gateway") || lowerError.contains("service unavailable")) {
            return "Something went wrong on our end. Please try again in a moment.";
        }

        return "Something went wrong. Please try again.";
    }
}

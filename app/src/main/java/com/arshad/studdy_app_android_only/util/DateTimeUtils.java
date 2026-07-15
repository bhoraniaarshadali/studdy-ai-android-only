package com.arshad.studdy_app_android_only.util;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Robust ISO 8601 and database timestamp parser/formatter.
 */
public final class DateTimeUtils {

    private static final String TAG = "DateTimeUtils";
    private static final String ISO_FORMAT_Z = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String ISO_FORMAT_MS = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    private DateTimeUtils() {}

    /**
     * Parse ISO 8601 UTC string (from Supabase) into local Date.
     */
    public static Date parseIso8601(String isoString) {
        if (isoString == null || isoString.isEmpty()) return null;
        
        // Normalize UTC representation if it ends with "+00:00"
        String normalized = isoString;
        if (normalized.endsWith("+00:00")) {
            normalized = normalized.substring(0, normalized.length() - 6) + "Z";
        }

        // Try format with milliseconds first
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(ISO_FORMAT_MS, Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.parse(normalized);
        } catch (Exception ignored) {}

        // Try standard ISO format without milliseconds
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(ISO_FORMAT_Z, Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.parse(normalized);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse ISO 8601 timestamp: " + isoString, e);
            return null;
        }
    }

    /**
     * Format current local Date as ISO 8601 UTC string.
     */
    public static String formatIso8601(Date date) {
        if (date == null) return null;
        SimpleDateFormat sdf = new SimpleDateFormat(ISO_FORMAT_Z, Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }

    public static String formatIso8601Now() {
        return formatIso8601(new Date());
    }

    /**
     * Returns true if the current time falls outside [windowStart, windowEnd]
     * (if those times are non-null).
     */
    public static boolean isBeforeWindow(String windowStart) {
        if (windowStart == null || windowStart.isEmpty()) return false;
        Date start = parseIso8601(windowStart);
        if (start == null) return false;
        return new Date().before(start);
    }

    public static boolean isAfterWindow(String windowEnd) {
        if (windowEnd == null || windowEnd.isEmpty()) return false;
        Date end = parseIso8601(windowEnd);
        if (end == null) return false;
        return new Date().after(end);
    }

    /**
     * Format remaining milliseconds as MM:SS.
     */
    public static String formatRemainingTime(long millis) {
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    /**
     * Parse ISO 8601 string and return a friendly formatted date/time.
     * e.g., "Jul 8, 2:00 PM"
     */
    public static String formatFriendlyDateTime(String isoString) {
        if (isoString == null || isoString.isEmpty()) return "";
        Date date = parseIso8601(isoString);
        if (date == null) return "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
            return sdf.format(date);
        } catch (Exception e) {
            Log.e(TAG, "Failed to format friendly date time", e);
            return isoString;
        }
    }

    /**
     * Parse ISO 8601 string and return a date-only format.
     * e.g., "03 July 2026"
     */
    public static String formatDateOnly(String isoString) {
        if (isoString == null || isoString.isEmpty()) return "";
        Date date = parseIso8601(isoString);
        if (date == null) return "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            return sdf.format(date);
        } catch (Exception e) {
            Log.e(TAG, "Failed to format date only", e);
            return isoString;
        }
    }
}

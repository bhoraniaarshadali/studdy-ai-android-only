package com.arshad.studdy_app_android_only.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Response structure for Google Gemini API generateContent endpoint.
 */
public class GeminiResponse {

    @SerializedName("candidates")
    public List<Candidate> candidates;

    public static class Candidate {
        @SerializedName("content")
        public Content content;
    }

    public static class Content {
        @SerializedName("parts")
        public List<Part> parts;
    }

    public static class Part {
        @SerializedName("text")
        public String text;
    }

    /**
     * Helper to retrieve generated text content safely.
     */
    public String getGeneratedText() {
        if (candidates != null && !candidates.isEmpty()) {
            Candidate candidate = candidates.get(0);
            if (candidate.content != null && candidate.content.parts != null && !candidate.content.parts.isEmpty()) {
                return candidate.content.parts.get(0).text;
            }
        }
        return null;
    }
}

# Studdy App ProGuard Rules

# ── Retrofit / OkHttp ─────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes *Annotation*
-keepattributes Signature
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ── Gson ──────────────────────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── Data models (keep for Gson serialisation) ─────────────────────────────
-keep class com.arshad.studdy_app_android_only.data.model.** { *; }
-keep class com.arshad.studdy_app_android_only.data.remote.dto.** { *; }

# ── Supabase / API responses ──────────────────────────────────────────────
-keepattributes Exceptions

# ── ML Kit ────────────────────────────────────────────────────────────────
-keep class com.google.mlkit.** { *; }

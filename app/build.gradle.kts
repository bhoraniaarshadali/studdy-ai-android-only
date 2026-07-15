import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// ── Read local.properties secrets ───────────────────────────────────────────
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.arshad.studdy_app_android_only"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.arshad.studdy_app_android_only"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ── Supabase credentials ────────────────────────────────────────────
        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${localProperties["SUPABASE_URL"] ?: ""}\""
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"${localProperties["SUPABASE_ANON_KEY"] ?: ""}\""
        )
        // ── Gemini API key ──────────────────────────────────────────────────
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProperties["GEMINI_API_KEY"] ?: ""}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    // ── Core UI ────────────────────────────────────────────────────────────
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity.ktx)
    implementation(libs.constraintlayout)
    implementation(libs.viewpager2)
    implementation(libs.swiperefreshlayout)
    implementation(libs.browser)
    implementation(libs.cardview)
    implementation(libs.recyclerview)
    implementation(libs.coordinatorlayout)

    // ── Networking (Supabase REST + Gemini REST) ────────────────────────────
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // ── Architecture (MVVM) ────────────────────────────────────────────────
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.runtime)
    implementation(libs.fragment)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // ── CameraX ────────────────────────────────────────────────────────────
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // ── ML Kit ─────────────────────────────────────────────────────────────
    implementation(libs.mlkit.barcode)
    implementation(libs.mlkit.face)

    // ── Security ───────────────────────────────────────────────────────────
    implementation(libs.security.crypto)

    // ── Image loading ──────────────────────────────────────────────────────
    implementation(libs.glide)

    // ── WorkManager ────────────────────────────────────────────────────────
    implementation(libs.work.runtime)


    // ── Tests ──────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
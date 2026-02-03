plugins {
    alias(libs.plugins.android.application)
    // If your project uses Kotlin plugin aliases, keep them as-is
    // alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.flatflex"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.flatflex"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // If you added WorkManager / Biometrics in settings:
    implementation("androidx.work:work-runtime:2.9.0")
    // WorkManager's ListenableWorker API references Guava's ListenableFuture.
    // Include the Android-optimized Guava artifact to ensure ListenableFuture is present.
    implementation("com.google.guava:guava:33.0.0-android")
    implementation("androidx.biometric:biometric:1.1.0")

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

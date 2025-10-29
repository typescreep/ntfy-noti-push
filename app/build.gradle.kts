plugins {
    id("com.android.application") version "8.5.2"
    id("org.jetbrains.kotlin.android") version "1.9.24"
}

android {
    namespace = "com.example.ntfynotipush"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.ntfynotipush"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // keep it simple; no generated bindings
    buildFeatures { viewBinding = false }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.preference:preference-ktx:1.2.1")

    // HTTP
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // WorkManager (background reliable work + retries)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}

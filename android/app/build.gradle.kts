plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

// Real Firebase config is per-developer and gitignored (see .gitignore) — fresh
// clones and CI fall back to the placeholder so the build still succeeds.
// Existing local files are left alone.
val googleServicesFile = file("google-services.json")
if (!googleServicesFile.exists()) {
    file("google-services.json.example").copyTo(googleServicesFile)
}

android {
    namespace = "com.teeup.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.teeup.android"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.google.firebase:firebase-database:22.0.2")
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")

    // Base networking scaffold (EME-294) — Retrofit/OkHttp REST client.
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // MVVM scaffold — ComponentActivity + ViewModel, no AppCompat/Material.
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    testImplementation("junit:junit:4.13.2")
}

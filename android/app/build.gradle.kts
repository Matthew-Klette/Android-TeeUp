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

    // Needed for BuildConfig.DEBUG, which gates the dev-only auth bypass
    // (DevIdentity/TeeUpApiClient) to debug builds only.
    buildFeatures {
        buildConfig = true
    }

    lint {
        // This project deliberately uses ComponentActivity, not AppCompatActivity
        // (see dependency comment below), so android:tint is the correct, functional
        // attribute here — UseAppTint's AppCompat recommendation doesn't apply.
        disable += "UseAppTint"
        // af/xh localization is a WIP rollout that doesn't yet cover every string
        // file; don't block CI on incomplete translations.
        disable += "MissingTranslation"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// iCloud Drive's "Desktop & Documents Folders" sync (this repo lives under
// ~/Documents) continuously races with Gradle and duplicates files as
// "name N.ext", e.g. "fade_in 3.xml" or "ic_logo 4.png" — a space is never
// valid in an Android resource filename, so resource parsing/merging
// hard-fails the build whenever one of these appears. This isn't confined to
// src/**/res or any one generated/intermediate directory: it's been observed
// across build/generated/res, build/intermediates/packaged_res, and even
// Kotlin's compiler caches under build/kotlin — anywhere under this
// iCloud-synced module tree. So instead of chasing individual AGP output
// directories, sweep the whole module (src + build) for stray "N.ext" files
// as the very first thing every build does (a preBuild dependency), before
// any task can trip over one.
val cleanDuplicateResFiles = tasks.register("cleanDuplicateResFiles") {
    doFirst {
        fileTree(".")
            .filter { it.name.matches(Regex(""".* \d+\..+""")) }
            .forEach {
                logger.warn("Removing stray duplicate resource file (iCloud/Finder sync artifact): ${it.path}")
                it.delete()
            }
    }
}

tasks.named("preBuild") {
    dependsOn(cleanDuplicateResFiles)
}

// Belt-and-braces: a duplicate can also appear mid-build (iCloud sync is
// continuous, not just before preBuild runs), so also sweep right before the
// specific task classes most often affected.
tasks.matching { it.name.matches(Regex("merge.*Resources|parse.*LocalResources|package.*Resources")) }.configureEach {
    dependsOn(cleanDuplicateResFiles)
}

dependencies {
    implementation("com.google.firebase:firebase-database:22.0.2")
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // Base networking scaffold (EME-294) — Retrofit/OkHttp REST client.
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // MVVM scaffold — ComponentActivity + ViewModel, no AppCompat/Material.
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Biometric Login (Profile & Settings) — BiometricPrompt requires a FragmentActivity
    // host, which is the one reason androidx.fragment enters this project at all.
    implementation("androidx.biometric:biometric:1.1.0")

    testImplementation("junit:junit:4.13.2")
}

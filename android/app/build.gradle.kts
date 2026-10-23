plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
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
    // 36 for the Compose 1.9 libraries; runtime behaviour still follows targetSdk below.
    compileSdk = 36

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
        // Release, but signed with the local debug key so it installs over a dev build and
        // keeps its sign-in. For measuring performance the way users get it: not debuggable,
        // and compiled ahead of time from the baseline profile (src/main/baseline-prof.txt).
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    // Needed for BuildConfig.DEBUG, which gates the dev-only auth bypass
    // (DevIdentity/TeeUpApiClient) to debug builds only.
    buildFeatures {
        buildConfig = true
        // iOS-style redesign (mklet/ios-redesign): screens move to Jetpack Compose one at a time.
        compose = true
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

    // iOS-style redesign: Compose UI, springs and gestures. BOM 2025.10.00 = Compose 1.9.3.
    // No Material: the design system in com.teeup.android.design draws every component itself.
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    // Real backdrop blur for the Liquid Glass bars (RenderEffect on Android 12+, a frosted
    // tint below).
    implementation("dev.chrisbanes.haze:haze:1.6.10")
    // Installs baseline-prof.txt on sideloaded/non-Play installs too, so first launches are fast.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Biometric Login (Profile & Settings) — BiometricPrompt requires a FragmentActivity
    // host, which is the one reason androidx.fragment enters this project at all.
    implementation("androidx.biometric:biometric:1.1.0")

    testImplementation("junit:junit:4.13.2")
    // The real org.json, so unit tests can parse the bundled hole maps (android.jar only has stubs).
    testImplementation("org.json:json:20240303")
}

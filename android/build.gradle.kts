plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
    // Kotlin Symbol Processing: generates the Room DAO/database code for offline mode.
    id("com.google.devtools.ksp") version "2.2.21-2.0.5" apply false
}

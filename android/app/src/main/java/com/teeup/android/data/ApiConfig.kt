package com.teeup.android.data

/**
 * Points at the deployed API (Azure App Service, backed by Supabase Postgres)
 * so every build — emulator, physical device, or a teammate's own checkout —
 * talks to the same live backend without needing a local `dotnet run` instance.
 */
object ApiConfig {
    const val BASE_URL = "https://teeup-awexdahtg2asepcy.southafricanorth-01.azurewebsites.net/"
}

package com.teeup.android.data

/**
 * `10.0.2.2` is the Android emulator's alias for the host machine's
 * loopback address, so this points at `dotnet run` from `api/TeeUp.Api`
 * (default port 5017, see Properties/launchSettings.json) running on your
 * dev machine. EME-294 owns real per-environment configuration (a real
 * device needs your machine's LAN IP or the deployed Azure App Service URL
 * instead) — swap this constant out once that lands.
 */
object ApiConfig {
    const val BASE_URL = "http://10.0.2.2:5017/"
}

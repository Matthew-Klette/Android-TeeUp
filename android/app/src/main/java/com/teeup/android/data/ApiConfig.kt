package com.teeup.android.data

/**
 * `10.0.2.2` is the Android emulator's alias for the host machine's loopback address, so
 * this points at `dotnet run` from api/TeeUp.Api on the dev machine. EME-294 owns making
 * this configurable per environment; swap this constant out once that lands.
 */
object ApiConfig {
    const val BASE_URL = "http://10.0.2.2:5017/"
}

package com.teeup.android.data.network

import android.util.Log
import com.teeup.android.data.ApiConfig
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// Retrofit client with OkHttp logging (Square, n.d.a; Square, n.d.b).
object RetrofitClient {
    private const val TAG = "TeeUpNetwork"

    private val loggingInterceptor = HttpLoggingInterceptor { message -> Log.d(TAG, message) }
        .apply { level = HttpLoggingInterceptor.Level.BASIC }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    val apiService: TeeUpApiService by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TeeUpApiService::class.java)
    }
}

/*
References:

Square, Inc. (n.d.a). Retrofit. [online] GitHub. Available at: <https://github.com/lysine-dev/retrofit> [Accessed 19 Sep. 2026].

Square, Inc. (n.d.b). okhttp-logging-interceptor. [online] GitHub. Available at: <https://github.com/lysine-dev/okhttp/tree/main/okhttp-logging-interceptor> [Accessed 19 Sep. 2026].
*/

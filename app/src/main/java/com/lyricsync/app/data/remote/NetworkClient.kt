package com.lyricsync.app.data.remote

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkClient {
    val baseOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

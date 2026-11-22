package com.lyricsync.app.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

data class LrcLibResponse(
    val id: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)

interface LrcLibApi {

    @Headers("User-Agent: LyricSyncTranslator/1.0 (https://github.com/MamiZ35/LyricSync)")
    @GET("api/get")
    suspend fun getLyrics(
        @Query("artist_name") artistName: String,
        @Query("track_name") trackName: String,
        @Query("duration") durationSeconds: Int? = null
    ): LrcLibResponse

    @Headers("User-Agent: LyricSyncTranslator/1.0 (https://github.com/MamiZ35/LyricSync)")
    @GET("api/search")
    suspend fun searchLyrics(
        @Query("artist_name") artistName: String? = null,
        @Query("track_name") trackName: String? = null,
        @Query("q") query: String? = null
    ): List<LrcLibResponse>

    companion object {
        private const val BASE_URL = "https://lrclib.net/"

        val instance: LrcLibApi by lazy {
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(NetworkClient.baseOkHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(LrcLibApi::class.java)
        }
    }
}

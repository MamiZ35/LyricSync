package com.lyricsync.app.domain

data class MediaTrackState(
    val trackName: String = "",
    val artistName: String = "",
    val packageName: String = "",
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false
) {
    val hasActiveTrack: Boolean get() = trackName.isNotBlank()
    val trackId: String get() = "${artistName.lowercase().trim()}|${trackName.lowercase().trim()}"
}

data class LyricLine(
    val timeMs: Long,
    val originalText: String,
    val translatedText: String = ""
)

data class LyricsState(
    val trackId: String = "",
    val lines: List<LyricLine> = emptyList(),
    val isSynced: Boolean = true,
    val sourceLang: String = "",
    val targetLang: String = "tr",
    val isLoading: Boolean = false,
    val isDownloadingModel: Boolean = false,
    val isFromCache: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

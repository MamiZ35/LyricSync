package com.lyricsync.app.domain

import android.graphics.Bitmap

data class MediaTrackState(
    val trackName: String = "",
    val artistName: String = "",
    val packageName: String = "",
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val albumArt: Bitmap? = null,
    val accentColorArgb: Int? = null,
    val isManualOverride: Boolean = false
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

enum class LyricDisplayMode(val label: String) {
    DUAL("Çift Dil"),
    TRANSLATION_ONLY("Sadece Çeviri"),
    ORIGINAL_ONLY("Sadece Orijinal")
}

enum class TranslationEngine(val label: String) {
    CLOUD_CONTEXT("Akıllı Bulut (Bağlamsal)"),
    ON_DEVICE_MLKIT("Cihaz İçi (Çevrimdışı ML Kit)")
}

data class OverlayConfig(
    val fontSizeSp: Float = 15f,
    val opacity: Float = 0.90f,
    val isTouchLocked: Boolean = false,
    val x: Int = 0,
    val y: Int = 180
)

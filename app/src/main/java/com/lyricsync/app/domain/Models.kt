package com.lyricsync.app.domain

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

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

@Immutable
data class LyricLine(
    val timeMs: Long,
    val originalText: String,
    val translatedText: String = ""
)

@Immutable
data class LyricsState(
    val trackId: String = "",
    val lines: List<LyricLine> = emptyList(),
    val isSynced: Boolean = true,
    val sourceLang: String = "",
    val targetLang: String = "tr",
    val syncOffsetMs: Long = 0L,
    val isLoading: Boolean = false,
    val isDownloadingModel: Boolean = false,
    val isFromCache: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

enum class ThemeMode(val label: String) {
    SYSTEM("Sistem"),
    DARK("Koyu"),
    AMOLED("Saf Siyah (OLED)"),
    LIGHT("Açık")
}

@Immutable
data class LoopRange(
    val startLineIndex: Int,
    val endLineIndex: Int,
    val startTimeMs: Long,
    val endTimeMs: Long
)

enum class LyricDisplayMode(val label: String) {
    DUAL("Çift Dil"),
    TRANSLATION_ONLY("Sadece Çeviri"),
    ORIGINAL_ONLY("Sadece Orijinal")
}

enum class TranslationEngine(val label: String) {
    CLOUD_CONTEXT("Akıllı Bulut (Bağlamsal)"),
    ON_DEVICE_MLKIT("Cihaz İçi (Çevrimdışı ML Kit)")
}

@Immutable
data class OverlayConfig(
    val fontSizeSp: Float = 15f,
    val opacity: Float = 0.90f,
    val isTouchLocked: Boolean = false,
    val showNextLine: Boolean = true,
    val x: Int = 0,
    val y: Int = 180
)

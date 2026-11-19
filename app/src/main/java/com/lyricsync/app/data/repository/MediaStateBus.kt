package com.lyricsync.app.data.repository

import com.google.mlkit.nl.translate.TranslateLanguage
import com.lyricsync.app.domain.LrcParser
import com.lyricsync.app.domain.LyricsState
import com.lyricsync.app.domain.MediaTrackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Shared reactive state bus linking MediaNotificationListenerService,
 * LyricsRepository, MainActivity UI, and FloatingOverlayService.
 */
object MediaStateBus {
    private val defaultTargetLang: String =
        TranslateLanguage.fromLanguageTag(Locale.getDefault().language)
            ?: TranslateLanguage.TURKISH

    private val _trackState = MutableStateFlow(MediaTrackState())
    val trackState: StateFlow<MediaTrackState> = _trackState.asStateFlow()

    private val _targetLanguage = MutableStateFlow(defaultTargetLang)
    val targetLanguage: StateFlow<String> = _targetLanguage.asStateFlow()

    private val _lyricsState = MutableStateFlow(LyricsState(targetLang = defaultTargetLang))
    val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()

    private val _activeLineIndex = MutableStateFlow(-1)
    val activeLineIndex: StateFlow<Int> = _activeLineIndex.asStateFlow()

    private val _isOverlayActive = MutableStateFlow(false)
    val isOverlayActive: StateFlow<Boolean> = _isOverlayActive.asStateFlow()

    fun updateTrackState(newState: MediaTrackState) {
        _trackState.value = newState
        recalculateActiveLine(newState.positionMs)
    }

    fun updatePosition(positionMs: Long, isPlaying: Boolean) {
        val current = _trackState.value
        if (current.positionMs != positionMs || current.isPlaying != isPlaying) {
            _trackState.value = current.copy(positionMs = positionMs, isPlaying = isPlaying)
            recalculateActiveLine(positionMs)
        }
    }

    fun updateLyricsState(newState: LyricsState) {
        _lyricsState.value = newState
        recalculateActiveLine(_trackState.value.positionMs)
    }

    fun setTargetLanguage(langCode: String) {
        _targetLanguage.value = langCode
    }

    fun setOverlayActive(active: Boolean) {
        _isOverlayActive.value = active
    }

    private fun recalculateActiveLine(positionMs: Long) {
        val lyrics = _lyricsState.value
        val newIndex = LrcParser.findActiveLineIndex(
            lines = lyrics.lines,
            positionMs = positionMs,
            isSynced = lyrics.isSynced
        )
        if (_activeLineIndex.value != newIndex) {
            _activeLineIndex.value = newIndex
        }
    }
}

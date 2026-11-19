package com.lyricsync.app.data.repository

import android.content.Context
import com.google.mlkit.nl.translate.TranslateLanguage
import com.lyricsync.app.data.local.AppPrefs
import com.lyricsync.app.domain.LrcParser
import com.lyricsync.app.domain.LoopRange
import com.lyricsync.app.domain.LyricDisplayMode
import com.lyricsync.app.domain.LyricsState
import com.lyricsync.app.domain.MediaTrackState
import com.lyricsync.app.domain.OverlayConfig
import com.lyricsync.app.domain.ThemeMode
import com.lyricsync.app.domain.TranslationEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.abs

/**
 * Shared reactive state bus linking MediaNotificationListenerService,
 * LyricsRepository, MainActivity UI, and FloatingOverlayService.
 */
object MediaStateBus {
    private val defaultTargetLang: String =
        TranslateLanguage.fromLanguageTag(Locale.getDefault().language)
            ?: TranslateLanguage.TURKISH

    @Volatile
    private var prefs: AppPrefs? = null

    private val _trackState = MutableStateFlow(MediaTrackState())
    val trackState: StateFlow<MediaTrackState> = _trackState.asStateFlow()

    private val _targetLanguage = MutableStateFlow(defaultTargetLang)
    val targetLanguage: StateFlow<String> = _targetLanguage.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.DARK)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _displayMode = MutableStateFlow(LyricDisplayMode.DUAL)
    val displayMode: StateFlow<LyricDisplayMode> = _displayMode.asStateFlow()

    private val _translationEngine = MutableStateFlow(TranslationEngine.CLOUD_CONTEXT)
    val translationEngine: StateFlow<TranslationEngine> = _translationEngine.asStateFlow()

    private val _wifiOnlyDownload = MutableStateFlow(false)
    val wifiOnlyDownload: StateFlow<Boolean> = _wifiOnlyDownload.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(false)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _autoHideOverlayOnPause = MutableStateFlow(true)
    val autoHideOverlayOnPause: StateFlow<Boolean> = _autoHideOverlayOnPause.asStateFlow()

    private val _loopRange = MutableStateFlow<LoopRange?>(null)
    val loopRange: StateFlow<LoopRange?> = _loopRange.asStateFlow()

    private val _overlayConfig = MutableStateFlow(OverlayConfig())
    val overlayConfig: StateFlow<OverlayConfig> = _overlayConfig.asStateFlow()

    private val _lyricsState = MutableStateFlow(LyricsState(targetLang = defaultTargetLang))
    val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()

    private val _activeLineIndex = MutableStateFlow(-1)
    val activeLineIndex: StateFlow<Int> = _activeLineIndex.asStateFlow()

    private val _isOverlayActive = MutableStateFlow(false)
    val isOverlayActive: StateFlow<Boolean> = _isOverlayActive.asStateFlow()

    // Transport control hooks populated by MediaNotificationListenerService
    var onPlayPauseToggle: (() -> Unit)? = null
    var onSeekToMs: ((Long) -> Unit)? = null
    var onSkipNext: (() -> Unit)? = null
    var onSkipPrevious: (() -> Unit)? = null

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val appPrefs = AppPrefs(context)
            prefs = appPrefs
            val savedLang = appPrefs.targetLang
            _targetLanguage.value = savedLang
            _themeMode.value = appPrefs.themeMode
            _displayMode.value = appPrefs.displayMode
            _translationEngine.value = appPrefs.translationEngine
            _wifiOnlyDownload.value = appPrefs.wifiOnlyDownload
            _keepScreenOn.value = appPrefs.keepScreenOn
            _autoHideOverlayOnPause.value = appPrefs.autoHideOverlayOnPause
            _overlayConfig.value = appPrefs.getOverlayConfig()
            _lyricsState.value = _lyricsState.value.copy(targetLang = savedLang)
        }
    }

    fun updateTrackState(newState: MediaTrackState) {
        if (newState.trackId != _trackState.value.trackId) {
            _loopRange.value = null
        }
        _trackState.value = newState
        recalculateActiveLine(newState.positionMs)
    }

    fun updatePosition(positionMs: Long, isPlaying: Boolean) {
        // Always update active line at 100ms precision while throttling full trackState UI emissions
        val previousIndex = _activeLineIndex.value
        recalculateActiveLine(positionMs)
        val indexChanged = _activeLineIndex.value != previousIndex
        val current = _trackState.value
        val secondChanged = (positionMs / 1000L) != (current.positionMs / 1000L)
        if (indexChanged ||
            current.isPlaying != isPlaying ||
            secondChanged ||
            positionMs < current.positionMs ||
            abs(positionMs - current.positionMs) >= 900L
        ) {
            _trackState.value = current.copy(positionMs = positionMs, isPlaying = isPlaying)
        }
    }

    fun clearTrackIfSessionEnded() {
        if (!_trackState.value.isManualOverride) {
            _trackState.value = MediaTrackState()
            _lyricsState.value = LyricsState(targetLang = _targetLanguage.value)
            _activeLineIndex.value = -1
        }
    }

    fun updateLyricsState(newState: LyricsState) {
        _lyricsState.value = newState
        recalculateActiveLine(_trackState.value.positionMs)
    }

    fun setTargetLanguage(langCode: String) {
        _targetLanguage.value = langCode
        prefs?.targetLang = langCode
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs?.themeMode = mode
    }

    fun setDisplayMode(mode: LyricDisplayMode) {
        _displayMode.value = mode
        prefs?.displayMode = mode
    }

    fun setTranslationEngine(engine: TranslationEngine) {
        _translationEngine.value = engine
        prefs?.translationEngine = engine
    }

    fun setWifiOnlyDownload(wifiOnly: Boolean) {
        _wifiOnlyDownload.value = wifiOnly
        prefs?.wifiOnlyDownload = wifiOnly
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
        prefs?.keepScreenOn = enabled
    }

    fun setAutoHideOverlayOnPause(enabled: Boolean) {
        _autoHideOverlayOnPause.value = enabled
        prefs?.autoHideOverlayOnPause = enabled
    }

    fun setLoopRange(range: LoopRange?) {
        _loopRange.value = range
    }

    fun updateOverlayConfig(config: OverlayConfig) {
        _overlayConfig.value = config
        prefs?.saveOverlayConfig(config)
    }

    fun updateOverlayPosition(x: Int, y: Int) {
        val updated = _overlayConfig.value.copy(x = x, y = y.coerceAtLeast(0))
        _overlayConfig.value = updated
        prefs?.saveOverlayConfig(updated)
    }

    fun toggleOverlayTouchLock() {
        val current = _overlayConfig.value
        _overlayConfig.value = current.copy(isTouchLocked = !current.isTouchLocked)
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

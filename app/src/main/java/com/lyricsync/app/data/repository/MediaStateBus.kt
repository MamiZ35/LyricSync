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

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

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

    private val _autoLaunchSpotify = MutableStateFlow(false)
    val autoLaunchSpotify: StateFlow<Boolean> = _autoLaunchSpotify.asStateFlow()

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

    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    // Transport control hooks populated by MediaNotificationListenerService
    var onPlayPauseToggle: (() -> Unit)? = null
    var onSeekToMs: ((Long) -> Unit)? = null
    var onSkipNext: (() -> Unit)? = null
    var onSkipPrevious: (() -> Unit)? = null
    var onSyncFavoriteToPlayer: ((Boolean) -> Boolean)? = null

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
            _autoLaunchSpotify.value = appPrefs.autoLaunchSpotify
            _overlayConfig.value = appPrefs.getOverlayConfig()
            _lyricsState.value = _lyricsState.value.copy(targetLang = savedLang)
        }
    }

    fun updateTrackState(newState: MediaTrackState) {
        if (newState.trackId != _trackState.value.trackId) {
            _loopRange.value = null
        }
        _trackState.value = newState
        _playbackPositionMs.value = newState.positionMs
        recalculateActiveLine(newState.positionMs)
    }

    fun updatePosition(positionMs: Long, isPlaying: Boolean) {
        // Always update active line at 100ms precision while throttling full UI position emissions
        val previousIndex = _activeLineIndex.value
        recalculateActiveLine(positionMs)
        val indexChanged = _activeLineIndex.value != previousIndex
        val lastEmittedPos = _playbackPositionMs.value
        val secondChanged = (positionMs / 1000L) != (lastEmittedPos / 1000L)
        if (indexChanged ||
            secondChanged ||
            positionMs < lastEmittedPos ||
            abs(positionMs - lastEmittedPos) >= 900L
        ) {
            _playbackPositionMs.value = positionMs
        }
        val current = _trackState.value
        if (current.isPlaying != isPlaying) {
            _trackState.value = current.copy(isPlaying = isPlaying)
        }
    }

    fun clearTrackIfSessionEnded() {
        if (!_trackState.value.isManualOverride) {
            _trackState.value = MediaTrackState()
            _playbackPositionMs.value = 0L
            _loopRange.value = null
            _lyricsState.value = LyricsState(targetLang = _targetLanguage.value)
            _activeLineIndex.value = -1
        }
    }

    fun updateLyricsState(newState: LyricsState) {
        _lyricsState.value = newState
        recalculateActiveLine(_playbackPositionMs.value)
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

    fun setAutoLaunchSpotify(enabled: Boolean) {
        _autoLaunchSpotify.value = enabled
        prefs?.autoLaunchSpotify = enabled
    }

    fun setLoopRange(range: LoopRange?) {
        _loopRange.value = range
    }

    fun previewOverlayConfig(config: OverlayConfig) {
        _overlayConfig.value = config
    }

    fun commitOverlayConfig(config: OverlayConfig) {
        _overlayConfig.value = config
        prefs?.saveOverlayConfig(config)
    }

    fun updateOverlayConfig(config: OverlayConfig) {
        commitOverlayConfig(config)
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

    fun setAppInForeground(inForeground: Boolean) {
        _isAppInForeground.value = inForeground
    }

    fun setSyncOffsetMs(offsetMs: Long) {
        val clamped = offsetMs.coerceIn(-15_000L, 15_000L)
        _lyricsState.value = _lyricsState.value.copy(syncOffsetMs = clamped)
        recalculateActiveLine(_playbackPositionMs.value)
    }

    internal fun resetForTesting() {
        prefs = null
        _trackState.value = MediaTrackState()
        _playbackPositionMs.value = 0L
        _targetLanguage.value = defaultTargetLang
        _themeMode.value = ThemeMode.DARK
        _displayMode.value = LyricDisplayMode.DUAL
        _translationEngine.value = TranslationEngine.CLOUD_CONTEXT
        _wifiOnlyDownload.value = false
        _keepScreenOn.value = false
        _autoHideOverlayOnPause.value = true
        _autoLaunchSpotify.value = false
        _loopRange.value = null
        _overlayConfig.value = OverlayConfig()
        _lyricsState.value = LyricsState(targetLang = defaultTargetLang)
        _activeLineIndex.value = -1
        _isOverlayActive.value = false
        _isAppInForeground.value = false
    }

    private fun recalculateActiveLine(positionMs: Long) {
        val lyrics = _lyricsState.value
        val newIndex = LrcParser.findActiveLineIndex(
            lines = lyrics.lines,
            positionMs = positionMs,
            isSynced = lyrics.isSynced,
            syncOffsetMs = lyrics.syncOffsetMs
        )
        if (_activeLineIndex.value != newIndex) {
            _activeLineIndex.value = newIndex
        }
    }
}

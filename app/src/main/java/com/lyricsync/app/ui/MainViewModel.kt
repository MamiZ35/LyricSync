package com.lyricsync.app.ui

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lyricsync.app.BuildConfig
import com.lyricsync.app.data.local.CachedSongSummary
import com.lyricsync.app.data.remote.LrcLibResponse
import com.lyricsync.app.data.remote.ReleaseInfo
import com.lyricsync.app.data.remote.UpdateChecker
import com.lyricsync.app.data.repository.LyricsRepository
import com.lyricsync.app.data.repository.MediaStateBus
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

@Immutable
data class UpdateUiState(
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val statusMessage: String? = null,
    val releaseInfo: ReleaseInfo? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LyricsRepository.getInstance(application.applicationContext)

    init {
        MediaStateBus.init(application.applicationContext)
    }

    val trackState: StateFlow<MediaTrackState> = MediaStateBus.trackState
    val playbackPositionMs: StateFlow<Long> = MediaStateBus.playbackPositionMs
    val lyricsState: StateFlow<LyricsState> = MediaStateBus.lyricsState
    val activeLineIndex: StateFlow<Int> = MediaStateBus.activeLineIndex
    val isOverlayActive: StateFlow<Boolean> = MediaStateBus.isOverlayActive
    val themeMode: StateFlow<ThemeMode> = MediaStateBus.themeMode
    val displayMode: StateFlow<LyricDisplayMode> = MediaStateBus.displayMode
    val translationEngine: StateFlow<TranslationEngine> = MediaStateBus.translationEngine
    val overlayConfig: StateFlow<OverlayConfig> = MediaStateBus.overlayConfig
    val wifiOnlyDownload: StateFlow<Boolean> = MediaStateBus.wifiOnlyDownload
    val keepScreenOn: StateFlow<Boolean> = MediaStateBus.keepScreenOn
    val autoHideOverlayOnPause: StateFlow<Boolean> = MediaStateBus.autoHideOverlayOnPause
    val autoLaunchSpotify: StateFlow<Boolean> = MediaStateBus.autoLaunchSpotify
    val loopRange: StateFlow<LoopRange?> = MediaStateBus.loopRange

    private val _updateUiState = MutableStateFlow(UpdateUiState())
    val updateUiState: StateFlow<UpdateUiState> = _updateUiState.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = MediaStateBus.setThemeMode(mode)

    fun setDisplayMode(mode: LyricDisplayMode) = MediaStateBus.setDisplayMode(mode)

    fun toggleKeepScreenOn() = MediaStateBus.setKeepScreenOn(!MediaStateBus.keepScreenOn.value)

    fun setKeepScreenOn(enabled: Boolean) = MediaStateBus.setKeepScreenOn(enabled)

    fun setAutoHideOverlayOnPause(enabled: Boolean) = MediaStateBus.setAutoHideOverlayOnPause(enabled)

    fun setAutoLaunchSpotify(enabled: Boolean) = MediaStateBus.setAutoLaunchSpotify(enabled)

    fun setWifiOnlyDownload(wifiOnly: Boolean) = MediaStateBus.setWifiOnlyDownload(wifiOnly)

    fun setLoopRange(range: LoopRange?) = MediaStateBus.setLoopRange(range)

    fun previewOverlayConfig(config: OverlayConfig) = MediaStateBus.previewOverlayConfig(config)

    fun commitOverlayConfig(config: OverlayConfig) = MediaStateBus.commitOverlayConfig(config)

    fun seekToMs(posMs: Long) {
        MediaStateBus.onSeekToMs?.invoke(posMs)
    }

    fun playPauseToggle() {
        MediaStateBus.onPlayPauseToggle?.invoke()
    }

    fun skipNext() {
        MediaStateBus.onSkipNext?.invoke()
    }

    fun skipPrevious() {
        MediaStateBus.onSkipPrevious?.invoke()
    }

    fun selectTargetLanguage(langCode: String, hasNotificationAccess: Boolean) {
        MediaStateBus.setTargetLanguage(langCode)
        val track = MediaStateBus.trackState.value
        if (track.hasActiveTrack && (track.isManualOverride || !hasNotificationAccess)) {
            repository.requestLoadAndTranslate(track, langCode)
        }
    }

    fun selectTranslationEngine(engine: TranslationEngine, hasNotificationAccess: Boolean) {
        MediaStateBus.setTranslationEngine(engine)
        val track = MediaStateBus.trackState.value
        if (track.hasActiveTrack && (track.isManualOverride || !hasNotificationAccess)) {
            repository.requestLoadAndTranslate(track, MediaStateBus.targetLanguage.value)
        }
    }

    fun retryLyrics() {
        val track = MediaStateBus.trackState.value
        if (track.hasActiveTrack) {
            repository.requestLoadAndTranslate(
                track = track,
                targetLang = MediaStateBus.targetLanguage.value,
                forceRefresh = true
            )
        }
    }

    fun adjustSyncOffset(deltaMs: Long) {
        val track = MediaStateBus.trackState.value
        val newOffset = (MediaStateBus.lyricsState.value.syncOffsetMs + deltaMs).coerceIn(-15_000L, 15_000L)
        viewModelScope.launch {
            repository.updateSyncOffset(track, newOffset)
        }
    }

    fun resetSyncOffset() {
        val track = MediaStateBus.trackState.value
        viewModelScope.launch {
            repository.updateSyncOffset(track, 0L)
        }
    }

    suspend fun searchManual(query: String): List<LrcLibResponse> {
        return repository.searchLyricsManual(query)
    }

    fun selectManualResult(item: LrcLibResponse) {
        viewModelScope.launch {
            repository.selectManualLyrics(
                item = item,
                targetLang = MediaStateBus.targetLanguage.value
            )
        }
    }

    suspend fun loadCacheAndModels(): Pair<Int, List<String>> {
        return Pair(repository.getCachedSongCount(), repository.getDownloadedModels())
    }

    suspend fun clearCache() {
        repository.clearCache()
    }

    suspend fun deleteModel(langCode: String) {
        repository.deleteModel(langCode)
    }

    suspend fun loadLibrarySummaries(): List<CachedSongSummary> {
        return repository.getLibrarySummaries()
    }

    suspend fun toggleFavorite(cacheKey: String, isFav: Boolean): Boolean {
        repository.toggleFavorite(cacheKey, isFav)
        val currentLive = MediaStateBus.trackState.value
        val isCurrentPlayingTrack = !currentLive.isManualOverride &&
            currentLive.trackId.isNotBlank() &&
            cacheKey.contains("|${currentLive.trackId}|", ignoreCase = true)
        return if (isCurrentPlayingTrack) {
            MediaStateBus.onSyncFavoriteToPlayer?.invoke(isFav) == true
        } else {
            false
        }
    }

    fun selectCachedSong(cacheKey: String) {
        viewModelScope.launch {
            repository.loadCachedSongByKey(cacheKey)
        }
    }

    fun checkForUpdate() {
        if (_updateUiState.value.isChecking || _updateUiState.value.isDownloading) return
        viewModelScope.launch {
            _updateUiState.update {
                it.copy(isChecking = true, statusMessage = "GitHub kontrol ediliyor...")
            }
            val info = UpdateChecker.checkLatestRelease(BuildConfig.VERSION_NAME)
            val status = when {
                info == null -> "Güncelleme bilgisi alınamadı."
                info.isUpdateAvailable -> "Yeni sürüm bulundu: ${info.latestTag}"
                else -> "Güncel sürümü (${info.latestTag}) kullanıyorsunuz ✓"
            }
            _updateUiState.update {
                it.copy(
                    isChecking = false,
                    releaseInfo = info,
                    statusMessage = status
                )
            }
        }
    }

    fun downloadApk(url: String, onReadyToInstall: (File) -> Unit) {
        if (_updateUiState.value.isDownloading) return
        viewModelScope.launch {
            _updateUiState.update {
                it.copy(
                    isDownloading = true,
                    downloadProgress = 0,
                    statusMessage = "Güncelleme paketi indiriliyor..."
                )
            }
            val file = UpdateChecker.downloadApk(
                context = getApplication(),
                downloadUrl = url,
                onProgress = { pct ->
                    _updateUiState.update { it.copy(downloadProgress = pct) }
                }
            )
            if (file != null && file.exists()) {
                _updateUiState.update {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = 100,
                        statusMessage = "İndirme tamamlandı, yükleyici açılıyor..."
                    )
                }
                onReadyToInstall(file)
            } else {
                _updateUiState.update {
                    it.copy(
                        isDownloading = false,
                        statusMessage = "İndirme başarısız. Tarayıcıdan indirmeyi deneyin."
                    )
                }
            }
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(application) as T
                }
            }
    }
}

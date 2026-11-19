package com.lyricsync.app.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.lyricsync.app.data.repository.LyricsRepository
import com.lyricsync.app.data.repository.MediaStateBus
import com.lyricsync.app.domain.MediaTrackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MediaNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var positionTickerJob: Job? = null
    private lateinit var repository: LyricsRepository

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            selectAndAttachController(controllers)
        }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            syncCurrentControllerState()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // Fires immediately on Play, Pause, and Seek (ileri/geri sarma)
            syncCurrentControllerState()
            if (state?.state != PlaybackState.STATE_PLAYING) {
                refreshSessions()
            }
        }

        override fun onSessionDestroyed() {
            refreshSessions()
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = LyricsRepository(applicationContext)
        mediaSessionManager =
            getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

        // Automatically fetch & translate lyrics whenever the active track or target language changes
        serviceScope.launch {
            combine(
                MediaStateBus.trackState.map { it.trackId }.distinctUntilChanged(),
                MediaStateBus.targetLanguage
            ) { _, targetLang ->
                Pair(MediaStateBus.trackState.value, targetLang)
            }.collectLatest { (track, targetLang) ->
                if (track.hasActiveTrack) {
                    repository.loadAndTranslateLyrics(track, targetLang)
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val componentName = ComponentName(this, MediaNotificationListenerService::class.java)
        runCatching {
            mediaSessionManager?.addOnActiveSessionsChangedListener(
                sessionsChangedListener,
                componentName
            )
            refreshSessions()
        }
        startPositionTicker()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Re-evaluate sessions whenever a media notification arrives or current player is paused
        if (activeController == null ||
            activeController?.playbackState?.state != PlaybackState.STATE_PLAYING
        ) {
            refreshSessions()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        cleanupController()
        runCatching {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        }
    }

    override fun onDestroy() {
        cleanupController()
        runCatching {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun refreshSessions() {
        val componentName = ComponentName(this, MediaNotificationListenerService::class.java)
        val controllers = runCatching {
            mediaSessionManager?.getActiveSessions(componentName)
        }.getOrNull()
        selectAndAttachController(controllers)
    }

    private fun selectAndAttachController(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) {
            cleanupController()
            return
        }

        // Prefer actively playing Spotify session, then any playing session, then first with metadata
        val chosen = controllers.firstOrNull {
            it.packageName.contains("spotify", ignoreCase = true) &&
                it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull {
            it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.isNotBlank() == true
        } ?: controllers.first()

        if (activeController?.sessionToken != chosen.sessionToken) {
            activeController?.unregisterCallback(controllerCallback)
            activeController = chosen
            chosen.registerCallback(controllerCallback)
        }
        syncCurrentControllerState()
    }

    private fun syncCurrentControllerState() {
        val controller = activeController ?: return
        val metadata = controller.metadata
        val playbackState = controller.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        val artist = (
            metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            )?.trim().orEmpty()
        val duration = (metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(0L)
        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
        val position = computeCurrentPosition(playbackState, duration)

        MediaStateBus.updateTrackState(
            MediaTrackState(
                trackName = title,
                artistName = artist,
                packageName = controller.packageName,
                durationMs = duration,
                positionMs = position,
                isPlaying = isPlaying
            )
        )
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = serviceScope.launch {
            var idleTicks = 0
            while (isActive) {
                val controller = activeController
                val state = controller?.playbackState
                if (controller != null && state != null) {
                    val isPlaying = state.state == PlaybackState.STATE_PLAYING
                    val duration = MediaStateBus.trackState.value.durationMs
                    val pos = computeCurrentPosition(state, duration)
                    MediaStateBus.updatePosition(pos, isPlaying)
                    if (!isPlaying) {
                        idleTicks++
                    } else {
                        idleTicks = 0
                    }
                } else {
                    idleTicks++
                }
                if (idleTicks >= 20) {
                    idleTicks = 0
                    refreshSessions()
                }
                delay(100L)
            }
        }
    }

    private fun computeCurrentPosition(state: PlaybackState?, durationMs: Long): Long {
        if (state == null) return 0L
        val basePos = state.position.coerceAtLeast(0L)
        val calculated = if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0L) {
            val elapsed = (SystemClock.elapsedRealtime() - state.lastPositionUpdateTime).coerceAtLeast(0L)
            val speed = if (state.playbackSpeed > 0f) state.playbackSpeed else 1f
            basePos + (elapsed * speed).toLong()
        } else {
            basePos
        }
        return if (durationMs > 0L) calculated.coerceAtMost(durationMs) else calculated
    }

    private fun cleanupController() {
        positionTickerJob?.cancel()
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
    }
}

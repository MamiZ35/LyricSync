package com.lyricsync.app.service

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.Rating
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

    private var lastSyncedControllerTrackId: String = ""
    private var lastUserSeekElapsedRealtime: Long = 0L
    private var lastUserSeekPositionMs: Long = 0L
    private var cachedArtTrackId: String = ""
    private var cachedAlbumArt: Bitmap? = null
    private var cachedAccentColor: Int? = null

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
        MediaStateBus.init(applicationContext)
        repository = LyricsRepository(applicationContext)
        mediaSessionManager =
            getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

        MediaStateBus.onPlayPauseToggle = {
            activeController?.let { controller ->
                if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    controller.transportControls.pause()
                } else {
                    controller.transportControls.play()
                }
            }
        }
        MediaStateBus.onSeekToMs = { posMs ->
            activeController?.let { controller ->
                lastUserSeekElapsedRealtime = SystemClock.elapsedRealtime()
                lastUserSeekPositionMs = posMs
                controller.transportControls.seekTo(posMs)
                val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
                MediaStateBus.updatePosition(posMs, isPlaying)
            }
        }
        MediaStateBus.onSkipNext = {
            activeController?.transportControls?.skipToNext()
        }
        MediaStateBus.onSkipPrevious = {
            activeController?.transportControls?.skipToPrevious()
        }
        MediaStateBus.onSyncFavoriteToPlayer = { isFavorite ->
            val controller = activeController
            if (controller == null) {
                false
            } else {
                val actions = controller.playbackState?.customActions.orEmpty()
                val preferredAction = actions.firstOrNull { ca ->
                    val label = "${ca.action} ${ca.name}"
                    if (isFavorite) {
                        label.contains("ADD_TO_COLLECTION", ignoreCase = true)
                    } else {
                        label.contains("REMOVE_FROM_COLLECTION", ignoreCase = true)
                    }
                } ?: actions.firstOrNull { ca ->
                    val label = "${ca.action} ${ca.name}"
                    label.contains("ADD_TO_COLLECTION", ignoreCase = true) ||
                        label.contains("REMOVE_FROM_COLLECTION", ignoreCase = true) ||
                        label.contains("COLLECTION", ignoreCase = true) ||
                        label.contains("HEART", ignoreCase = true) ||
                        label.contains("LIKE", ignoreCase = true) ||
                        label.contains("FAVORITE", ignoreCase = true)
                }
                preferredAction?.let { customAction ->
                    runCatching {
                        controller.transportControls.sendCustomAction(customAction, customAction.extras)
                    }
                }
                runCatching {
                    controller.transportControls.setRating(Rating.newHeartRating(isFavorite))
                }
                true
            }
        }

        // Automatically fetch & translate lyrics whenever track, target language, or engine changes
        serviceScope.launch {
            combine(
                MediaStateBus.trackState.map { it.trackId }.distinctUntilChanged(),
                MediaStateBus.targetLanguage,
                MediaStateBus.translationEngine
            ) { _, targetLang, _ ->
                Pair(MediaStateBus.trackState.value, targetLang)
            }.collectLatest { (track, targetLang) ->
                if (track.hasActiveTrack && !track.isManualOverride) {
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
        positionTickerJob?.cancel()
        cleanupController()
        runCatching {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        }
    }

    override fun onDestroy() {
        MediaStateBus.onPlayPauseToggle = null
        MediaStateBus.onSeekToMs = null
        MediaStateBus.onSkipNext = null
        MediaStateBus.onSkipPrevious = null
        MediaStateBus.onSyncFavoriteToPlayer = null
        positionTickerJob?.cancel()
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
        if (positionTickerJob?.isActive != true) {
            startPositionTicker()
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
        val controllerTrackId = "${artist.lowercase()}|${title.lowercase()}"

        // Do not overwrite user's manual search unless a live player switched to a different playing track
        val currentState = MediaStateBus.trackState.value
        if (currentState.isManualOverride) {
            val isNewLiveTrack = title.isNotBlank() &&
                isPlaying &&
                controllerTrackId != lastSyncedControllerTrackId
            if (!isNewLiveTrack) return
        }

        if (controllerTrackId != lastSyncedControllerTrackId) {
            lastUserSeekElapsedRealtime = 0L
        }
        lastSyncedControllerTrackId = controllerTrackId

        if (title.isBlank()) return

        if (cachedArtTrackId != controllerTrackId || cachedAlbumArt == null) {
            val rawArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            cachedArtTrackId = controllerTrackId
            cachedAlbumArt = rawArt?.let { downscaleBitmap(it, 256) }
            cachedAccentColor = cachedAlbumArt?.let { extractDominantColor(it) }
        }

        MediaStateBus.updateTrackState(
            MediaTrackState(
                trackName = title,
                artistName = artist,
                packageName = controller.packageName,
                durationMs = duration,
                positionMs = position,
                isPlaying = isPlaying,
                albumArt = cachedAlbumArt,
                accentColorArgb = cachedAccentColor,
                isManualOverride = false
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
                var isCurrentlyPlaying = false
                if (controller != null && state != null) {
                    val isPlaying = state.state == PlaybackState.STATE_PLAYING
                    isCurrentlyPlaying = isPlaying
                    val duration = MediaStateBus.trackState.value.durationMs
                    var pos = computeCurrentPosition(state, duration)

                    // A-B / Stanza loop practice mode: seek back to startTimeMs at endTimeMs
                    val activeLoop = MediaStateBus.loopRange.value
                    if (isPlaying && activeLoop != null && pos >= activeLoop.endTimeMs) {
                        lastUserSeekElapsedRealtime = SystemClock.elapsedRealtime()
                        lastUserSeekPositionMs = activeLoop.startTimeMs
                        controller.transportControls.seekTo(activeLoop.startTimeMs)
                        pos = activeLoop.startTimeMs
                    }

                    MediaStateBus.updatePosition(pos, isPlaying)
                    if (!isPlaying) {
                        idleTicks++
                    } else {
                        idleTicks = 0
                    }
                } else {
                    idleTicks++
                }
                if (idleTicks >= 5) {
                    idleTicks = 0
                    refreshSessions()
                }
                delay(if (isCurrentlyPlaying) 100L else 400L)
            }
        }
    }

    private fun computeCurrentPosition(state: PlaybackState?, durationMs: Long): Long {
        val sinceSeek = SystemClock.elapsedRealtime() - lastUserSeekElapsedRealtime
        if (lastUserSeekElapsedRealtime > 0L && sinceSeek in 0L..550L) {
            val isPlaying = state?.state == PlaybackState.STATE_PLAYING
            val extrapolated = if (isPlaying) lastUserSeekPositionMs + sinceSeek else lastUserSeekPositionMs
            return if (durationMs > 0L) extrapolated.coerceAtMost(durationMs) else extrapolated
        }
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
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
        lastSyncedControllerTrackId = ""
        MediaStateBus.clearTrackIfSessionEnded()
    }

    private fun downscaleBitmap(source: Bitmap, maxDim: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDim && height <= maxDim) return source
        val scale = maxDim.toFloat() / maxOf(width, height).toFloat()
        val targetW = (width * scale).toInt().coerceAtLeast(1)
        val targetH = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }

    private fun extractDominantColor(bitmap: Bitmap): Int? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null

        val stepX = (w / 10).coerceAtLeast(1)
        val stepY = (h / 10).coerceAtLeast(1)
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0

        for (y in 0 until h step stepY) {
            for (x in 0 until w step stepX) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val maxC = maxOf(r, g, b)
                val minC = minOf(r, g, b)
                // Prefer colorful pixels over pure black/white borders
                if (maxC > 30 && (maxC - minC) > 20) {
                    rSum += r
                    gSum += g
                    bSum += b
                    count++
                }
            }
        }
        if (count == 0) return null
        return Color.rgb((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
    }
}

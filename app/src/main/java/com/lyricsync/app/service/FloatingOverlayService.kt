package com.lyricsync.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.lyricsync.app.MainActivity
import com.lyricsync.app.R
import com.lyricsync.app.data.repository.MediaStateBus
import com.lyricsync.app.domain.LyricDisplayMode
import kotlin.math.roundToInt

class FloatingOverlayService : Service(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private lateinit var windowParams: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        MediaStateBus.init(applicationContext)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // Android 8+ requires startForeground() before any early stopSelf(), and Android 14 requires FGS type
        val notification = buildForegroundNotification(MediaStateBus.overlayConfig.value.isTouchLocked)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        showOverlayWindow()
        MediaStateBus.setOverlayActive(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP_OVERLAY -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_LOCK -> {
                MediaStateBus.toggleOverlayTouchLock()
            }
        }
        return START_STICKY
    }

    private fun showOverlayWindow() {
        if (overlayView != null) return
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val initialConfig = MediaStateBus.overlayConfig.value
        val initialFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            initialFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = initialConfig.x
            y = initialConfig.y.coerceAtLeast(0)
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeViewModelStoreOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)

            setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides this@FloatingOverlayService) {
                    FloatingSubtitleOverlayContent(
                        onDrag = { dx, dy ->
                            val dm = resources.displayMetrics
                            val maxAbsX = (dm.widthPixels / 2).coerceAtLeast(120)
                            val maxY = (dm.heightPixels - 120).coerceAtLeast(200)
                            windowParams.x = (windowParams.x + dx.roundToInt()).coerceIn(-maxAbsX, maxAbsX)
                            windowParams.y = (windowParams.y + dy.roundToInt()).coerceIn(0, maxY)
                            runCatching {
                                windowManager?.updateViewLayout(this, windowParams)
                            }
                        },
                        onDragEnd = {
                            MediaStateBus.updateOverlayPosition(windowParams.x, windowParams.y)
                        },
                        onToggleLock = {
                            MediaStateBus.toggleOverlayTouchLock()
                        },
                        onTouchLockChanged = { locked ->
                            updateLockNotification(locked)
                        },
                        onClose = { stopSelf() }
                    )
                }
            }
        }

        overlayView = composeView
        runCatching {
            windowManager?.addView(composeView, windowParams)
        }
    }

    private fun updateLockNotification(isLocked: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildForegroundNotification(isLocked))
    }

    override fun onDestroy() {
        MediaStateBus.setOverlayActive(false)
        overlayView?.let { view ->
            runCatching { view.disposeComposition() }
            runCatching { windowManager?.removeView(view) }
        }
        overlayView = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildForegroundNotification(isTouchLocked: Boolean): Notification {
        val channelId = "lyricsync_overlay_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Yüzen Şarkı Sözü Altyazısı",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingOverlayService::class.java).apply {
                action = ACTION_STOP_OVERLAY
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleLockIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, FloatingOverlayService::class.java).apply {
                action = ACTION_TOGGLE_LOCK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = if (isTouchLocked) {
            "Konum kilidi aktif — açmak için kilit simgesine veya aşağıdaki düğmeye dokunun"
        } else {
            "Spotify üzerinde anlık şarkı sözü çevirisi gösteriliyor"
        }

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle("LyricSync Yüzen Altyazı Aktif")
            .setContentText(contentText)
            .setContentIntent(openAppIntent)
            .addAction(0, if (isTouchLocked) "Kilidi Aç" else "Kilitle", toggleLockIntent)
            .addAction(0, "Kapat", stopIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 2026
        const val ACTION_STOP_OVERLAY = "com.lyricsync.app.ACTION_STOP_OVERLAY"
        const val ACTION_TOGGLE_LOCK = "com.lyricsync.app.ACTION_TOGGLE_LOCK"

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, FloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingOverlayService::class.java))
        }
    }
}

@Composable
private fun FloatingSubtitleOverlayContent(
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onToggleLock: () -> Unit,
    onTouchLockChanged: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val trackState by MediaStateBus.trackState.collectAsStateWithLifecycle()
    val lyricsState by MediaStateBus.lyricsState.collectAsStateWithLifecycle()
    val activeIndex by MediaStateBus.activeLineIndex.collectAsStateWithLifecycle()
    val overlayConfig by MediaStateBus.overlayConfig.collectAsStateWithLifecycle()
    val displayMode by MediaStateBus.displayMode.collectAsStateWithLifecycle()
    val autoHideOnPause by MediaStateBus.autoHideOverlayOnPause.collectAsStateWithLifecycle()
    var isCollapsed by remember { mutableStateOf(false) }
    var isAutoHidden by remember { mutableStateOf(false) }
    var lastNotifiedLockState by remember { mutableStateOf(overlayConfig.isTouchLocked) }

    LaunchedEffect(trackState.isPlaying, trackState.hasActiveTrack, autoHideOnPause) {
        if (autoHideOnPause && trackState.hasActiveTrack && !trackState.isPlaying) {
            kotlinx.coroutines.delay(3500L)
            isAutoHidden = true
        } else {
            isAutoHidden = false
        }
    }

    LaunchedEffect(overlayConfig.isTouchLocked) {
        if (overlayConfig.isTouchLocked != lastNotifiedLockState) {
            lastNotifiedLockState = overlayConfig.isTouchLocked
            onTouchLockChanged(overlayConfig.isTouchLocked)
        }
    }

    if (isAutoHidden) {
        Box(modifier = Modifier.size(0.dp))
        return
    }

    val activeLine = remember(lyricsState.lines, activeIndex) {
        if (activeIndex in lyricsState.lines.indices) {
            lyricsState.lines[activeIndex]
        } else {
            lyricsState.lines.firstOrNull()
        }
    }
    val nextLine = remember(lyricsState.lines, activeIndex) {
        val currentIdx = when {
            activeIndex in lyricsState.lines.indices -> activeIndex
            lyricsState.lines.isNotEmpty() -> 0
            else -> -1
        }
        if (currentIdx >= 0 && currentIdx + 1 < lyricsState.lines.size) {
            lyricsState.lines[currentIdx + 1]
        } else {
            null
        }
    }

    val shape = RoundedCornerShape(22.dp)
    val bgAlpha = overlayConfig.opacity.coerceIn(0.35f, 1.0f)

    Box(
        modifier = Modifier
            .padding(6.dp)
            .widthIn(min = if (isCollapsed) 48.dp else 272.dp, max = 350.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A).copy(alpha = bgAlpha),
                        Color(0xFF1E293B).copy(alpha = bgAlpha)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    if (overlayConfig.isTouchLocked) {
                        listOf(Color(0x66FBBF24), Color(0x33F59E0B))
                    } else {
                        listOf(Color(0x8838BDF8), Color(0x44818CF8))
                    }
                ),
                shape = shape
            )
            .then(
                if (!overlayConfig.isTouchLocked) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        ) { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (isCollapsed) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { isCollapsed = false }
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Genişlet",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Altyazı",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header bar with drag indicator, track pill, lock, and close buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !overlayConfig.isTouchLocked) {
                                isCollapsed = true
                            }
                    ) {
                        Icon(
                            imageVector = if (overlayConfig.isTouchLocked) Icons.Default.Lock else Icons.Default.DragIndicator,
                            contentDescription = if (overlayConfig.isTouchLocked) "Kilitli" else "Sürükle",
                            tint = if (overlayConfig.isTouchLocked) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (trackState.hasActiveTrack) {
                                "${trackState.trackName} • ${trackState.artistName}"
                            } else {
                                "LyricSync • Şarkı bekleniyor"
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(
                                onClickLabel = if (overlayConfig.isTouchLocked) "Kilidi Aç" else "Konumu Kilitle"
                            ) { onToggleLock() },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(
                                    if (overlayConfig.isTouchLocked) Color(0x44FBBF24) else Color(0x33FFFFFF)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (overlayConfig.isTouchLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (overlayConfig.isTouchLocked) "Kilidi Aç" else "Konumu Kilitle",
                                tint = if (overlayConfig.isTouchLocked) Color(0xFFFBBF24) else Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    if (!overlayConfig.isTouchLocked) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable(onClickLabel = "Kapat") { onClose() },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Kapat",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Active synced lyric based on displayMode & overlay fontSizeSp
                AnimatedContent(
                    targetState = activeLine,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "OverlayLyricLineTransition"
                ) { line ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        when {
                            !trackState.hasActiveTrack -> {
                                Text(
                                    text = "Spotify'da bir şarkı başlatın...",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            lyricsState.isLoading -> {
                                Text(
                                    text = lyricsState.statusMessage ?: "Sözler yükleniyor...",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            line != null -> {
                                val hasTranslation = line.translatedText.isNotBlank() &&
                                    line.translatedText != line.originalText
                                val showOriginal = displayMode == LyricDisplayMode.DUAL ||
                                    displayMode == LyricDisplayMode.ORIGINAL_ONLY ||
                                    (displayMode == LyricDisplayMode.TRANSLATION_ONLY && !hasTranslation)
                                val showTranslation = hasTranslation && (
                                    displayMode == LyricDisplayMode.DUAL ||
                                        displayMode == LyricDisplayMode.TRANSLATION_ONLY
                                    )

                                if (showOriginal) {
                                    Text(
                                        text = line.originalText,
                                        color = Color.White,
                                        fontSize = overlayConfig.fontSizeSp.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                if (showTranslation) {
                                    if (showOriginal) Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = line.translatedText,
                                        color = Color(0xFF38BDF8),
                                        fontSize = if (displayMode == LyricDisplayMode.TRANSLATION_ONLY) {
                                            overlayConfig.fontSizeSp.sp
                                        } else {
                                            (overlayConfig.fontSizeSp - 2f).coerceAtLeast(11f).sp
                                        },
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                } else if (lyricsState.isDownloadingModel) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Dil modeli indiriliyor...",
                                        color = Color(0xFFFBBF24),
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            else -> {
                                Text(
                                    text = lyricsState.errorMessage ?: "Söz bekleniyor...",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                if (overlayConfig.showNextLine &&
                    trackState.hasActiveTrack &&
                    !lyricsState.isLoading &&
                    nextLine != null &&
                    nextLine.originalText.isNotBlank()
                ) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "▸ ${nextLine.originalText}",
                        color = Color(0xFF94A3B8),
                        fontSize = (overlayConfig.fontSizeSp - 3f).coerceIn(10f, 14f).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (!overlayConfig.isTouchLocked) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClickLabel = "Yazıyı küçült") {
                                    MediaStateBus.updateOverlayConfig(
                                        overlayConfig.copy(
                                            fontSizeSp = (overlayConfig.fontSizeSp - 1f).coerceIn(12f, 24f)
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x22FFFFFF))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "A-",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable(onClickLabel = "Önceki") {
                                    MediaStateBus.onSkipPrevious?.invoke()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x22FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Önceki",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable(
                                    onClickLabel = if (trackState.isPlaying) "Duraklat" else "Oynat"
                                ) {
                                    MediaStateBus.onPlayPauseToggle?.invoke()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x4438BDF8)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (trackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (trackState.isPlaying) "Duraklat" else "Oynat",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable(onClickLabel = "Sonraki") {
                                    MediaStateBus.onSkipNext?.invoke()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x22FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Sonraki",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClickLabel = "Yazıyı büyüt") {
                                    MediaStateBus.updateOverlayConfig(
                                        overlayConfig.copy(
                                            fontSizeSp = (overlayConfig.fontSizeSp + 1f).coerceIn(12f, 24f)
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x22FFFFFF))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "A+",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

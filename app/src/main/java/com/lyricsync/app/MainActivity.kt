package com.lyricsync.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lyricsync.app.data.repository.LyricsRepository
import com.lyricsync.app.data.repository.MediaStateBus
import com.lyricsync.app.domain.ThemeMode
import com.lyricsync.app.service.FloatingOverlayService
import com.lyricsync.app.service.MediaNotificationListenerService
import com.lyricsync.app.ui.screens.MainScreen
import com.lyricsync.app.ui.screens.PermissionOnboardingScreen
import com.lyricsync.app.ui.theme.LyricSyncTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: LyricsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MediaStateBus.init(applicationContext)
        repository = LyricsRepository(applicationContext)

        setContent {
            var hasNotificationAccess by remember {
                mutableStateOf(isNotificationServiceEnabled(this))
            }
            var hasOverlayPermission by remember {
                mutableStateOf(Settings.canDrawOverlays(this))
            }
            var onboardingCompleted by rememberSaveable {
                mutableStateOf(hasNotificationAccess)
            }

            // Request POST_NOTIFICATIONS runtime permission on Android 13+ for foreground overlay notification
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = {}
            )
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        val notifGranted = isNotificationServiceEnabled(this@MainActivity)
                        val overlayGranted = Settings.canDrawOverlays(this@MainActivity)
                        hasNotificationAccess = notifGranted
                        hasOverlayPermission = overlayGranted
                        if (notifGranted) {
                            runCatching {
                                NotificationListenerService.requestRebind(
                                    ComponentName(
                                        this@MainActivity,
                                        MediaNotificationListenerService::class.java
                                    )
                                )
                            }
                        }
                        if (notifGranted && overlayGranted) {
                            onboardingCompleted = true
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val trackState by MediaStateBus.trackState.collectAsState()
            val lyricsState by MediaStateBus.lyricsState.collectAsState()
            val activeLineIndex by MediaStateBus.activeLineIndex.collectAsState()
            val isOverlayActive by MediaStateBus.isOverlayActive.collectAsState()
            val themeMode by MediaStateBus.themeMode.collectAsState()
            val displayMode by MediaStateBus.displayMode.collectAsState()
            val translationEngine by MediaStateBus.translationEngine.collectAsState()
            val overlayConfig by MediaStateBus.overlayConfig.collectAsState()
            val wifiOnlyDownload by MediaStateBus.wifiOnlyDownload.collectAsState()
            val coroutineScope = rememberCoroutineScope()
            var activeTranslationJob by remember { mutableStateOf<Job?>(null) }

            LyricSyncTheme(themeMode = themeMode) {
                if (!onboardingCompleted) {
                    PermissionOnboardingScreen(
                        hasNotificationAccess = hasNotificationAccess,
                        hasOverlayPermission = hasOverlayPermission,
                        onOpenAppDetails = {
                            openAppDetailsSettings()
                        },
                        onRequestNotificationAccess = {
                            openNotificationListenerSettings()
                        },
                        onRequestOverlayPermission = {
                            openOverlaySettings()
                        },
                        onContinueToApp = {
                            onboardingCompleted = true
                        }
                    )
                } else {
                    MainScreen(
                        hasNotificationAccess = hasNotificationAccess,
                        trackState = trackState,
                        lyricsState = lyricsState,
                        activeLineIndex = activeLineIndex,
                        isOverlayActive = isOverlayActive,
                        themeMode = themeMode,
                        displayMode = displayMode,
                        translationEngine = translationEngine,
                        overlayConfig = overlayConfig,
                        wifiOnlyDownload = wifiOnlyDownload,
                        onOpenPermissions = {
                            onboardingCompleted = false
                        },
                        onToggleTheme = {
                            val nextTheme = when (themeMode) {
                                ThemeMode.DARK -> ThemeMode.LIGHT
                                ThemeMode.LIGHT -> ThemeMode.SYSTEM
                                ThemeMode.SYSTEM -> ThemeMode.DARK
                            }
                            MediaStateBus.setThemeMode(nextTheme)
                        },
                        onSelectTargetLanguage = { langCode ->
                            MediaStateBus.setTargetLanguage(langCode)
                            if (trackState.hasActiveTrack && (trackState.isManualOverride || !hasNotificationAccess)) {
                                activeTranslationJob?.cancel()
                                activeTranslationJob = coroutineScope.launch {
                                    repository.loadAndTranslateLyrics(trackState, langCode)
                                }
                            }
                        },
                        onSelectDisplayMode = { mode ->
                            MediaStateBus.setDisplayMode(mode)
                        },
                        onSelectTranslationEngine = { engine ->
                            MediaStateBus.setTranslationEngine(engine)
                            if (trackState.hasActiveTrack && (trackState.isManualOverride || !hasNotificationAccess)) {
                                activeTranslationJob?.cancel()
                                activeTranslationJob = coroutineScope.launch {
                                    repository.loadAndTranslateLyrics(
                                        trackState,
                                        MediaStateBus.targetLanguage.value
                                    )
                                }
                            }
                        },
                        onToggleOverlay = {
                            if (!Settings.canDrawOverlays(this)) {
                                openOverlaySettings()
                            } else if (isOverlayActive) {
                                FloatingOverlayService.stop(this)
                            } else {
                                FloatingOverlayService.start(this)
                            }
                        },
                        onRetryLyrics = {
                            if (trackState.hasActiveTrack) {
                                activeTranslationJob?.cancel()
                                activeTranslationJob = coroutineScope.launch {
                                    repository.loadAndTranslateLyrics(
                                        trackState,
                                        MediaStateBus.targetLanguage.value,
                                        forceRefresh = true
                                    )
                                }
                            }
                        },
                        onSeekToMs = { posMs ->
                            MediaStateBus.onSeekToMs?.invoke(posMs)
                        },
                        onPlayPauseToggle = {
                            MediaStateBus.onPlayPauseToggle?.invoke()
                        },
                        onSkipNext = {
                            MediaStateBus.onSkipNext?.invoke()
                        },
                        onSkipPrevious = {
                            MediaStateBus.onSkipPrevious?.invoke()
                        },
                        onSearchManual = { query ->
                            repository.searchLyricsManual(query)
                        },
                        onSelectManualResult = { item ->
                            activeTranslationJob?.cancel()
                            activeTranslationJob = coroutineScope.launch {
                                repository.selectManualLyrics(
                                    item = item,
                                    targetLang = MediaStateBus.targetLanguage.value
                                )
                            }
                        },
                        onUpdateOverlayConfig = { newConfig ->
                            MediaStateBus.updateOverlayConfig(newConfig)
                        },
                        onSetWifiOnlyDownload = { wifiOnly ->
                            MediaStateBus.setWifiOnlyDownload(wifiOnly)
                        },
                        onLoadCacheAndModels = {
                            Pair(repository.getCachedSongCount(), repository.getDownloadedModels())
                        },
                        onClearCache = {
                            repository.clearCache()
                        },
                        onDeleteModel = { langCode ->
                            repository.deleteModel(langCode)
                        }
                    )
                }
            }
        }
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        return NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    private fun openAppDetailsSettings() {
        runCatching {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun openNotificationListenerSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun openOverlaySettings() {
        runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}

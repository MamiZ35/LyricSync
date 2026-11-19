package com.lyricsync.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: LyricsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = LyricsRepository(applicationContext)

        setContent {
            var themeMode by rememberSaveable { mutableStateOf(ThemeMode.DARK) }
            var hasNotificationAccess by remember {
                mutableStateOf(isNotificationServiceEnabled(this))
            }
            var hasOverlayPermission by remember {
                mutableStateOf(Settings.canDrawOverlays(this))
            }
            var onboardingCompleted by rememberSaveable {
                mutableStateOf(hasNotificationAccess && hasOverlayPermission)
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
            val coroutineScope = rememberCoroutineScope()

            LyricSyncTheme(themeMode = themeMode) {
                if (!hasNotificationAccess || !onboardingCompleted) {
                    PermissionOnboardingScreen(
                        hasNotificationAccess = hasNotificationAccess,
                        hasOverlayPermission = hasOverlayPermission,
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
                        trackState = trackState,
                        lyricsState = lyricsState,
                        activeLineIndex = activeLineIndex,
                        isOverlayActive = isOverlayActive,
                        themeMode = themeMode,
                        onToggleTheme = {
                            themeMode = when (themeMode) {
                                ThemeMode.DARK -> ThemeMode.LIGHT
                                ThemeMode.LIGHT -> ThemeMode.SYSTEM
                                ThemeMode.SYSTEM -> ThemeMode.DARK
                            }
                        },
                        onSelectTargetLanguage = { langCode ->
                            MediaStateBus.setTargetLanguage(langCode)
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
                                coroutineScope.launch {
                                    repository.loadAndTranslateLyrics(
                                        trackState,
                                        MediaStateBus.targetLanguage.value
                                    )
                                }
                            }
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

package com.lyricsync.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.WindowManager
import android.widget.Toast
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
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lyricsync.app.data.remote.UpdateChecker
import com.lyricsync.app.data.repository.LyricsRepository
import com.lyricsync.app.data.repository.MediaStateBus
import com.lyricsync.app.domain.LyricLine
import com.lyricsync.app.domain.ThemeMode
import com.lyricsync.app.service.FloatingOverlayService
import com.lyricsync.app.service.MediaNotificationListenerService
import com.lyricsync.app.ui.screens.MainScreen
import com.lyricsync.app.ui.screens.PermissionOnboardingScreen
import com.lyricsync.app.ui.theme.LyricSyncTheme
import com.lyricsync.app.ui.util.LyricCardGenerator
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var repository: LyricsRepository
    private var pendingInstallApkFile: File? = null

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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                            packageManager.canRequestPackageInstalls()
                        ) {
                            pendingInstallApkFile?.let { file ->
                                pendingInstallApkFile = null
                                installDownloadedApk(file)
                            }
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
            val keepScreenOn by MediaStateBus.keepScreenOn.collectAsState()
            val autoHideOverlayOnPause by MediaStateBus.autoHideOverlayOnPause.collectAsState()
            val loopRange by MediaStateBus.loopRange.collectAsState()
            val coroutineScope = rememberCoroutineScope()
            var activeTranslationJob by remember { mutableStateOf<Job?>(null) }

            LaunchedEffect(keepScreenOn) {
                if (keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

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
                        keepScreenOn = keepScreenOn,
                        autoHideOverlayOnPause = autoHideOverlayOnPause,
                        loopRange = loopRange,
                        onOpenPermissions = {
                            onboardingCompleted = false
                        },
                        onToggleTheme = {
                            val nextTheme = when (themeMode) {
                                ThemeMode.DARK -> ThemeMode.AMOLED
                                ThemeMode.AMOLED -> ThemeMode.LIGHT
                                ThemeMode.LIGHT -> ThemeMode.SYSTEM
                                ThemeMode.SYSTEM -> ThemeMode.DARK
                            }
                            MediaStateBus.setThemeMode(nextTheme)
                            Toast.makeText(this, "Tema: ${nextTheme.label}", Toast.LENGTH_SHORT).show()
                        },
                        onToggleKeepScreenOn = {
                            MediaStateBus.setKeepScreenOn(!keepScreenOn)
                        },
                        onSetAutoHideOverlayOnPause = { enabled ->
                            MediaStateBus.setAutoHideOverlayOnPause(enabled)
                        },
                        onSetLoopRange = { range ->
                            MediaStateBus.setLoopRange(range)
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
                        },
                        onLoadLibrarySummaries = {
                            repository.getLibrarySummaries()
                        },
                        onToggleFavorite = { cacheKey, isFav ->
                            repository.toggleFavorite(cacheKey, isFav)
                        },
                        onSelectCachedSong = { cacheKey ->
                            activeTranslationJob?.cancel()
                            activeTranslationJob = coroutineScope.launch {
                                repository.loadCachedSongByKey(cacheKey)
                            }
                        },
                        onCopyLine = { line ->
                            copyLyricLine(line)
                        },
                        onShareLine = { line ->
                            shareLyricLine(line, trackState.trackName, trackState.artistName)
                        },
                        onShareImageCard = { line ->
                            coroutineScope.launch {
                                LyricCardGenerator.generateAndShareCard(
                                    context = this@MainActivity,
                                    track = trackState,
                                    line = line
                                )
                            }
                        },
                        onCheckForUpdate = {
                            UpdateChecker.checkLatestRelease(BuildConfig.VERSION_NAME)
                        },
                        onDownloadAndInstallApk = { url, onProgress, onComplete ->
                            coroutineScope.launch {
                                val file = UpdateChecker.downloadApk(
                                    context = this@MainActivity,
                                    downloadUrl = url,
                                    onProgress = onProgress
                                )
                                if (file != null && file.exists()) {
                                    onComplete(true)
                                    installDownloadedApk(file)
                                } else {
                                    onComplete(false)
                                }
                            }
                        },
                        onOpenDownloadUrl = { url ->
                            openDownloadUrl(url)
                        }
                    )
                }
            }
        }
    }

    private fun formatLineForShare(line: LyricLine): String {
        return if (line.translatedText.isNotBlank() && line.translatedText != line.originalText) {
            "${line.originalText}\n${line.translatedText}"
        } else {
            line.originalText
        }
    }

    private fun copyLyricLine(line: LyricLine) {
        val text = formatLineForShare(line)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("LyricSync", text))
        Toast.makeText(this, "Panoya kopyalandı", Toast.LENGTH_SHORT).show()
    }

    private fun shareLyricLine(line: LyricLine, trackName: String, artistName: String) {
        val header = if (trackName.isNotBlank()) "$trackName - $artistName\n\n" else ""
        val shareText = "$header${formatLineForShare(line)}"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        runCatching {
            startActivity(Intent.createChooser(sendIntent, "Sözü Paylaş"))
        }
    }

    private fun openDownloadUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun installDownloadedApk(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallApkFile = apkFile
            Toast.makeText(
                this,
                "Güncellemeyi kurmak için 'Bu kaynaktan izin ver' seçeneğini açın",
                Toast.LENGTH_LONG
            ).show()
            runCatching {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    )
                )
            }
            return
        }

        runCatching {
            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(installIntent)
        }.onFailure {
            Toast.makeText(this, "Kurulum başlatılamadı: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
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

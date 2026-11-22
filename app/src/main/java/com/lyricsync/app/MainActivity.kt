package com.lyricsync.app

import android.Manifest
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lyricsync.app.data.repository.MediaStateBus
import com.lyricsync.app.domain.LyricLine
import com.lyricsync.app.service.FloatingOverlayService
import com.lyricsync.app.service.MediaNotificationListenerService
import com.lyricsync.app.service.PackageInstallReceiver
import com.lyricsync.app.ui.MainViewModel
import com.lyricsync.app.ui.screens.MainScreen
import com.lyricsync.app.ui.screens.PermissionOnboardingScreen
import com.lyricsync.app.ui.theme.LyricSyncTheme
import com.lyricsync.app.ui.util.LyricCardGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private var pendingInstallApkFile: File? = null
    @Volatile
    private var isCreatingInstallSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MediaStateBus.init(applicationContext)

        setContent {
            val viewModel: MainViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
            val autoLaunchSpotify by viewModel.autoLaunchSpotify.collectAsStateWithLifecycle()
            val trackState by viewModel.trackState.collectAsStateWithLifecycle()

            var hasNotificationAccess by remember {
                mutableStateOf(isNotificationServiceEnabled(this))
            }
            var hasOverlayPermission by remember {
                mutableStateOf(Settings.canDrawOverlays(this))
            }
            var onboardingCompleted by rememberSaveable {
                mutableStateOf(hasNotificationAccess)
            }
            var hasAttemptedNotificationAccess by rememberSaveable {
                mutableStateOf(false)
            }
            var isSessionInstalling by remember {
                mutableStateOf(false)
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
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            MediaStateBus.setAppInForeground(true)
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
                                    installDownloadedApk(file) { installing ->
                                        isSessionInstalling = installing
                                    }
                                }
                            }
                        }
                        Lifecycle.Event.ON_PAUSE -> {
                            MediaStateBus.setAppInForeground(false)
                        }
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    MediaStateBus.setAppInForeground(false)
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val coroutineScope = rememberCoroutineScope()
            var autoLaunchAttempted by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(onboardingCompleted) {
                if (onboardingCompleted && !autoLaunchAttempted) {
                    autoLaunchAttempted = true
                    if (autoLaunchSpotify && !trackState.hasActiveTrack) {
                        wakeOrLaunchSpotify(returnToLyricSync = true)
                    }
                }
            }

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
                        hasAttemptedNotificationAccess = hasAttemptedNotificationAccess,
                        isSessionInstalling = isSessionInstalling,
                        onOpenAppDetails = { openAppDetailsSettings() },
                        onRequestNotificationAccess = {
                            hasAttemptedNotificationAccess = true
                            openNotificationListenerSettings()
                        },
                        onReinstallViaSession = {
                            reinstallSelfViaSession { installing ->
                                isSessionInstalling = installing
                            }
                        },
                        onRequestOverlayPermission = { openOverlaySettings() },
                        onContinueToApp = { onboardingCompleted = true }
                    )
                } else {
                    MainScreen(
                        viewModel = viewModel,
                        hasNotificationAccess = hasNotificationAccess,
                        onOpenPermissions = { onboardingCompleted = false },
                        onToggleOverlay = {
                            if (!Settings.canDrawOverlays(this)) {
                                openOverlaySettings()
                            } else if (viewModel.isOverlayActive.value) {
                                FloatingOverlayService.stop(this)
                            } else {
                                FloatingOverlayService.start(this)
                            }
                        },
                        onLaunchSpotify = { wakeOrLaunchSpotify(returnToLyricSync = false) },
                        onCopyLine = ::copyLyricLine,
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
                        onFavoriteFeedback = {
                            Toast.makeText(
                                this@MainActivity,
                                "LyricSync favorilerine eklendi",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onInstallApk = { file ->
                            installDownloadedApk(file)
                        },
                        onOpenDownloadUrl = ::openDownloadUrl
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

    private fun reinstallSelfViaSession(onInstallingStateChanged: (Boolean) -> Unit) {
        val baseApk = applicationInfo.sourceDir?.let { File(it) }
        if (baseApk == null || !baseApk.exists() || !baseApk.canRead()) {
            Toast.makeText(
                this,
                "Uygulama paketi okunamadı. Play Store'dan SAI ile kurmayı deneyin.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        installDownloadedApk(baseApk, onInstallingStateChanged)
    }

    private fun installDownloadedApk(
        apkFile: File,
        onInstallingStateChanged: ((Boolean) -> Unit)? = null
    ) {
        if (isCreatingInstallSession) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            onInstallingStateChanged?.invoke(false)
            pendingInstallApkFile = apkFile
            Toast.makeText(
                this,
                "Kurulumu başlatmak için 'Bu kaynaktan izin ver' seçeneğini açın",
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

        isCreatingInstallSession = true
        onInstallingStateChanged?.invoke(true)
        lifecycleScope.launch(Dispatchers.IO) {
            var session: PackageInstaller.Session? = null
            val installResult = runCatching {
                val packageInstaller = packageManager.packageInstaller
                runCatching {
                    packageInstaller.mySessions.forEach { info ->
                        runCatching { packageInstaller.abandonSession(info.sessionId) }
                    }
                }
                val sizeBytes = apkFile.length()
                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL
                ).apply {
                    setAppPackageName(packageName)
                    if (sizeBytes > 0L) {
                        setSize(sizeBytes)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }
                }
                val sessionId = packageInstaller.createSession(params)
                val activeSession = packageInstaller.openSession(sessionId).also { session = it }
                val writeLength = sizeBytes.takeIf { it > 0L } ?: -1L
                apkFile.inputStream().use { input ->
                    activeSession.openWrite("base.apk", 0L, writeLength).use { output ->
                        input.copyTo(output)
                        activeSession.fsync(output)
                    }
                }
                val callbackIntent = Intent(
                    this@MainActivity,
                    PackageInstallReceiver::class.java
                ).apply {
                    action = PackageInstallReceiver.ACTION_INSTALL_COMMIT
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    this@MainActivity,
                    sessionId,
                    callbackIntent,
                    flags
                )
                activeSession.commit(pendingIntent.intentSender)
                activeSession.close()
                session = null
            }

            withContext(Dispatchers.Main) {
                isCreatingInstallSession = false
                onInstallingStateChanged?.invoke(false)
                installResult.onFailure { error ->
                    runCatching { session?.abandon() }
                    runCatching { session?.close() }
                    fallbackInstallViaFileProvider(apkFile, error)
                }
            }
        }
    }

    private fun fallbackInstallViaFileProvider(apkFile: File, error: Throwable) {
        val isInCache = runCatching {
            val canonicalApk = apkFile.canonicalPath
            val cachePath = cacheDir.canonicalPath
            val extCachePath = externalCacheDir?.canonicalPath
            canonicalApk.startsWith(cachePath) ||
                (extCachePath != null && canonicalApk.startsWith(extCachePath))
        }.getOrDefault(false)

        if (!isInCache) {
            Toast.makeText(
                this,
                "Oturum kurulumu başlatılamadı: ${error.localizedMessage ?: "Bilinmeyen hata"}",
                Toast.LENGTH_LONG
            ).show()
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
        }.onFailure { fallbackError ->
            Toast.makeText(
                this,
                "Kurulum başlatılamadı: ${fallbackError.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        return NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    private fun wakeOrLaunchSpotify(returnToLyricSync: Boolean) {
        val currentTrack = MediaStateBus.trackState.value
        if (currentTrack.hasActiveTrack && !currentTrack.isPlaying && MediaStateBus.onPlayPauseToggle != null) {
            MediaStateBus.onPlayPauseToggle?.invoke()
            return
        }

        val spotifyIntent = packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE)
        if (spotifyIntent == null) {
            Toast.makeText(this, "Spotify uygulaması cihazda bulunamadı.", Toast.LENGTH_SHORT).show()
            return
        }

        spotifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(spotifyIntent) }

        if (returnToLyricSync) {
            lifecycleScope.launch {
                delay(650L)
                if (isFinishing || isDestroyed) return@launch
                runCatching {
                    val playDown = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                        setPackage(SPOTIFY_PACKAGE)
                        putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                    }
                    val playUp = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                        setPackage(SPOTIFY_PACKAGE)
                        putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
                    }
                    sendBroadcast(playDown)
                    sendBroadcast(playUp)
                }
                if (MediaStateBus.trackState.value.let { it.hasActiveTrack && !it.isPlaying }) {
                    MediaStateBus.onPlayPauseToggle?.invoke()
                }
                // Best-effort return to foreground; Android 10+ BAL may suppress background startActivity
                runCatching {
                    startActivity(
                        Intent(this@MainActivity, MainActivity::class.java).addFlags(
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                    )
                }
            }
        }
    }

    private fun openAppDetailsSettings() {
        runCatching {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            ).apply {
                putExtra(":settings:fragment_args_key", "allow_restricted_settings")
            }
            startActivity(intent)
        }
    }

    private fun openNotificationListenerSettings() {
        val componentName = ComponentName(packageName, MediaNotificationListenerService::class.java.name)
        val componentString = componentName.flattenToString()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val detailIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, componentString)
            }
            if (runCatching { startActivity(detailIntent) }.isSuccess) {
                return
            }
        }

        val fallbackIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            putExtra(":settings:fragment_args_key", componentString)
            putExtra(
                ":settings:show_fragment_args",
                Bundle().apply { putString(":settings:fragment_args_key", componentString) }
            )
        }
        runCatching { startActivity(fallbackIntent) }
            .onFailure {
                runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
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

    private companion object {
        const val SPOTIFY_PACKAGE = "com.spotify.music"
    }
}

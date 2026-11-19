package com.lyricsync.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.nl.translate.TranslateLanguage
import com.lyricsync.app.BuildConfig
import com.lyricsync.app.data.remote.LrcLibResponse
import com.lyricsync.app.data.remote.ReleaseInfo
import com.lyricsync.app.data.remote.UpdateChecker
import com.lyricsync.app.domain.LyricDisplayMode
import com.lyricsync.app.domain.LyricLine
import com.lyricsync.app.domain.LyricsState
import com.lyricsync.app.domain.MediaTrackState
import com.lyricsync.app.domain.OverlayConfig
import com.lyricsync.app.domain.ThemeMode
import com.lyricsync.app.domain.TranslationEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SUPPORTED_TARGET_LANGUAGES = listOf(
    TranslateLanguage.TURKISH to "Türkçe (TR)",
    TranslateLanguage.ENGLISH to "English (EN)",
    TranslateLanguage.GERMAN to "Deutsch (DE)",
    TranslateLanguage.SPANISH to "Español (ES)",
    TranslateLanguage.FRENCH to "Français (FR)",
    TranslateLanguage.ITALIAN to "Italiano (IT)",
    TranslateLanguage.RUSSIAN to "Русский (RU)",
    TranslateLanguage.JAPANESE to "日本語 (JA)",
    TranslateLanguage.KOREAN to "한국어 (KO)",
    TranslateLanguage.ARABIC to "العربية (AR)"
)

@Composable
fun MainScreen(
    hasNotificationAccess: Boolean,
    trackState: MediaTrackState,
    lyricsState: LyricsState,
    activeLineIndex: Int,
    isOverlayActive: Boolean,
    themeMode: ThemeMode,
    displayMode: LyricDisplayMode,
    translationEngine: TranslationEngine,
    overlayConfig: OverlayConfig,
    wifiOnlyDownload: Boolean,
    onOpenPermissions: () -> Unit,
    onToggleTheme: () -> Unit,
    onSelectTargetLanguage: (String) -> Unit,
    onSelectDisplayMode: (LyricDisplayMode) -> Unit,
    onSelectTranslationEngine: (TranslationEngine) -> Unit,
    onToggleOverlay: () -> Unit,
    onRetryLyrics: () -> Unit,
    onSeekToMs: (Long) -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSearchManual: suspend (String) -> List<LrcLibResponse>,
    onSelectManualResult: (LrcLibResponse) -> Unit,
    onUpdateOverlayConfig: (OverlayConfig) -> Unit,
    onSetWifiOnlyDownload: (Boolean) -> Unit,
    onLoadCacheAndModels: suspend () -> Pair<Int, List<String>>,
    onClearCache: suspend () -> Unit,
    onDeleteModel: suspend (String) -> Unit,
    onCopyLine: (LyricLine) -> Unit,
    onShareLine: (LyricLine) -> Unit,
    onCheckForUpdate: suspend () -> ReleaseInfo?,
    onOpenDownloadUrl: (String) -> Unit
) {
    val listState = rememberLazyListState()
    var showSearchDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isAutoScrollPaused by remember { mutableStateOf(false) }
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    var selectedLineForActions by remember { mutableStateOf<LyricLine?>(null) }

    LaunchedEffect(lyricsState.trackId) {
        isAutoScrollPaused = false
    }

    // Pause auto-scroll when user manually drags/flings the list
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !isProgrammaticScroll) {
            isAutoScrollPaused = true
        }
    }

    // Resume auto-scroll 4.5 seconds after user finishes scrolling
    LaunchedEffect(isAutoScrollPaused, listState.isScrollInProgress) {
        if (isAutoScrollPaused && !listState.isScrollInProgress) {
            delay(4500L)
            isAutoScrollPaused = false
        }
    }

    // Automatically center the active lyric line on track load, lyric ready, or line change
    LaunchedEffect(activeLineIndex, lyricsState.trackId, lyricsState.lines.size, isAutoScrollPaused) {
        if (!isAutoScrollPaused &&
            lyricsState.isSynced &&
            activeLineIndex in lyricsState.lines.indices
        ) {
            val viewportHeight = listState.layoutInfo.viewportEndOffset -
                listState.layoutInfo.viewportStartOffset
            val centerOffset = if (viewportHeight > 0) -(viewportHeight / 3) else -220
            isProgrammaticScroll = true
            try {
                listState.animateScrollToItem(
                    index = activeLineIndex,
                    scrollOffset = centerOffset
                )
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Top Control Bar + Quick Action Chips
            TopHeaderBar(
                targetLang = lyricsState.targetLang,
                displayMode = displayMode,
                isOverlayActive = isOverlayActive,
                themeMode = themeMode,
                onToggleTheme = onToggleTheme,
                onSelectTargetLanguage = onSelectTargetLanguage,
                onSelectDisplayMode = onSelectDisplayMode,
                onToggleOverlay = onToggleOverlay,
                onOpenSearch = { showSearchDialog = true },
                onOpenSettings = { showSettingsDialog = true },
                onRetryLyrics = onRetryLyrics
            )

            // Notification Access Warning Banner (when user entered via Manual Search mode)
            if (!hasNotificationAccess) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF59E0B).copy(alpha = 0.14f))
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .clickable { onOpenPermissions() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Otomatik Spotify takibi kapalı (Manuel mod). İzin vermek için dokunun.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Now Playing Track Card with Album Art & Transport Controls
            NowPlayingCard(
                trackState = trackState,
                lyricsState = lyricsState,
                onPlayPauseToggle = onPlayPauseToggle,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
                onOpenSearch = { showSearchDialog = true }
            )

            // ML Kit Model Download / Status Banner
            if (lyricsState.statusMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                StatusBanner(
                    message = lyricsState.statusMessage,
                    isDownloading = lyricsState.isDownloadingModel
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Lyrics Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    !trackState.hasActiveTrack -> {
                        EmptyLyricsPlaceholder(
                            title = if (hasNotificationAccess) "Aktif Müzik Bekleniyor" else "Manuel Şarkı Arama Hazır",
                            subtitle = if (hasNotificationAccess) {
                                "Spotify'da bir şarkı açtığınızda senkronize sözler burada belirecek veya sağ üstten manuel arayabilirsiniz."
                            } else {
                                "Üstteki arama simgesine dokunarak herhangi bir şarkıyı arayıp anında çevirebilirsiniz."
                            },
                            onOpenSearch = { showSearchDialog = true }
                        )
                    }
                    lyricsState.isLoading && lyricsState.lines.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    lyricsState.lines.isEmpty() -> {
                        EmptyLyricsPlaceholder(
                            title = lyricsState.errorMessage ?: "Şarkı sözü bulunamadı",
                            subtitle = "LRCLIB veritabanında eşleşme bulunamadıysa manuel arama yapmayı deneyebilirsiniz.",
                            onOpenSearch = { showSearchDialog = true }
                        )
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!lyricsState.isSynced) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Düz Metin Modu (Senkronize zaman damgası bulunamadı)",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            itemsIndexed(
                                items = lyricsState.lines,
                                key = { idx, line -> "${idx}_${line.timeMs}" }
                            ) { index, line ->
                                val isActive = lyricsState.isSynced && index == activeLineIndex
                                val isPast = lyricsState.isSynced && activeLineIndex > index
                                val canSeek = lyricsState.isSynced && !trackState.isManualOverride
                                LyricLineRow(
                                    line = line,
                                    displayMode = displayMode,
                                    isActive = isActive,
                                    isPast = isPast,
                                    isSynced = lyricsState.isSynced,
                                    canSeek = canSeek,
                                    onSeek = { onSeekToMs(line.timeMs) },
                                    onLongPress = {
                                        onCopyLine(line)
                                        selectedLineForActions = line
                                    }
                                )
                            }
                        }

                        // Floating "Jump back to active line" button while manual scrolling pauses auto-follow
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isAutoScrollPaused &&
                                lyricsState.isSynced &&
                                activeLineIndex in lyricsState.lines.indices,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 14.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                shadowElevation = 6.dp,
                                modifier = Modifier.clickable { isAutoScrollPaused = false }
                            ) {
                                Text(
                                    text = "🎯 Aktif Satıra Dön",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedLineForActions?.let { line ->
        AlertDialog(
            onDismissRequest = { selectedLineForActions = null },
            title = { Text("Söz İşlemleri", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = line.originalText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    if (line.translatedText.isNotBlank() && line.translatedText != line.originalText) {
                        Text(
                            text = line.translatedText,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        text = "Panoya kopyalandı ✓",
                        fontSize = 12.sp,
                        color = Color(0xFF10B981)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onCopyLine(line)
                    selectedLineForActions = null
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Kopyala")
                }
            },
            confirmButton = {
                Button(onClick = {
                    onShareLine(line)
                    selectedLineForActions = null
                }) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Paylaş")
                }
            }
        )
    }

    if (showSearchDialog) {
        ManualSearchDialog(
            initialQuery = trackState.trackName,
            onDismiss = { showSearchDialog = false },
            onSearch = onSearchManual,
            onSelect = { item ->
                showSearchDialog = false
                onSelectManualResult(item)
            }
        )
    }

    if (showSettingsDialog) {
        SettingsAndModelsDialog(
            overlayConfig = overlayConfig,
            translationEngine = translationEngine,
            wifiOnlyDownload = wifiOnlyDownload,
            onUpdateOverlayConfig = onUpdateOverlayConfig,
            onSelectTranslationEngine = onSelectTranslationEngine,
            onSetWifiOnlyDownload = onSetWifiOnlyDownload,
            onLoadCacheAndModels = onLoadCacheAndModels,
            onClearCache = onClearCache,
            onDeleteModel = onDeleteModel,
            onCheckForUpdate = onCheckForUpdate,
            onOpenDownloadUrl = onOpenDownloadUrl,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun TopHeaderBar(
    targetLang: String,
    displayMode: LyricDisplayMode,
    isOverlayActive: Boolean,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    onSelectTargetLanguage: (String) -> Unit,
    onSelectDisplayMode: (LyricDisplayMode) -> Unit,
    onToggleOverlay: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetryLyrics: () -> Unit
) {
    var langMenuExpanded by remember { mutableStateOf(false) }
    var displayMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "LyricSync",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Manuel Şarkı Ara",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Ayarlar & Modeller",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRetryLyrics) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Yenile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onToggleTheme) {
                    val icon = when (themeMode) {
                        ThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
                        ThemeMode.DARK -> Icons.Default.DarkMode
                        ThemeMode.LIGHT -> Icons.Default.LightMode
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Tema Değiştir",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Scrollable compact action chips row (Language, Display Mode, Overlay)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Target Language Picker Chip
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { langMenuExpanded = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = "Hedef Dil",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Dil: ${targetLang.uppercase()}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                DropdownMenu(
                    expanded = langMenuExpanded,
                    onDismissRequest = { langMenuExpanded = false }
                ) {
                    SUPPORTED_TARGET_LANGUAGES.forEach { (code, label) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    fontWeight = if (code == targetLang) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                langMenuExpanded = false
                                onSelectTargetLanguage(code)
                            }
                        )
                    }
                }
            }

            // Display Mode Switcher Chip
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { displayMenuExpanded = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Görünüm Modu",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = displayMode.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                DropdownMenu(
                    expanded = displayMenuExpanded,
                    onDismissRequest = { displayMenuExpanded = false }
                ) {
                    LyricDisplayMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = mode.label,
                                    fontWeight = if (mode == displayMode) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                displayMenuExpanded = false
                                onSelectDisplayMode(mode)
                            }
                        )
                    }
                }
            }

            // Floating Overlay Toggle Chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isOverlayActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onToggleOverlay() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PictureInPictureAlt,
                    contentDescription = "Yüzen Altyazı",
                    tint = if (isOverlayActive) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (isOverlayActive) "Overlay Açık" else "Yüzen Pencere",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isOverlayActive) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun NowPlayingCard(
    trackState: MediaTrackState,
    lyricsState: LyricsState,
    onPlayPauseToggle: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onOpenSearch: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val progress = if (trackState.durationMs > 0L) {
        (trackState.positionMs.toFloat() / trackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val accentColor = trackState.accentColorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        accentColor.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.35f),
                shape = shape
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                if (trackState.albumArt != null) {
                    Image(
                        bitmap = trackState.albumArt.asImageBitmap(),
                        contentDescription = "Albüm Kapağı",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (trackState.hasActiveTrack) trackState.trackName else "Şarkı Çalmıyor",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (trackState.hasActiveTrack) trackState.artistName else "Spotify başlatın veya manuel arayın",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Metadata Badges (Language pair, Manual mode & Room cache indicator)
            Column(horizontalAlignment = Alignment.End) {
                if (lyricsState.sourceLang.isNotBlank()) {
                    Text(
                        text = "${lyricsState.sourceLang.uppercase()} → ${lyricsState.targetLang.uppercase()}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (lyricsState.isFromCache) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Önbellek ✓",
                        fontSize = 10.sp,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (trackState.isManualOverride) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Manuel",
                        fontSize = 10.sp,
                        color = Color(0xFFF59E0B),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (trackState.hasActiveTrack && !trackState.isManualOverride) {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(trackState.positionMs),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Media Transport Controls (Previous / Play-Pause / Next)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onSkipPrevious, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Önceki",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            .clickable { onPlayPauseToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (trackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (trackState.isPlaying) "Duraklat" else "Oynat",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onSkipNext, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Sonraki",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = formatDuration(trackState.durationMs),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (!trackState.hasActiveTrack) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onOpenSearch,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Manuel Şarkı Sözü Ara", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StatusBanner(
    message: String,
    isDownloading: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isDownloading) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = message,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LyricLineRow(
    line: LyricLine,
    displayMode: LyricDisplayMode,
    isActive: Boolean,
    isPast: Boolean,
    isSynced: Boolean,
    canSeek: Boolean,
    onSeek: () -> Unit,
    onLongPress: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.03f else 1.0f,
        animationSpec = tween(durationMillis = 240),
        label = "LyricScale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 240),
        label = "LyricBg"
    )

    val originalColor = when {
        !isSynced -> MaterialTheme.colorScheme.onSurface
        isActive -> MaterialTheme.colorScheme.onBackground
        isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    }

    val translationColor = when {
        !isSynced -> MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
        isActive -> MaterialTheme.colorScheme.primary
        isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
    }

    val hasTranslation = line.translatedText.isNotBlank() && line.translatedText != line.originalText
    val showOriginal = displayMode == LyricDisplayMode.DUAL ||
        displayMode == LyricDisplayMode.ORIGINAL_ONLY ||
        (displayMode == LyricDisplayMode.TRANSLATION_ONLY && !hasTranslation)
    val showTranslation = hasTranslation && (
        displayMode == LyricDisplayMode.DUAL ||
            displayMode == LyricDisplayMode.TRANSLATION_ONLY
        )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .then(
                if (isActive) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(18.dp)
                    )
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = { if (canSeek) onSeek() },
                onLongClick = { onLongPress() }
            )
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        if (showOriginal) {
            Text(
                text = line.originalText,
                color = originalColor,
                fontSize = if (isActive) 21.sp else 17.sp,
                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = if (isActive) 27.sp else 23.sp
            )
        }

        if (showTranslation) {
            if (showOriginal) Spacer(modifier = Modifier.height(5.dp))
            val isTranslationPrimary = displayMode == LyricDisplayMode.TRANSLATION_ONLY
            Text(
                text = line.translatedText,
                color = if (isTranslationPrimary) originalColor else translationColor,
                fontSize = when {
                    isTranslationPrimary && isActive -> 20.sp
                    isTranslationPrimary -> 17.sp
                    isActive -> 16.sp
                    else -> 14.sp
                },
                fontWeight = when {
                    isTranslationPrimary && isActive -> FontWeight.ExtraBold
                    isTranslationPrimary || isActive -> FontWeight.SemiBold
                    else -> FontWeight.Normal
                },
                textAlign = TextAlign.Center,
                lineHeight = if (isActive) 23.sp else 19.sp
            )
        }
    }
}

@Composable
private fun ManualSearchDialog(
    initialQuery: String,
    onDismiss: () -> Unit,
    onSearch: suspend (String) -> List<LrcLibResponse>,
    onSelect: (LrcLibResponse) -> Unit
) {
    var query by remember { mutableStateOf(initialQuery) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<LrcLibResponse>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun triggerSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            isSearching = true
            hasSearched = true
            results = onSearch(q)
            isSearching = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Manuel Şarkı / Söz Ara", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Şarkı veya sanatçı adı") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { triggerSearch() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { triggerSearch() },
                    enabled = query.isNotBlank() && !isSearching,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("LRCLIB'de Ara")
                }

                Spacer(modifier = Modifier.height(10.dp))

                when {
                    isSearching -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    hasSearched && results.isEmpty() -> {
                        Text(
                            text = "Sonuç bulunamadı. Şarkı ve sanatçı adını sadeleştirerek tekrar deneyin.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    results.isNotEmpty() -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(results) { item ->
                                val isSyncedItem = !item.syncedLyrics.isNullOrBlank()
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        .clickable { onSelect(item) }
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = item.trackName ?: "Bilinmeyen Şarkı",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.artistName ?: "",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isSyncedItem) "Senkronize" else "Düz Metin",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSyncedItem) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Kapat") }
        }
    )
}

@Composable
private fun SettingsAndModelsDialog(
    overlayConfig: OverlayConfig,
    translationEngine: TranslationEngine,
    wifiOnlyDownload: Boolean,
    onUpdateOverlayConfig: (OverlayConfig) -> Unit,
    onSelectTranslationEngine: (TranslationEngine) -> Unit,
    onSetWifiOnlyDownload: (Boolean) -> Unit,
    onLoadCacheAndModels: suspend () -> Pair<Int, List<String>>,
    onClearCache: suspend () -> Unit,
    onDeleteModel: suspend (String) -> Unit,
    onCheckForUpdate: suspend () -> ReleaseInfo?,
    onOpenDownloadUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var cachedCount by remember { mutableStateOf(0) }
    var downloadedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(true) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var releaseInfo by remember { mutableStateOf<ReleaseInfo?>(null) }
    val scope = rememberCoroutineScope()

    fun refreshStats() {
        scope.launch {
            isRefreshing = true
            val (count, models) = onLoadCacheAndModels()
            cachedCount = count
            downloadedModels = models
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        refreshStats()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Ayarlar & Dil Modelleri", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Yüzen Pencere (Overlay)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Yazı Boyutu: ${overlayConfig.fontSizeSp.roundToInt()} sp",
                    fontSize = 13.sp
                )
                Slider(
                    value = overlayConfig.fontSizeSp,
                    onValueChange = { newSize ->
                        onUpdateOverlayConfig(overlayConfig.copy(fontSizeSp = newSize))
                    },
                    valueRange = 12f..24f
                )

                Text(
                    text = "Arka Plan Opaklığı: ${(overlayConfig.opacity * 100).roundToInt()}%",
                    fontSize = 13.sp
                )
                Slider(
                    value = overlayConfig.opacity,
                    onValueChange = { newOpacity ->
                        onUpdateOverlayConfig(overlayConfig.copy(opacity = newOpacity))
                    },
                    valueRange = 0.35f..1.0f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Dokunma Kilidi (Click-Through)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Açıkken overlay altındaki Spotify butonlarına dokunabilirsiniz.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = overlayConfig.isTouchLocked,
                        onCheckedChange = { locked ->
                            onUpdateOverlayConfig(overlayConfig.copy(isTouchLocked = locked))
                        }
                    )
                }

                HorizontalDivider()

                Text(
                    text = "Çeviri & Önbellek",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Çeviri Motoru",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                TranslationEngine.entries.forEach { engine ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (engine == translationEngine) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .clickable { onSelectTranslationEngine(engine) }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = engine == translationEngine,
                            onClick = { onSelectTranslationEngine(engine) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = engine.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (engine == TranslationEngine.CLOUD_CONTEXT) {
                                    "Tüm şarkıyı bağlamıyla tek seferde anlamlı çevirir (0.3 sn)."
                                } else {
                                    "İnternetsiz çalışır (cihazda dil modeli indirir)."
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Sadece Wi-Fi ile Model İndir", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Mobil veri harcamamak için dil modellerini yalnızca Wi-Fi'da indirir.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = wifiOnlyDownload,
                        onCheckedChange = onSetWifiOnlyDownload
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Önbellekte $cachedCount şarkı",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                onClearCache()
                                refreshStats()
                            }
                        },
                        enabled = cachedCount > 0 && !isRefreshing,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Temizle", fontSize = 12.sp)
                    }
                }

                Text(
                    text = "İndirilen ML Kit Dil Modelleri",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                when {
                    isRefreshing -> {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    downloadedModels.isEmpty() -> {
                        Text(
                            text = "Henüz indirilmiş dil modeli yok.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        downloadedModels.forEach { langCode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Model: ${langCode.uppercase()}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            onDeleteModel(langCode)
                                            refreshStats()
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Modeli Sil",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Uygulama Güncellemesi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Mevcut Sürüm: v${BuildConfig.VERSION_NAME}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (updateStatus != null) {
                    Text(
                        text = updateStatus!!,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (releaseInfo?.isUpdateAvailable == true) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isCheckingUpdate = true
                                updateStatus = "GitHub kontrol ediliyor..."
                                val info = onCheckForUpdate()
                                releaseInfo = info
                                updateStatus = when {
                                    info == null -> "Güncelleme bilgisi alınamadı."
                                    info.isUpdateAvailable -> "Yeni sürüm bulundu: ${info.latestTag}"
                                    else -> "Güncel sürümü (${info.latestTag}) kullanıyorsunuz ✓"
                                }
                                isCheckingUpdate = false
                            }
                        },
                        enabled = !isCheckingUpdate,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (isCheckingUpdate) "Kontrol..." else "Kontrol Et",
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = {
                            onOpenDownloadUrl(releaseInfo?.downloadUrl ?: UpdateChecker.LATEST_APK_URL)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Son APK İndir", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tamam") }
        }
    )
}

@Composable
private fun EmptyLyricsPlaceholder(
    title: String,
    subtitle: String,
    onOpenSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        OutlinedButton(onClick = onOpenSearch, shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Manuel Şarkı Sözü Ara")
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

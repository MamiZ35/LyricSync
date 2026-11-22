package com.lyricsync.app.ui.screens

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lyricsync.app.domain.LyricLine
import com.lyricsync.app.ui.MainViewModel
import com.lyricsync.app.ui.components.EmptyLyricsPlaceholder
import com.lyricsync.app.ui.components.InstrumentalCountdownBanner
import com.lyricsync.app.ui.components.LibraryAndStatsDialog
import com.lyricsync.app.ui.components.LineActionsDialog
import com.lyricsync.app.ui.components.LyricLineRow
import com.lyricsync.app.ui.components.ManualSearchDialog
import com.lyricsync.app.ui.components.NowPlayingCard
import com.lyricsync.app.ui.components.StatusBanner
import com.lyricsync.app.ui.components.TopHeaderBar
import com.lyricsync.app.ui.components.TrackSeekSlider
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    hasNotificationAccess: Boolean,
    onOpenPermissions: () -> Unit,
    onToggleOverlay: () -> Unit,
    onLaunchSpotify: () -> Unit,
    onCopyLine: (LyricLine) -> Unit,
    onShareLine: (LyricLine) -> Unit,
    onShareImageCard: (LyricLine) -> Unit,
    onFavoriteFeedback: () -> Unit,
    onInstallApk: (File) -> Unit,
    onOpenDownloadUrl: (String) -> Unit
) {
    val trackState by viewModel.trackState.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()
    val activeLineIndex by viewModel.activeLineIndex.collectAsStateWithLifecycle()
    val isOverlayActive by viewModel.isOverlayActive.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val displayMode by viewModel.displayMode.collectAsStateWithLifecycle()
    val translationEngine by viewModel.translationEngine.collectAsStateWithLifecycle()
    val overlayConfig by viewModel.overlayConfig.collectAsStateWithLifecycle()
    val wifiOnlyDownload by viewModel.wifiOnlyDownload.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val autoHideOverlayOnPause by viewModel.autoHideOverlayOnPause.collectAsStateWithLifecycle()
    val autoLaunchSpotify by viewModel.autoLaunchSpotify.collectAsStateWithLifecycle()
    val loopRange by viewModel.loopRange.collectAsStateWithLifecycle()
    val updateUiState by viewModel.updateUiState.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    var showSearchDialog by remember { mutableStateOf(false) }
    var showLibraryDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isAmbientFullscreen by rememberSaveable { mutableStateOf(false) }
    var isAutoScrollPaused by remember { mutableStateOf(false) }
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    var selectedLineIndexForActions by remember { mutableStateOf<Int?>(null) }

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
    LaunchedEffect(activeLineIndex, lyricsState.trackId, lyricsState.lines.size, isAutoScrollPaused, isAmbientFullscreen) {
        if (!isAutoScrollPaused &&
            lyricsState.isSynced &&
            activeLineIndex in lyricsState.lines.indices
        ) {
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            isProgrammaticScroll = true
            try {
                if (viewportHeight > 0) {
                    val beforePadding = layoutInfo.beforeContentPadding
                    val activeItemSize = layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == activeLineIndex }?.size ?: 140
                    val targetY = if (isAmbientFullscreen) {
                        (viewportHeight * 0.40f).toInt()
                    } else {
                        (viewportHeight * 0.26f).toInt()
                    }
                    val centerOffset = beforePadding + (activeItemSize / 2) - targetY
                    listState.animateScrollToItem(
                        index = activeLineIndex,
                        scrollOffset = centerOffset
                    )
                } else {
                    listState.animateScrollToItem(index = activeLineIndex)
                }
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    val ambientAccent = trackState.accentColorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val containerBgModifier = if (isAmbientFullscreen) {
        Modifier.background(
            Brush.verticalGradient(
                listOf(
                    ambientAccent.copy(alpha = 0.28f),
                    MaterialTheme.colorScheme.background,
                    ambientAccent.copy(alpha = 0.16f)
                )
            )
        )
    } else {
        Modifier
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(containerBgModifier)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (isAmbientFullscreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (trackState.hasActiveTrack) trackState.trackName else "Ambiyans Modu",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (trackState.artistName.isNotBlank()) {
                            Text(
                                text = trackState.artistName,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = viewModel::toggleKeepScreenOn) {
                            Icon(
                                imageVector = Icons.Default.BrightnessHigh,
                                contentDescription = "Ekranı Açık Tut",
                                tint = if (keepScreenOn) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
                                .clickable { isAmbientFullscreen = false }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = "Ambiyanstan Çık",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Çık",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (trackState.hasActiveTrack && !trackState.isManualOverride) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TrackSeekSlider(
                        playbackPositionFlow = viewModel.playbackPositionMs,
                        durationMs = trackState.durationMs,
                        isPlaying = trackState.isPlaying,
                        onSeekToMs = viewModel::seekToMs,
                        onPlayPauseToggle = viewModel::playPauseToggle,
                        onSkipPrevious = viewModel::skipPrevious,
                        onSkipNext = viewModel::skipNext
                    )
                }
            } else {
                TopHeaderBar(
                    targetLang = lyricsState.targetLang,
                    displayMode = displayMode,
                    isOverlayActive = isOverlayActive,
                    keepScreenOn = keepScreenOn,
                    syncOffsetMs = lyricsState.syncOffsetMs,
                    showSyncOffset = lyricsState.isSynced && lyricsState.lines.isNotEmpty(),
                    onToggleKeepScreenOn = viewModel::toggleKeepScreenOn,
                    onEnterAmbient = { isAmbientFullscreen = true },
                    onAdjustSyncOffset = viewModel::adjustSyncOffset,
                    onResetSyncOffset = viewModel::resetSyncOffset,
                    onSelectTargetLanguage = { lang ->
                        viewModel.selectTargetLanguage(lang, hasNotificationAccess)
                    },
                    onSelectDisplayMode = viewModel::setDisplayMode,
                    onToggleOverlay = onToggleOverlay,
                    onOpenSearch = { showSearchDialog = true },
                    onOpenLibrary = { showLibraryDialog = true },
                    onOpenSettings = { showSettingsDialog = true },
                    onRetryLyrics = viewModel::retryLyrics
                )

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

                NowPlayingCard(
                    trackState = trackState,
                    lyricsState = lyricsState,
                    playbackPositionFlow = viewModel.playbackPositionMs,
                    onSeekToMs = viewModel::seekToMs,
                    onPlayPauseToggle = viewModel::playPauseToggle,
                    onSkipPrevious = viewModel::skipPrevious,
                    onSkipNext = viewModel::skipNext,
                    onLaunchSpotify = onLaunchSpotify,
                    onOpenSearch = { showSearchDialog = true }
                )
            }

            lyricsState.statusMessage?.let { statusMsg ->
                Spacer(modifier = Modifier.height(10.dp))
                StatusBanner(
                    message = statusMsg,
                    isDownloading = lyricsState.isDownloadingModel
                )
            }

            loopRange?.let { activeLoop ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF59E0B).copy(alpha = 0.16f))
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Kıta Döngüsü (Satır ${activeLoop.startLineIndex + 1}–${activeLoop.endLineIndex + 1})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    TextButton(
                        onClick = { viewModel.setLoopRange(null) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Kapat", fontSize = 11.sp, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                    }
                }
            }

            InstrumentalCountdownBanner(
                trackState = trackState,
                lyricsState = lyricsState,
                activeLineIndex = activeLineIndex,
                playbackPositionFlow = viewModel.playbackPositionMs
            )

            Spacer(modifier = Modifier.height(10.dp))

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
                            contentPadding = PaddingValues(top = 48.dp, bottom = 260.dp),
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
                                val activeLoop = loopRange
                                val isInLoop = activeLoop != null &&
                                    index in activeLoop.startLineIndex..activeLoop.endLineIndex
                                val canSeek = lyricsState.isSynced && !trackState.isManualOverride
                                LyricLineRow(
                                    line = line,
                                    displayMode = displayMode,
                                    isActive = isActive,
                                    isPast = isPast,
                                    isInLoop = isInLoop,
                                    isSynced = lyricsState.isSynced,
                                    canSeek = canSeek,
                                    onSeek = {
                                        viewModel.seekToMs((line.timeMs - lyricsState.syncOffsetMs).coerceAtLeast(0L))
                                    },
                                    onLongPress = {
                                        selectedLineIndexForActions = index
                                    }
                                )
                            }
                        }

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

    selectedLineIndexForActions?.let { selectedIdx ->
        val line = lyricsState.lines.getOrNull(selectedIdx)
        if (line != null) {
            LineActionsDialog(
                selectedIdx = selectedIdx,
                line = line,
                lyricsState = lyricsState,
                trackState = trackState,
                onShareImageCard = onShareImageCard,
                onSetLoopRange = viewModel::setLoopRange,
                onSeekToMs = viewModel::seekToMs,
                onCopyLine = onCopyLine,
                onShareLine = onShareLine,
                onDismiss = { selectedLineIndexForActions = null }
            )
        }
    }

    if (showSearchDialog) {
        ManualSearchDialog(
            initialQuery = trackState.trackName,
            onDismiss = { showSearchDialog = false },
            onSearch = viewModel::searchManual,
            onSelect = { item ->
                showSearchDialog = false
                viewModel.selectManualResult(item)
            }
        )
    }

    if (showLibraryDialog) {
        LibraryAndStatsDialog(
            onLoadSummaries = viewModel::loadLibrarySummaries,
            onToggleFavorite = { cacheKey, isFav ->
                val syncedToPlayer = viewModel.toggleFavorite(cacheKey, isFav)
                if (isFav && !syncedToPlayer) {
                    onFavoriteFeedback()
                }
            },
            onSelectSong = { cacheKey ->
                showLibraryDialog = false
                viewModel.selectCachedSong(cacheKey)
            },
            onDismiss = { showLibraryDialog = false }
        )
    }

    if (showSettingsDialog) {
        SettingsAndModelsDialog(
            themeMode = themeMode,
            keepScreenOn = keepScreenOn,
            autoLaunchSpotify = autoLaunchSpotify,
            overlayConfig = overlayConfig,
            translationEngine = translationEngine,
            wifiOnlyDownload = wifiOnlyDownload,
            autoHideOverlayOnPause = autoHideOverlayOnPause,
            updateUiState = updateUiState,
            onSelectThemeMode = viewModel::setThemeMode,
            onSetKeepScreenOn = viewModel::setKeepScreenOn,
            onSetAutoLaunchSpotify = viewModel::setAutoLaunchSpotify,
            onPreviewOverlayConfig = viewModel::previewOverlayConfig,
            onCommitOverlayConfig = viewModel::commitOverlayConfig,
            onSelectTranslationEngine = { engine ->
                viewModel.selectTranslationEngine(engine, hasNotificationAccess)
            },
            onSetWifiOnlyDownload = viewModel::setWifiOnlyDownload,
            onSetAutoHideOverlayOnPause = viewModel::setAutoHideOverlayOnPause,
            onLoadCacheAndModels = viewModel::loadCacheAndModels,
            onClearCache = viewModel::clearCache,
            onDeleteModel = viewModel::deleteModel,
            onCheckForUpdate = viewModel::checkForUpdate,
            onDownloadAndInstallApk = { url ->
                viewModel.downloadApk(url, onReadyToInstall = onInstallApk)
            },
            onOpenDownloadUrl = onOpenDownloadUrl,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

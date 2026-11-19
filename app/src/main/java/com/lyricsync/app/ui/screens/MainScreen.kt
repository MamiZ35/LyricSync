package com.lyricsync.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.nl.translate.TranslateLanguage
import com.lyricsync.app.domain.LyricLine
import com.lyricsync.app.domain.LyricsState
import com.lyricsync.app.domain.MediaTrackState
import com.lyricsync.app.domain.ThemeMode

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
    trackState: MediaTrackState,
    lyricsState: LyricsState,
    activeLineIndex: Int,
    isOverlayActive: Boolean,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    onSelectTargetLanguage: (String) -> Unit,
    onToggleOverlay: () -> Unit,
    onRetryLyrics: () -> Unit
) {
    val listState = rememberLazyListState()

    // Automatically center the active lyric line in the LazyColumn viewport (unless user is dragging)
    LaunchedEffect(activeLineIndex, lyricsState.trackId) {
        if (lyricsState.isSynced &&
            activeLineIndex in lyricsState.lines.indices &&
            !listState.isScrollInProgress
        ) {
            val viewportHeight = listState.layoutInfo.viewportEndOffset -
                listState.layoutInfo.viewportStartOffset
            val centerOffset = if (viewportHeight > 0) -(viewportHeight / 3) else -220
            runCatching {
                listState.animateScrollToItem(
                    index = activeLineIndex,
                    scrollOffset = centerOffset
                )
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
            // Top Control Bar
            TopHeaderBar(
                targetLang = lyricsState.targetLang,
                isOverlayActive = isOverlayActive,
                themeMode = themeMode,
                onToggleTheme = onToggleTheme,
                onSelectTargetLanguage = onSelectTargetLanguage,
                onToggleOverlay = onToggleOverlay,
                onRetryLyrics = onRetryLyrics
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Now Playing Track Card
            NowPlayingCard(
                trackState = trackState,
                lyricsState = lyricsState
            )

            // ML Kit Model Download / Status Banner
            if (lyricsState.statusMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                StatusBanner(
                    message = lyricsState.statusMessage,
                    isDownloading = lyricsState.isDownloadingModel
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Lyrics Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    !trackState.hasActiveTrack -> {
                        EmptyLyricsPlaceholder(
                            title = "Aktif Müzik Bekleniyor",
                            subtitle = "Spotify veya başka bir müzik uygulamasından şarkı açtığınızda senkronize sözler ve anlık çevirisi burada belirecek."
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
                            subtitle = "LRCLIB veritabanında bu parça için henüz kayıtlı söz bulunmuyor olabilir."
                        )
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 140.dp),
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
                                LyricLineRow(
                                    line = line,
                                    isActive = isActive,
                                    isPast = isPast,
                                    isSynced = lyricsState.isSynced
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopHeaderBar(
    targetLang: String,
    isOverlayActive: Boolean,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    onSelectTargetLanguage: (String) -> Unit,
    onToggleOverlay: () -> Unit,
    onRetryLyrics: () -> Unit
) {
    var langMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "LyricSync",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
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
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = targetLang.uppercase(),
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

            Spacer(modifier = Modifier.width(6.dp))

            // Floating Overlay Toggle Button
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
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isOverlayActive) "Overlay Açık" else "Overlay",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isOverlayActive) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            // Refresh Lyrics Button
            IconButton(onClick = onRetryLyrics) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Yenile",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Light / Dark / System Theme Toggle
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
}

@Composable
private fun NowPlayingCard(
    trackState: MediaTrackState,
    lyricsState: LyricsState
) {
    val shape = RoundedCornerShape(22.dp)
    val progress = if (trackState.durationMs > 0L) {
        (trackState.positionMs.toFloat() / trackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                shape = shape
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (trackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (trackState.isPlaying) "Çalıyor" else "Duraklatıldı",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (trackState.hasActiveTrack) trackState.trackName else "Şarkı Çalmıyor",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (trackState.hasActiveTrack) trackState.artistName else "Spotify veya müzik çalarınızı başlatın",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Metadata Badges (Language pair & Room cache indicator)
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
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Önbellek ✓",
                        fontSize = 10.sp,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (trackState.hasActiveTrack) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(trackState.positionMs),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDuration(trackState.durationMs),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

@Composable
private fun LyricLineRow(
    line: LyricLine,
    isActive: Boolean,
    isPast: Boolean,
    isSynced: Boolean
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
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        // Upper line: Original Lyric
        Text(
            text = line.originalText,
            color = originalColor,
            fontSize = if (isActive) 21.sp else 17.sp,
            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = if (isActive) 27.sp else 23.sp
        )

        // Lower line: Translated Lyric in a subtler, refined tone
        if (line.translatedText.isNotBlank() && line.translatedText != line.originalText) {
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = line.translatedText,
                color = translationColor,
                fontSize = if (isActive) 16.sp else 14.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                lineHeight = if (isActive) 22.sp else 19.sp
            )
        }
    }
}

@Composable
private fun EmptyLyricsPlaceholder(
    title: String,
    subtitle: String
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
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

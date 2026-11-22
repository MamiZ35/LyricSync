package com.lyricsync.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lyricsync.app.domain.LyricsState
import com.lyricsync.app.domain.MediaTrackState
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun TrackSeekSlider(
    playbackPositionFlow: StateFlow<Long>,
    durationMs: Long,
    isPlaying: Boolean,
    onSeekToMs: (Long) -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit
) {
    val positionMs by playbackPositionFlow.collectAsStateWithLifecycle()
    var isDragging by remember(durationMs) { mutableStateOf(false) }
    var dragProgress by remember(durationMs) { mutableStateOf(0f) }
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val displayedProgress = if (isDragging) dragProgress else progress
    val displayedPosMs = if (durationMs > 0L) {
        (displayedProgress * durationMs.toFloat()).toLong()
    } else {
        positionMs
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = displayedProgress,
            onValueChange = { value ->
                if (durationMs > 0L) {
                    isDragging = true
                    dragProgress = value.coerceIn(0f, 1f)
                }
            },
            onValueChangeFinished = {
                if (durationMs > 0L) {
                    val targetMs = (dragProgress * durationMs.toFloat()).toLong()
                    onSeekToMs(targetMs)
                }
                isDragging = false
            },
            enabled = durationMs > 0L,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(displayedPosMs),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onSkipPrevious, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Önceki",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        .clickable(onClickLabel = if (isPlaying) "Duraklat" else "Oynat") {
                            onPlayPauseToggle()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = onSkipNext, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Sonraki",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Text(
                text = formatDuration(durationMs),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun NowPlayingCard(
    trackState: MediaTrackState,
    lyricsState: LyricsState,
    playbackPositionFlow: StateFlow<Long>,
    onSeekToMs: (Long) -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onLaunchSpotify: () -> Unit,
    onOpenSearch: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val accentColor = trackState.accentColorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val albumImageBitmap = remember(trackState.albumArt) {
        trackState.albumArt?.asImageBitmap()
    }

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
                if (albumImageBitmap != null) {
                    Image(
                        bitmap = albumImageBitmap,
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
            Spacer(modifier = Modifier.height(6.dp))
            TrackSeekSlider(
                playbackPositionFlow = playbackPositionFlow,
                durationMs = trackState.durationMs,
                isPlaying = trackState.isPlaying,
                onSeekToMs = onSeekToMs,
                onPlayPauseToggle = onPlayPauseToggle,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext
            )
        } else if (!trackState.hasActiveTrack) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onLaunchSpotify,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Spotify'ı Başlat",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onOpenSearch,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Manuel Söz Ara",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun InstrumentalCountdownBanner(
    trackState: MediaTrackState,
    lyricsState: LyricsState,
    activeLineIndex: Int,
    playbackPositionFlow: StateFlow<Long>
) {
    if (!trackState.hasActiveTrack ||
        !trackState.isPlaying ||
        !lyricsState.isSynced ||
        lyricsState.lines.isEmpty() ||
        trackState.isManualOverride
    ) {
        return
    }

    val positionMs by playbackPositionFlow.collectAsStateWithLifecycle()
    val effectivePos = (positionMs + lyricsState.syncOffsetMs).coerceAtLeast(0L)

    val remainingMs = remember(effectivePos, lyricsState.lines, activeLineIndex) {
        if (activeLineIndex == -1) {
            val nextTime = lyricsState.lines[0].timeMs
            val remaining = nextTime - effectivePos
            if (effectivePos >= 300L && nextTime >= 4_000L && remaining in 1_000L..60_000L) {
                remaining
            } else {
                null
            }
        } else if (activeLineIndex in 0 until lyricsState.lines.lastIndex) {
            val curTime = lyricsState.lines[activeLineIndex].timeMs
            val nextTime = lyricsState.lines[activeLineIndex + 1].timeMs
            val gap = nextTime - curTime
            val elapsed = effectivePos - curTime
            val remaining = nextTime - effectivePos
            if (gap >= 14_000L && elapsed >= 6_000L && remaining in 1_000L..60_000L) {
                remaining
            } else {
                null
            }
        } else {
            null
        }
    } ?: return

    val remainingSec = ((remainingMs + 999L) / 1000L).coerceAtLeast(1L)

    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    CircleShape
                )
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "${remainingSec}s",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun StatusBanner(
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

internal fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

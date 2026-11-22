package com.lyricsync.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyricsync.app.domain.LoopRange
import com.lyricsync.app.domain.LyricDisplayMode
import com.lyricsync.app.domain.LyricLine
import com.lyricsync.app.domain.LyricsState
import com.lyricsync.app.domain.MediaTrackState

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LyricLineRow(
    line: LyricLine,
    displayMode: LyricDisplayMode,
    isActive: Boolean,
    isPast: Boolean,
    isInLoop: Boolean,
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
        targetValue = when {
            isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            isInLoop -> Color(0xFFF59E0B).copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 240),
        label = "LyricBg"
    )

    val originalColor = when {
        !isSynced -> MaterialTheme.colorScheme.onSurface
        isActive -> MaterialTheme.colorScheme.onBackground
        isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
    }

    val translationColor = when {
        !isSynced -> MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
        isActive -> MaterialTheme.colorScheme.primary
        isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
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
                when {
                    isActive -> Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    isInLoop -> Modifier.border(
                        width = 1.dp,
                        color = Color(0xFFF59E0B).copy(alpha = 0.45f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    else -> Modifier
                }
            )
            .combinedClickable(
                onClickLabel = if (canSeek) "Bu satıra sar" else null,
                onLongClickLabel = "Söz işlemleri ve paylaşım",
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
internal fun LineActionsDialog(
    selectedIdx: Int,
    line: LyricLine,
    lyricsState: LyricsState,
    trackState: MediaTrackState,
    onShareImageCard: (LyricLine) -> Unit,
    onSetLoopRange: (LoopRange?) -> Unit,
    onSeekToMs: (Long) -> Unit,
    onCopyLine: (LyricLine) -> Unit,
    onShareLine: (LyricLine) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Söz İşlemleri", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

                Button(
                    onClick = {
                        onShareImageCard(line)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Görsel Kart Paylaş (PNG)")
                }

                if (lyricsState.isSynced && !trackState.isManualOverride) {
                    OutlinedButton(
                        onClick = {
                            val endIdx = (selectedIdx + 3).coerceAtMost(lyricsState.lines.lastIndex)
                            val offset = lyricsState.syncOffsetMs
                            val startMs = (lyricsState.lines[selectedIdx].timeMs - offset).coerceAtLeast(0L)
                            val rawEndMs = if (endIdx + 1 < lyricsState.lines.size) {
                                (lyricsState.lines[endIdx + 1].timeMs - offset).coerceAtLeast(0L)
                            } else {
                                val fallback = (lyricsState.lines[endIdx].timeMs - offset).coerceAtLeast(0L) + 12_000L
                                if (trackState.durationMs > 0L) fallback.coerceAtMost(trackState.durationMs) else fallback
                            }
                            val safeEndMs = maxOf(rawEndMs, startMs + 4000L)
                            onSetLoopRange(LoopRange(selectedIdx, endIdx, startMs, safeEndMs))
                            onSeekToMs(startMs)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bu Kıta İçin Döngü Başlat (4 Satır)", fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onCopyLine(line)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kopyala", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            onShareLine(line)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Metin Paylaş", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat")
            }
        }
    )
}

@Composable
internal fun EmptyLyricsPlaceholder(
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

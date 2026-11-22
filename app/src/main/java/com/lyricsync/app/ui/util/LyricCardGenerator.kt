package com.lyricsync.app.ui.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.lyricsync.app.domain.LyricLine
import com.lyricsync.app.domain.MediaTrackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object LyricCardGenerator {

    suspend fun generateAndShareCard(
        context: Context,
        track: MediaTrackState,
        line: LyricLine
    ) {
        val file = withContext(Dispatchers.IO) {
            val width = 1080
            val height = 1350
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)

                val accent = track.accentColorArgb ?: Color.parseColor("#0284C7")
                val darkAccent = blendColors(accent, Color.parseColor("#090D16"), 0.55f)

                // Full-bleed ambient gradient background
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, width.toFloat(), height.toFloat(),
                        intArrayOf(darkAccent, Color.parseColor("#090D16"), Color.parseColor("#131B2E")),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

                // Inner frosted card
                val cardRect = RectF(64f, 100f, 1016f, 1250f)
                val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#CC0F172A")
                }
                canvas.drawRoundRect(cardRect, 48f, 48f, cardPaint)

                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                    color = Color.argb(140, Color.red(accent), Color.green(accent), Color.blue(accent))
                }
                canvas.drawRoundRect(cardRect, 48f, 48f, borderPaint)

                // Album art + track title & artist header
                val artRect = RectF(120f, 156f, 260f, 296f)
                if (track.albumArt != null) {
                    val clipPath = Path().apply { addRoundRect(artRect, 24f, 24f, Path.Direction.CW) }
                    canvas.save()
                    canvas.clipPath(clipPath)
                    val scaledArt = Bitmap.createScaledBitmap(track.albumArt, 140, 140, true)
                    try {
                        canvas.drawBitmap(scaledArt, 120f, 156f, Paint(Paint.FILTER_BITMAP_FLAG))
                    } finally {
                        if (scaledArt !== track.albumArt && !scaledArt.isRecycled) {
                            scaledArt.recycle()
                        }
                    }
                    canvas.restore()
                } else {
                    val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(60, Color.red(accent), Color.green(accent), Color.blue(accent))
                    }
                    canvas.drawRoundRect(artRect, 24f, 24f, placeholderPaint)
                }

                val textStartX = 290f
                val headerWidth = (960f - textStartX).toInt()
                val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 40f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#94A3B8")
                    textSize = 30f
                }

                val titleText = track.trackName.ifBlank { "LyricSync" }
                val artistText = track.artistName.ifBlank { "Şarkı Sözü Kartı" }
                val titleLayout = StaticLayout.Builder.obtain(titleText, 0, titleText.length, titlePaint, headerWidth)
                    .setMaxLines(2)
                    .build()
                canvas.save()
                canvas.translate(textStartX, 175f)
                titleLayout.draw(canvas)
                canvas.restore()

                val artistLayout = StaticLayout.Builder.obtain(artistText, 0, artistText.length, artistPaint, headerWidth)
                    .setMaxLines(1)
                    .build()
                canvas.save()
                canvas.translate(textStartX, 185f + titleLayout.height)
                artistLayout.draw(canvas)
                canvas.restore()

                // Divider line
                val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(55, 255, 255, 255)
                    strokeWidth = 2f
                }
                canvas.drawLine(120f, 344f, 960f, 344f, dividerPaint)

                // Centered original & translated lyric
                val lyricWidth = 820
                val origPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 54f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val origLayout = StaticLayout.Builder.obtain(
                    line.originalText, 0, line.originalText.length, origPaint, lyricWidth
                ).setAlignment(Layout.Alignment.ALIGN_CENTER).build()

                val hasTranslation = line.translatedText.isNotBlank() && line.translatedText != line.originalText
                val transPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#38BDF8")
                    textSize = 44f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                }
                val transLayout = if (hasTranslation) {
                    StaticLayout.Builder.obtain(
                        line.translatedText, 0, line.translatedText.length, transPaint, lyricWidth
                    ).setAlignment(Layout.Alignment.ALIGN_CENTER).build()
                } else {
                    null
                }

                val totalTextHeight = origLayout.height + (transLayout?.let { it.height + 42 } ?: 0)
                val contentTop = (370f + ((760f - totalTextHeight) / 2f)).coerceAtLeast(380f)

                canvas.save()
                canvas.translate(130f, contentTop)
                origLayout.draw(canvas)
                if (transLayout != null) {
                    canvas.translate(0f, origLayout.height + 42f)
                    transLayout.draw(canvas)
                }
                canvas.restore()

                // Bottom watermark
                val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#94A3B8")
                    textSize = 26f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("LyricSync • Anlık Şarkı Sözü Çeviri", width / 2f, 1190f, footerPaint)

                val outDir = File(context.cacheDir, "shared_cards").apply { mkdirs() }
                outDir.listFiles()?.forEach { old ->
                    if (old.isFile && old.name.endsWith(".png")) {
                        runCatching { old.delete() }
                    }
                }
                val outFile = File(outDir, "lyricsync_card_${System.currentTimeMillis()}.png")
                FileOutputStream(outFile).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 96, stream)
                }
                outFile
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }

        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Görsel Kartı Paylaş").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(chooser) }
        }
    }

    private fun blendColors(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val r = (Color.red(c1) * ratio + Color.red(c2) * inv).toInt()
        val g = (Color.green(c1) * ratio + Color.green(c2) * inv).toInt()
        val b = (Color.blue(c1) * ratio + Color.blue(c2) * inv).toInt()
        return Color.rgb(r, g, b)
    }
}

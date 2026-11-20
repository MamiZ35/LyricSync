package com.lyricsync.app.domain

object LrcParser {
    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    /**
     * Parses LRC formatted synced lyrics ([mm:ss.xx] text) into sorted [LyricLine] list.
     */
    fun parseSyncedLyrics(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrBlank()) return emptyList()
        val result = mutableListOf<LyricLine>()

        for (rawLine in lrcContent.lineSequence()) {
            val matches = TIMESTAMP_REGEX.findAll(rawLine).toList()
            if (matches.isEmpty()) continue

            val textStart = matches.last().range.last + 1
            val lyricText = rawLine.substring(textStart).trim()
            if (lyricText.isEmpty()) continue

            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val fractionStr = match.groupValues[3]
                val millis = when (fractionStr.length) {
                    1 -> (fractionStr.toLongOrNull() ?: 0L) * 100L
                    2 -> (fractionStr.toLongOrNull() ?: 0L) * 10L
                    3 -> fractionStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val totalTimeMs = minutes * 60_000L + seconds * 1_000L + millis
                result.add(LyricLine(timeMs = totalTimeMs, originalText = lyricText))
            }
        }

        return result.sortedBy { it.timeMs }
    }

    /**
     * Fallback parser when syncedLyrics is empty/null; splits plainLyrics into lines.
     */
    fun parsePlainLyrics(plainContent: String?): List<LyricLine> {
        if (plainContent.isNullOrBlank()) return emptyList()
        return plainContent
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, text ->
                LyricLine(timeMs = index * 1000L, originalText = text)
            }
            .toList()
    }

    /**
     * Returns the index of the currently active synced lyric line for [positionMs] in O(log N) time.
     */
    fun findActiveLineIndex(lines: List<LyricLine>, positionMs: Long, isSynced: Boolean): Int {
        if (!isSynced || lines.isEmpty() || positionMs < lines[0].timeMs) return -1
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}

package com.lyricsync.app.domain

object LrcParser {
    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val OFFSET_TAG_REGEX = Regex("""^\s*\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val INLINE_WORD_TIMESTAMP_REGEX = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>\s*""")
    private val MULTI_SPACE_REGEX = Regex("""\s{2,}""")
    private val NOISE_PARENS_REGEX = Regex(
        """\s*\([^)]*\b(?:feat\.?|ft\.?|with|remaster(?:ed)?|live|version|edit|mix|mono|stereo|deluxe|bonus|explicit|radio|acoustic|instrumental|from\s+)[^)]*\)""",
        RegexOption.IGNORE_CASE
    )
    private val NOISE_BRACKETS_REGEX = Regex("""\s*\[[^\]]*]""")
    private val NOISE_DASH_SUFFIX_REGEX = Regex(
        """\s+-\s+.*\b(?:remaster(?:ed)?|version|edit|mix|live|mono|stereo|deluxe|bonus|explicit|radio|acoustic|from\s+).*$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parses LRC formatted synced lyrics ([mm:ss.xx] text) into sorted [LyricLine] list,
     * applying optional [offset:±ms] tags, stripping inline karaoke word timestamps,
     * and merging lines sharing the exact same timestamp.
     */
    fun parseSyncedLyrics(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrBlank()) return emptyList()
        val result = mutableListOf<LyricLine>()
        var globalOffsetMs = 0L

        for (rawLine in lrcContent.lineSequence()) {
            val offsetMatch = OFFSET_TAG_REGEX.find(rawLine)
            if (offsetMatch != null) {
                globalOffsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
                continue
            }

            val matches = TIMESTAMP_REGEX.findAll(rawLine).toList()
            if (matches.isEmpty()) continue

            val textStart = matches.last().range.last + 1
            val rawText = rawLine.substring(textStart)
                .replace(INLINE_WORD_TIMESTAMP_REGEX, " ")
            val lyricText = MULTI_SPACE_REGEX.replace(rawText, " ").trim()
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
                val totalTimeMs = (minutes * 60_000L + seconds * 1_000L + millis + globalOffsetMs)
                    .coerceAtLeast(0L)
                result.add(LyricLine(timeMs = totalTimeMs, originalText = lyricText))
            }
        }

        return result
            .sortedBy { it.timeMs }
            .groupBy { it.timeMs }
            .map { (timeMs, group) ->
                val combinedText = group
                    .map { it.originalText }
                    .distinct()
                    .joinToString(" • ")
                LyricLine(timeMs = timeMs, originalText = combinedText)
            }
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
     * Returns the index of the currently active synced lyric line for [positionMs]
     * shifted by [syncOffsetMs] in O(log N) time.
     */
    fun findActiveLineIndex(
        lines: List<LyricLine>,
        positionMs: Long,
        isSynced: Boolean,
        syncOffsetMs: Long = 0L
    ): Int {
        if (!isSynced || lines.isEmpty() || positionMs < 0L) return -1
        val effectivePos = (positionMs + syncOffsetMs).coerceAtLeast(0L)
        if (effectivePos < lines[0].timeMs) return -1
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= effectivePos) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    /**
     * Strips streaming edition/feature/explicit tags from track titles for LRCLIB fallback search
     * while preserving genuine parentheses that belong to the song title (e.g. "(Don't Fear) The Reaper").
     */
    fun cleanTrackTitleForSearch(rawTrack: String): String {
        val cleaned = rawTrack
            .replace(NOISE_BRACKETS_REGEX, "")
            .replace(NOISE_PARENS_REGEX, "")
            .replace(NOISE_DASH_SUFFIX_REGEX, "")
            .trim()
        return cleaned.ifBlank { rawTrack.trim() }
    }

    /**
     * Extracts the primary artist name from multi-artist Spotify metadata strings.
     */
    fun cleanArtistForSearch(rawArtist: String): String {
        val primary = rawArtist
            .substringBefore(",")
            .substringBefore(" & ")
            .substringBefore(" feat.")
            .substringBefore(" ft.")
            .trim()
        return primary.ifBlank { rawArtist.trim() }
    }
}

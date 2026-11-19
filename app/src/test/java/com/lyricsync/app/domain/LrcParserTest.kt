package com.lyricsync.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parseSyncedLyrics parses timestamps, ignores metadata tags, and sorts lines`() {
        val raw = """
            [ar:Daft Punk]
            [ti:Instant Crush]
            [00:12.50] First line
            [00:05.00] Intro line
            [01:02.345] Bridge line
            [00:20.00][00:40.00] Chorus line
        """.trimIndent()

        val parsed = LrcParser.parseSyncedLyrics(raw)
        assertEquals(5, parsed.size)
        assertEquals(5_000L, parsed[0].timeMs)
        assertEquals("Intro line", parsed[0].originalText)
        assertEquals(12_500L, parsed[1].timeMs)
        assertEquals(20_000L, parsed[2].timeMs)
        assertEquals(40_000L, parsed[3].timeMs)
        assertEquals(62_345L, parsed[4].timeMs)
    }

    @Test
    fun `parsePlainLyrics splits non-empty lines when syncedLyrics is unavailable`() {
        val plain = """
            Line A
            
            Line B
        """.trimIndent()

        val parsed = LrcParser.parsePlainLyrics(plain)
        assertEquals(2, parsed.size)
        assertEquals("Line A", parsed[0].originalText)
        assertEquals("Line B", parsed[1].originalText)
        assertEquals(-1, LrcParser.findActiveLineIndex(parsed, 1500L, isSynced = false))
        assertTrue(LrcParser.parsePlainLyrics(null).isEmpty())
    }

    @Test
    fun `findActiveLineIndex returns matching line for playback position`() {
        val lines = listOf(
            LyricLine(1000L, "One"),
            LyricLine(5000L, "Two"),
            LyricLine(9000L, "Three")
        )
        assertEquals(-1, LrcParser.findActiveLineIndex(lines, 500L, isSynced = true))
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 1000L, isSynced = true))
        assertEquals(1, LrcParser.findActiveLineIndex(lines, 7500L, isSynced = true))
        assertEquals(2, LrcParser.findActiveLineIndex(lines, 15000L, isSynced = true))
    }
}

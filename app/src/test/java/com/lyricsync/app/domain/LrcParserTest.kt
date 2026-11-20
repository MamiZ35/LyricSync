package com.lyricsync.app.domain

import com.lyricsync.app.data.remote.CloudTranslator
import com.lyricsync.app.data.remote.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `normalizeLyricText expands song contractions and slang`() {
        val raw = "I ain't gonna fallin' 'cause I wanna know"
        val normalized = CloudTranslator.normalizeLyricText(raw)
        assertEquals("I am not going to falling because I want to know", normalized)
    }

    @Test
    fun `isNewerVersion compares semantic version segments accurately`() {
        assertTrue(UpdateChecker.isNewerVersion("v1.2.10", "1.2.9"))
        assertTrue(UpdateChecker.isNewerVersion("v1.3.0", "1.2.99"))
        assertFalse(UpdateChecker.isNewerVersion("v1.2.5", "1.2.5"))
        assertFalse(UpdateChecker.isNewerVersion("v1.2.4", "1.2.5"))
    }

    @Test
    fun `findActiveLineIndex handles empty lists, negative positions, and duplicate timestamps`() {
        assertEquals(-1, LrcParser.findActiveLineIndex(emptyList(), 1000L, isSynced = true))
        val dupLines = listOf(
            LyricLine(2000L, "First"),
            LyricLine(5000L, "Repeat A"),
            LyricLine(5000L, "Repeat B"),
            LyricLine(8000L, "End")
        )
        assertEquals(-1, LrcParser.findActiveLineIndex(dupLines, -500L, isSynced = true))
        assertEquals(-1, LrcParser.findActiveLineIndex(dupLines, 1999L, isSynced = true))
        assertEquals(2, LrcParser.findActiveLineIndex(dupLines, 5000L, isSynced = true))
        assertEquals(2, LrcParser.findActiveLineIndex(dupLines, 7999L, isSynced = true))
        assertEquals(3, LrcParser.findActiveLineIndex(dupLines, 8000L, isSynced = true))
    }

    @Test
    fun `isTrustedGithubUrl accepts only GitHub HTTPS hosts`() {
        assertTrue(UpdateChecker.isTrustedGithubUrl(UpdateChecker.LATEST_APK_URL))
        assertTrue(UpdateChecker.isTrustedGithubUrl("https://objects.githubusercontent.com/github-production-release-asset/app.apk"))
        assertTrue(UpdateChecker.isTrustedGithubUrl("https://release-assets.githubusercontent.com/test.apk"))
        assertFalse(UpdateChecker.isTrustedGithubUrl("http://github.com/MamiZ35/LyricSync/releases/latest/download/LyricSync.apk"))
        assertFalse(UpdateChecker.isTrustedGithubUrl("https://evilgithub.com/LyricSync.apk"))
        assertFalse(UpdateChecker.isTrustedGithubUrl("https://example.com/LyricSync.apk"))
        assertFalse(UpdateChecker.isTrustedGithubUrl(""))
    }
}

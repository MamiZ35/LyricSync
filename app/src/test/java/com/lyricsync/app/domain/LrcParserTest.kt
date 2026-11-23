package com.lyricsync.app.domain

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

    @Test
    fun `parseSyncedLyrics applies global offset tag and strips inline word timestamps`() {
        val raw = """
            [offset:500]
            [00:10.00] <00:10.20>Hello <00:10.60>world
            [00:00.20] Intro
        """.trimIndent()

        val parsed = LrcParser.parseSyncedLyrics(raw)
        assertEquals(2, parsed.size)
        assertEquals(700L, parsed[0].timeMs)
        assertEquals("Intro", parsed[0].originalText)
        assertEquals(10_500L, parsed[1].timeMs)
        assertEquals("Hello world", parsed[1].originalText)
    }

    @Test
    fun `parseSyncedLyrics merges duplicate timestamp lines cleanly`() {
        val raw = """
            [00:10.00] Line A
            [00:10.00] Line B
            [00:10.00] Line A
        """.trimIndent()

        val parsed = LrcParser.parseSyncedLyrics(raw)
        assertEquals(1, parsed.size)
        assertEquals(10_000L, parsed[0].timeMs)
        assertEquals("Line A • Line B", parsed[0].originalText)
    }

    @Test
    fun `findActiveLineIndex applies positive and negative syncOffsetMs`() {
        val lines = listOf(
            LyricLine(1000L, "One"),
            LyricLine(5000L, "Two"),
            LyricLine(9000L, "Three")
        )
        assertEquals(1, LrcParser.findActiveLineIndex(lines, 4600L, isSynced = true, syncOffsetMs = 500L))
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 5200L, isSynced = true, syncOffsetMs = -500L))
        assertEquals(-1, LrcParser.findActiveLineIndex(lines, 1200L, isSynced = true, syncOffsetMs = -500L))
    }

    @Test
    fun `cleanTrackTitleForSearch and cleanArtistForSearch strip noise while preserving real title parentheses`() {
        assertEquals("Starboy", LrcParser.cleanTrackTitleForSearch("Starboy [Explicit]"))
        assertEquals("Bohemian Rhapsody", LrcParser.cleanTrackTitleForSearch("Bohemian Rhapsody - Remastered 2011"))
        assertEquals("Love The Way You Lie (Part II)", LrcParser.cleanTrackTitleForSearch("Love The Way You Lie (Part II) (feat. Eminem)"))
        assertEquals("(Don't Fear) The Reaper", LrcParser.cleanTrackTitleForSearch("(Don't Fear) The Reaper"))
        assertEquals("Daft Punk", LrcParser.cleanArtistForSearch("Daft Punk, Julian Casablancas"))
    }

    @Test
    fun `recoverNumberedTranslations recovers lines when translator merges or splits newlines`() {
        val raw = """
            1. Merhaba dünya
            2. Nasılsın
            bugün?
            3. Görüşürüz
        """.trimIndent()

        val recovered = CloudTranslator.recoverNumberedTranslations(raw, expectedCount = 3)
        assertEquals(listOf("Merhaba dünya", "Nasılsın bugün?", "Görüşürüz"), recovered)
    }

    @Test
    fun `gson round-trips LyricLine list using TypeToken getParameterized`() {
        val original = listOf(
            LyricLine(timeMs = 1_200L, originalText = "Hello", translatedText = "Merhaba"),
            LyricLine(timeMs = 4_500L, originalText = "World", translatedText = "Dünya")
        )
        val gson = Gson()
        val listType = TypeToken.getParameterized(List::class.java, LyricLine::class.java).type
        val json = gson.toJson(original, listType)
        val decoded: List<LyricLine> = gson.fromJson(json, listType)
        assertEquals(original, decoded)
    }
}

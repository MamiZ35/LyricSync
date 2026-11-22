package com.lyricsync.app.data.remote

import com.lyricsync.app.domain.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudTranslatorTest {

    @Test
    fun `normalizeLyricText expands slang and preserves non-Latin lines`() {
        assertEquals(
            "I am not going to leave you because I want to stay",
            CloudTranslator.normalizeLyricText("  I ain't gonna leave you 'cause I wanna stay ")
        )
        assertEquals(
            "until the morning",
            CloudTranslator.normalizeLyricText("'til the morning")
        )
        assertEquals(
            "君の名は。愛してる",
            CloudTranslator.normalizeLyricText("  君の名は。愛してる ")
        )
        assertEquals("", CloudTranslator.normalizeLyricText("   "))
    }

    @Test
    fun `parseResponse handles nested arrays, primitive arrays, and invalid payloads`() {
        assertEquals(
            Pair("Merhaba dünya", "en"),
            CloudTranslator.parseResponse("""[["Merhaba dünya","en"]]""")
        )
        assertEquals(
            Pair("Bonjour", "en"),
            CloudTranslator.parseResponse("""[["Bonjour"]]""")
        )
        assertEquals(
            Pair("Hola", "en"),
            CloudTranslator.parseResponse("""["Hola"]""")
        )
        assertNull(CloudTranslator.parseResponse("[]"))
        assertNull(CloudTranslator.parseResponse("""{"error":"bad"}"""))
        assertNull(CloudTranslator.parseResponse("not-valid-json"))
    }

    @Test
    fun `chunkLinesByCharLimit splits lines by char budget without dropping items`() {
        val lines = listOf(
            LyricLine(1000L, "Alpha line"),
            LyricLine(2000L, "Beta line"),
            LyricLine(3000L, "Gamma line"),
            LyricLine(4000L, "Delta line")
        )
        val chunks = CloudTranslator.chunkLinesByCharLimit(lines, maxChars = 40)
        assertEquals(2, chunks.size)
        assertEquals(listOf("Alpha line", "Beta line"), chunks[0].map { it.originalText })
        assertEquals(listOf("Gamma line", "Delta line"), chunks[1].map { it.originalText })
    }

    @Test
    fun `recoverNumberedTranslations returns null when too few markers exist`() {
        val incomplete = "1. İlk satır\n2. İkinci satır"
        assertNull(CloudTranslator.recoverNumberedTranslations(incomplete, expectedCount = 5))
        assertNull(CloudTranslator.recoverNumberedTranslations("Numarasız metin", expectedCount = 2))
    }
}

package com.lyricsync.app.data.remote

import com.google.gson.JsonParser
import com.lyricsync.app.domain.LyricLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

object CloudTranslator {

    private val client: OkHttpClient
        get() = NetworkClient.baseOkHttpClient

    private val SLANG_REPLACEMENTS: List<Pair<Regex, String>> = listOf(
        Regex("\\b[Ii]\\s+ain't\\b") to "I am not",
        Regex("\\b(you|we|they)\\s+ain't\\b", RegexOption.IGNORE_CASE) to "$1 are not",
        Regex("\\b(he|she|it)\\s+ain't\\b", RegexOption.IGNORE_CASE) to "$1 is not",
        Regex("\\bain't\\b", RegexOption.IGNORE_CASE) to "is not",
        Regex("\\bgonna\\b", RegexOption.IGNORE_CASE) to "going to",
        Regex("\\bwanna\\b", RegexOption.IGNORE_CASE) to "want to",
        Regex("\\bgotta\\b", RegexOption.IGNORE_CASE) to "got to",
        Regex("\\btryna\\b", RegexOption.IGNORE_CASE) to "trying to",
        Regex("\\blemme\\b", RegexOption.IGNORE_CASE) to "let me",
        Regex("\\bgimme\\b", RegexOption.IGNORE_CASE) to "give me",
        Regex("\\boutta\\b", RegexOption.IGNORE_CASE) to "out of",
        Regex("\\bkinda\\b", RegexOption.IGNORE_CASE) to "kind of",
        Regex("\\bc'mon\\b", RegexOption.IGNORE_CASE) to "come on",
        Regex("\\bdunno\\b", RegexOption.IGNORE_CASE) to "do not know",
        Regex("(?<!\\w)(?:'cause|'cuz|cuz)\\b", RegexOption.IGNORE_CASE) to "because",
        Regex("(?<!\\w)'til\\b", RegexOption.IGNORE_CASE) to "until",
        Regex("\\b([a-zA-Z]{3,})in'(?!\\w)") to "$1ing"
    )

    private val NUMBER_PREFIX_REGEX = Regex("""^\d+[.)]\s*""")
    private val NUMBER_MARKER_REGEX = Regex("""(?:^|\n)\s*(\d{1,4})[.)]\s*""")

    /**
     * Expands informal song contractions/slang to improve NMT grammar & meaning
     * without altering the displayed [LyricLine.originalText].
     */
    fun normalizeLyricText(line: String): String {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.none { it in 'A'..'Z' || it in 'a'..'z' }) {
            return trimmed
        }
        var normalized = trimmed
        for ((pattern, replacement) in SLANG_REPLACEMENTS) {
            normalized = pattern.replace(normalized, replacement)
        }
        return normalized
    }

    /**
     * Translates the song in full-context batches so multi-line clauses, idioms,
     * and verb tenses stay coherent across lines. Returns (detectedSourceLang, lines) or null on failure.
     */
    suspend fun translateFullSongContextAware(
        lines: List<LyricLine>,
        targetLang: String
    ): Pair<String, List<LyricLine>>? = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext null

        val prepared = lines.map { line -> line to normalizeLyricText(line.originalText) }
        val chunks = chunkPreparedLinesByCharLimit(prepared, maxChars = 3800)
        val resultLines = ArrayList<LyricLine>(lines.size)
        var detectedSourceLang = ""

        for (chunk in chunks) {
            val chunkResult = translateChunk(chunk, targetLang) ?: return@withContext null
            if (detectedSourceLang.isBlank()) {
                detectedSourceLang = chunkResult.first
            }
            resultLines.addAll(chunkResult.second)
        }

        Pair(detectedSourceLang.ifBlank { "en" }, resultLines)
    }

    private fun translateChunk(
        chunk: List<Pair<LyricLine, String>>,
        targetLang: String
    ): Pair<String, List<LyricLine>>? {
        val plainJoined = chunk.joinToString("\n") { it.second }
        val firstAttempt = executeRequest(plainJoined, targetLang) ?: return null

        val detectedLang = firstAttempt.second
        if (detectedLang.equals(targetLang, ignoreCase = true)) {
            return Pair(detectedLang, chunk.map { it.first })
        }

        val translatedLines = firstAttempt.first.split("\n")
        if (translatedLines.size == chunk.size) {
            val mapped = chunk.mapIndexed { index, (line, _) ->
                line.copy(translatedText = translatedLines[index].trim())
            }
            return Pair(detectedLang, mapped)
        }

        // Fallback alignment: retry with explicit line numbers if the engine merged/split a newline
        val numberedJoined = chunk.mapIndexed { idx, (_, norm) -> "${idx + 1}. $norm" }.joinToString("\n")
        val numberedAttempt = executeRequest(numberedJoined, targetLang) ?: return null
        val numberedLines = numberedAttempt.first.split("\n")
        if (numberedLines.size == chunk.size) {
            val mapped = chunk.mapIndexed { index, (line, _) ->
                val cleaned = numberedLines[index].trim().replaceFirst(NUMBER_PREFIX_REGEX, "")
                line.copy(translatedText = cleaned)
            }
            return Pair(numberedAttempt.second, mapped)
        }

        val recovered = recoverNumberedTranslations(numberedAttempt.first, chunk.size) ?: return null
        val mappedRecovered = chunk.mapIndexed { index, (line, _) ->
            line.copy(translatedText = recovered[index].ifBlank { line.originalText })
        }
        return Pair(numberedAttempt.second, mappedRecovered)
    }

    internal fun recoverNumberedTranslations(rawTranslated: String, expectedCount: Int): List<String>? {
        if (expectedCount <= 0) return emptyList()
        val matches = NUMBER_MARKER_REGEX.findAll(rawTranslated).toList()
        if (matches.isEmpty()) return null
        val byNumber = HashMap<Int, String>(expectedCount)
        for (i in matches.indices) {
            val num = matches[i].groupValues[1].toIntOrNull() ?: continue
            val contentStart = matches[i].range.last + 1
            val contentEnd = if (i + 1 < matches.size) matches[i + 1].range.first else rawTranslated.length
            if (contentStart <= contentEnd) {
                val text = rawTranslated.substring(contentStart, contentEnd).replace("\n", " ").trim()
                if (num in 1..expectedCount && text.isNotEmpty()) {
                    byNumber[num] = text
                }
            }
        }
        val minRequired = maxOf(1, (expectedCount * 4) / 5)
        if (byNumber.size < minRequired) return null
        return (1..expectedCount).map { idx -> byNumber[idx].orEmpty() }
    }

    private fun executeRequest(textBlock: String, targetLang: String): Pair<String, String>? {
        return try {
            val url = "https://clients5.google.com/translate_a/t".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("client", "dict-chrome-ex")
                ?.addQueryParameter("sl", "auto")
                ?.addQueryParameter("tl", targetLang.trim())
                ?.build()
                ?: return null
            val formBody = FormBody.Builder()
                .add("q", textBlock)
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                parseResponse(body)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }

    internal fun parseResponse(body: String): Pair<String, String>? {
        val root = runCatching { JsonParser.parseString(body) }.getOrNull() ?: return null
        if (!root.isJsonArray) return null
        val outer = root.asJsonArray
        if (outer.size() == 0) return null

        val first = outer[0]
        return when {
            first.isJsonArray -> {
                val inner = first.asJsonArray
                val text = if (inner.size() > 0 && inner[0].isJsonPrimitive) inner[0].asString else return null
                val lang = if (inner.size() > 1 && inner[1].isJsonPrimitive) inner[1].asString else "en"
                Pair(text, lang)
            }
            first.isJsonPrimitive -> Pair(first.asString, "en")
            else -> null
        }
    }

    internal fun chunkLinesByCharLimit(lines: List<LyricLine>, maxChars: Int): List<List<LyricLine>> {
        val prepared = lines.map { it to normalizeLyricText(it.originalText) }
        return chunkPreparedLinesByCharLimit(prepared, maxChars).map { chunk -> chunk.map { it.first } }
    }

    private fun chunkPreparedLinesByCharLimit(
        lines: List<Pair<LyricLine, String>>,
        maxChars: Int
    ): List<List<Pair<LyricLine, String>>> {
        val chunks = mutableListOf<List<Pair<LyricLine, String>>>()
        var current = mutableListOf<Pair<LyricLine, String>>()
        var charCount = 0

        for (pair in lines) {
            val len = pair.second.length + 8
            if (current.isNotEmpty() && charCount + len > maxChars) {
                chunks.add(current)
                current = mutableListOf()
                charCount = 0
            }
            current.add(pair)
            charCount += len
        }
        if (current.isNotEmpty()) {
            chunks.add(current)
        }
        return chunks
    }
}

package com.lyricsync.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.lyricsync.app.data.local.CachedLyricsEntity
import com.lyricsync.app.data.local.CachedSongSummary
import com.lyricsync.app.data.local.LyricsDatabase
import com.lyricsync.app.data.remote.CloudTranslator
import com.lyricsync.app.data.remote.LrcLibApi
import com.lyricsync.app.data.remote.LrcLibResponse
import com.lyricsync.app.domain.LrcParser
import com.lyricsync.app.domain.LyricLine
import com.lyricsync.app.domain.LyricsState
import com.lyricsync.app.domain.MediaTrackState
import com.lyricsync.app.domain.TranslationEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.abs

class LyricsRepository private constructor(context: Context) {

    private val dao = LyricsDatabase.getInstance(context.applicationContext).lyricsDao()
    private val api = LrcLibApi.instance
    private val gson = Gson()
    private val lineListType = TypeToken.getParameterized(List::class.java, LyricLine::class.java).type
    private val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val jobLock = Any()
    private var activeLoadJob: Job? = null

    companion object {
        @Volatile
        private var INSTANCE: LyricsRepository? = null

        fun getInstance(context: Context): LyricsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LyricsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun requestLoadAndTranslate(
        track: MediaTrackState,
        targetLang: String,
        preFetchedResponse: LrcLibResponse? = null,
        forceRefresh: Boolean = false
    ): Job {
        synchronized(jobLock) {
            activeLoadJob?.cancel()
            val job = repositoryScope.launch {
                loadAndTranslateLyrics(
                    track = track,
                    targetLang = targetLang,
                    preFetchedResponse = preFetchedResponse,
                    forceRefresh = forceRefresh
                )
            }
            activeLoadJob = job
            return job
        }
    }

    suspend fun loadAndTranslateLyrics(
        track: MediaTrackState,
        targetLang: String,
        preFetchedResponse: LrcLibResponse? = null,
        forceRefresh: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (!track.hasActiveTrack) return@withContext

        val trackId = track.trackId
        val engine = MediaStateBus.translationEngine.value
        val cacheKey = "${engine.name}|$trackId|$targetLang"

        // 1. Check Room Offline Cache first unless user explicitly picked a manual result or forced refresh
        if (preFetchedResponse == null && !forceRefresh) {
            val cached = safeCatching { dao.getByKey(cacheKey) }.getOrNull()
            if (cached != null) {
                val cachedLines: List<LyricLine> = safeCatching {
                    gson.fromJson<List<LyricLine>>(cached.linesJson, lineListType)
                }.getOrDefault(emptyList())

                if (cachedLines.isNotEmpty()) {
                    safeCatching { dao.touch(cacheKey) }
                    publishIfCurrentTrack(
                        trackId,
                        LyricsState(
                            trackId = trackId,
                            lines = cachedLines,
                            isSynced = cached.isSynced,
                            sourceLang = cached.sourceLang,
                            targetLang = cached.targetLang,
                            syncOffsetMs = cached.syncOffsetMs,
                            isLoading = false,
                            isFromCache = true
                        )
                    )
                    return@withContext
                }
            }
        }

        publishIfCurrentTrack(
            trackId,
            LyricsState(
                trackId = trackId,
                targetLang = targetLang,
                isLoading = true,
                statusMessage = "LRCLIB üzerinden şarkı sözleri çekiliyor..."
            )
        )

        // 2. Fetch from LRCLIB API (/api/get with fallback to /api/search)
        val durationSec = if (track.durationMs > 0) (track.durationMs / 1000L).toInt() else null
        val response = preFetchedResponse ?: fetchFromLrcLib(track.artistName, track.trackName, durationSec)
        currentCoroutineContext().ensureActive()

        if (response == null) {
            if (!forceRefresh) {
                val crossEngineCached = tryLoadCrossEngineCache(track, targetLang)
                if (crossEngineCached != null) {
                    publishIfCurrentTrack(trackId, crossEngineCached)
                    return@withContext
                }
            }
            publishIfCurrentTrack(
                trackId,
                LyricsState(
                    trackId = trackId,
                    targetLang = targetLang,
                    isLoading = false,
                    errorMessage = "'${track.trackName}' için şarkı sözü bulunamadı."
                )
            )
            return@withContext
        }

        // 3. Parse syncedLyrics (.lrc), or fallback to plainLyrics
        val syncedLines = LrcParser.parseSyncedLyrics(response.syncedLyrics)
        val isSynced = syncedLines.isNotEmpty()
        val parsedLines = if (isSynced) {
            syncedLines
        } else {
            LrcParser.parsePlainLyrics(response.plainLyrics)
        }

        if (parsedLines.isEmpty()) {
            publishIfCurrentTrack(
                trackId,
                LyricsState(
                    trackId = trackId,
                    targetLang = targetLang,
                    isLoading = false,
                    errorMessage = if (response.instrumental == true) {
                        "Bu parça enstrümantal (söz içermiyor)."
                    } else {
                        "Bu şarkı için söz verisi boş."
                    }
                )
            )
            return@withContext
        }

        val preservedOffset = safeCatching { dao.getByKey(cacheKey)?.syncOffsetMs }.getOrNull()
            ?: if (MediaStateBus.lyricsState.value.trackId == trackId) {
                MediaStateBus.lyricsState.value.syncOffsetMs
            } else {
                0L
            }

        // Emit original lyrics immediately so synced scrolling starts without waiting for translation
        publishIfCurrentTrack(
            trackId,
            LyricsState(
                trackId = trackId,
                lines = parsedLines,
                isSynced = isSynced,
                targetLang = targetLang,
                syncOffsetMs = preservedOffset,
                isLoading = false,
                statusMessage = if (engine == TranslationEngine.CLOUD_CONTEXT) {
                    "Şarkı sözleri bağlamsal bulut motoruyla çevriliyor..."
                } else {
                    "Dil algılanıyor..."
                }
            )
        )

        // 4A. Primary Path: Context-Aware Full-Song Cloud Translation (Option C)
        if (engine == TranslationEngine.CLOUD_CONTEXT) {
            val cloudResult = CloudTranslator.translateFullSongContextAware(parsedLines, targetLang)
            currentCoroutineContext().ensureActive()
            if (cloudResult != null) {
                val detectedLang = TranslateLanguage.fromLanguageTag(cloudResult.first)
                    ?: cloudResult.first.lowercase()
                val translatedLines = cloudResult.second
                saveToCache(cacheKey, track, detectedLang, targetLang, isSynced, translatedLines)
                publishIfCurrentTrack(
                    trackId,
                    LyricsState(
                        trackId = trackId,
                        lines = translatedLines,
                        isSynced = isSynced,
                        sourceLang = detectedLang,
                        targetLang = targetLang,
                        syncOffsetMs = preservedOffset,
                        isLoading = false,
                        isDownloadingModel = false,
                        isFromCache = false,
                        statusMessage = null
                    )
                )
                return@withContext
            }
            if (!forceRefresh) {
                val crossEngineCached = tryLoadCrossEngineCache(track, targetLang)
                if (crossEngineCached != null) {
                    publishIfCurrentTrack(trackId, crossEngineCached)
                    return@withContext
                }
            }
            // Fall through to offline ML Kit if cloud is unreachable
            publishIfCurrentTrack(
                trackId,
                MediaStateBus.lyricsState.value.copy(
                    statusMessage = "Bulut erişilemedi, cihaz içi ML Kit yedeğine geçiliyor..."
                )
            )
        }

        // 4B. Offline Path / Fallback: Identify source language on-device via Google ML Kit Language ID
        val sampleText = parsedLines.take(15).joinToString("\n") { it.originalText }
        val langIdentifier = LanguageIdentification.getClient()
        val detectedTag = try {
            safeCatching { langIdentifier.identifyLanguage(sampleText).await() }.getOrDefault("en")
        } finally {
            runCatching { langIdentifier.close() }
        }
        currentCoroutineContext().ensureActive()

        val sourceLang = TranslateLanguage.fromLanguageTag(
            if (detectedTag == "und" || detectedTag.isBlank()) "en" else detectedTag
        ) ?: TranslateLanguage.ENGLISH

        if (sourceLang == targetLang) {
            val finalState = LyricsState(
                trackId = trackId,
                lines = parsedLines,
                isSynced = isSynced,
                sourceLang = sourceLang,
                targetLang = targetLang,
                syncOffsetMs = preservedOffset,
                isLoading = false,
                statusMessage = null
            )
            publishIfCurrentTrack(trackId, finalState)
            saveToCache(cacheKey, track, sourceLang, targetLang, isSynced, parsedLines)
            return@withContext
        }

        // 5. Translate on-device via Google ML Kit Translate
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()
        val translator = Translation.getClient(options)

        try {
            val modelManager = RemoteModelManager.getInstance()
            val srcModel = TranslateRemoteModel.Builder(sourceLang).build()
            val tgtModel = TranslateRemoteModel.Builder(targetLang).build()
            val isSrcReady = safeCatching { modelManager.isModelDownloaded(srcModel).await() }.getOrDefault(false)
            val isTgtReady = safeCatching { modelManager.isModelDownloaded(tgtModel).await() }.getOrDefault(false)

            if (!isSrcReady || !isTgtReady) {
                val wifiNote = if (MediaStateBus.wifiOnlyDownload.value) " (Wi-Fi bekleniyor)" else ""
                publishIfCurrentTrack(
                    trackId,
                    MediaStateBus.lyricsState.value.copy(
                        sourceLang = sourceLang,
                        isDownloadingModel = true,
                        statusMessage = "Dil modeli (${sourceLang.uppercase()} → ${targetLang.uppercase()}) indiriliyor$wifiNote..."
                    )
                )
            } else {
                publishIfCurrentTrack(
                    trackId,
                    MediaStateBus.lyricsState.value.copy(
                        sourceLang = sourceLang,
                        statusMessage = "Sözler cihaz içinde çevriliyor (${sourceLang.uppercase()} → ${targetLang.uppercase()})..."
                    )
                )
            }

            val conditions = DownloadConditions.Builder().apply {
                if (MediaStateBus.wifiOnlyDownload.value) requireWifi()
            }.build()
            translator.downloadModelIfNeeded(conditions).await()
            currentCoroutineContext().ensureActive()

            publishIfCurrentTrack(
                trackId,
                MediaStateBus.lyricsState.value.copy(
                    isDownloadingModel = false,
                    statusMessage = "Şarkı sözleri çevriliyor..."
                )
            )

            // Deduplicate identical lines (e.g. repeated chorus) for faster translation
            val lineTranslationCache = HashMap<String, String>()
            val translatedLines = parsedLines.map { line ->
                currentCoroutineContext().ensureActive()
                val clean = line.originalText.trim()
                val normalized = CloudTranslator.normalizeLyricText(clean)
                val translated = lineTranslationCache.getOrPut(normalized) {
                    safeCatching { translator.translate(normalized).await() }.getOrDefault(clean)
                }
                line.copy(translatedText = translated)
            }

            // 6. Save translated lyrics under ON_DEVICE_MLKIT key so offline fallback does not poison cloud cache
            val mlKitCacheKey = "${TranslationEngine.ON_DEVICE_MLKIT.name}|$trackId|$targetLang"
            saveToCache(mlKitCacheKey, track, sourceLang, targetLang, isSynced, translatedLines)

            publishIfCurrentTrack(
                trackId,
                LyricsState(
                    trackId = trackId,
                    lines = translatedLines,
                    isSynced = isSynced,
                    sourceLang = sourceLang,
                    targetLang = targetLang,
                    syncOffsetMs = preservedOffset,
                    isLoading = false,
                    isDownloadingModel = false,
                    isFromCache = false,
                    statusMessage = null
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            publishIfCurrentTrack(
                trackId,
                MediaStateBus.lyricsState.value.copy(
                    isDownloadingModel = false,
                    statusMessage = null,
                    errorMessage = "Çeviri modeli hatası: ${e.localizedMessage ?: "Bilinmeyen hata"}"
                )
            )
        } finally {
            translator.close()
        }
    }

    suspend fun searchLyricsManual(query: String): List<LrcLibResponse> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        safeCatching {
            api.searchLyrics(query = trimmed)
                .filter { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
                .sortedByDescending { !it.syncedLyrics.isNullOrBlank() }
        }.getOrDefault(emptyList())
    }

    suspend fun selectManualLyrics(item: LrcLibResponse, targetLang: String) {
        val manualTrack = MediaTrackState(
            trackName = item.trackName.orEmpty().ifBlank { "Bilinmeyen Şarkı" },
            artistName = item.artistName.orEmpty().ifBlank { "Bilinmeyen Sanatçı" },
            durationMs = ((item.duration ?: 0.0) * 1000.0).toLong().coerceAtLeast(0L),
            isPlaying = false,
            isManualOverride = true
        )
        MediaStateBus.updateTrackState(manualTrack)
        requestLoadAndTranslate(manualTrack, targetLang, preFetchedResponse = item).join()
    }

    suspend fun getCachedSongCount(): Int = withContext(Dispatchers.IO) {
        safeCatching { dao.count() }.getOrDefault(0)
    }

    suspend fun getLibrarySummaries(): List<CachedSongSummary> = withContext(Dispatchers.IO) {
        safeCatching { dao.getRecentSummaries(100) }.getOrDefault(emptyList())
    }

    suspend fun toggleFavorite(cacheKey: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        safeCatching { dao.setFavorite(cacheKey, isFavorite) }
    }

    suspend fun loadCachedSongByKey(cacheKey: String) = withContext(Dispatchers.IO) {
        synchronized(jobLock) {
            activeLoadJob?.cancel()
            activeLoadJob = null
        }
        val entity = safeCatching { dao.getByKey(cacheKey) }.getOrNull() ?: return@withContext
        val cachedLines: List<LyricLine> = safeCatching {
            gson.fromJson<List<LyricLine>>(entity.linesJson, lineListType)
        }.getOrDefault(emptyList())
        if (cachedLines.isEmpty()) return@withContext

        safeCatching { dao.touch(cacheKey) }
        val manualTrack = MediaTrackState(
            trackName = entity.trackName,
            artistName = entity.artistName,
            isPlaying = false,
            isManualOverride = true
        )
        MediaStateBus.updateTrackState(manualTrack)
        MediaStateBus.updateLyricsState(
            LyricsState(
                trackId = manualTrack.trackId,
                lines = cachedLines,
                isSynced = entity.isSynced,
                sourceLang = entity.sourceLang,
                targetLang = entity.targetLang,
                syncOffsetMs = entity.syncOffsetMs,
                isLoading = false,
                isFromCache = true
            )
        )
    }

    suspend fun updateSyncOffset(track: MediaTrackState, offsetMs: Long) = withContext(Dispatchers.IO) {
        val clamped = offsetMs.coerceIn(-15_000L, 15_000L)
        MediaStateBus.setSyncOffsetMs(clamped)
        if (track.hasActiveTrack) {
            safeCatching {
                dao.updateSyncOffsetForTrack(
                    artistName = track.artistName.trim(),
                    trackName = track.trackName.trim(),
                    offsetMs = clamped
                )
            }
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        safeCatching { dao.clearNonFavorites() }
    }

    suspend fun getDownloadedModels(): List<String> = withContext(Dispatchers.IO) {
        safeCatching {
            RemoteModelManager.getInstance()
                .getDownloadedModels(TranslateRemoteModel::class.java)
                .await()
                .map { it.language }
                .sorted()
        }.getOrDefault(emptyList())
    }

    suspend fun deleteModel(langCode: String) = withContext(Dispatchers.IO) {
        safeCatching {
            val model = TranslateRemoteModel.Builder(langCode).build()
            RemoteModelManager.getInstance().deleteDownloadedModel(model).await()
        }
    }

    private fun publishIfCurrentTrack(trackId: String, state: LyricsState) {
        if (MediaStateBus.trackState.value.trackId == trackId) {
            MediaStateBus.updateLyricsState(state)
        }
    }

    private suspend fun fetchFromLrcLib(
        artistName: String,
        trackName: String,
        durationSec: Int?
    ): LrcLibResponse? {
        // Primary: exact /api/get endpoint with artist_name, track_name, duration
        if (artistName.isNotBlank()) {
            safeCatching {
                val direct = api.getLyrics(
                    artistName = artistName,
                    trackName = trackName,
                    durationSeconds = durationSec
                )
                if (!direct.syncedLyrics.isNullOrBlank() || !direct.plainLyrics.isNullOrBlank()) {
                    return direct
                }
            }
        }

        // Fallback: /api/search if Spotify duration differs slightly or track has remaster/explicit/feat suffix
        return safeCatching {
            val cleanTrack = LrcParser.cleanTrackTitleForSearch(trackName)
            val cleanArtist = LrcParser.cleanArtistForSearch(artistName)
            var results = if (artistName.isNotBlank()) {
                api.searchLyrics(artistName = artistName, trackName = cleanTrack)
            } else {
                api.searchLyrics(query = cleanTrack)
            }
            if (results.isEmpty() && cleanArtist.isNotBlank() && cleanArtist != artistName) {
                results = api.searchLyrics(artistName = cleanArtist, trackName = cleanTrack)
            }
            results.sortedWith(
                compareByDescending<LrcLibResponse> { !it.syncedLyrics.isNullOrBlank() }
                    .thenBy { resp ->
                        if (durationSec != null && resp.duration != null) {
                            abs(resp.duration.toInt() - durationSec)
                        } else {
                            0
                        }
                    }
            ).firstOrNull()
        }.getOrNull()
    }

    private suspend fun tryLoadCrossEngineCache(
        track: MediaTrackState,
        targetLang: String
    ): LyricsState? {
        val cached = safeCatching {
            dao.getLatestByTrackAndLang(
                artistName = track.artistName.trim(),
                trackName = track.trackName.trim(),
                targetLang = targetLang
            )
        }.getOrNull() ?: return null

        val cachedLines: List<LyricLine> = safeCatching {
            gson.fromJson<List<LyricLine>>(cached.linesJson, lineListType)
        }.getOrDefault(emptyList())
        if (cachedLines.isEmpty()) return null

        safeCatching { dao.touch(cached.cacheKey) }
        return LyricsState(
            trackId = track.trackId,
            lines = cachedLines,
            isSynced = cached.isSynced,
            sourceLang = cached.sourceLang,
            targetLang = cached.targetLang,
            syncOffsetMs = cached.syncOffsetMs,
            isLoading = false,
            isFromCache = true
        )
    }

    private suspend fun saveToCache(
        cacheKey: String,
        track: MediaTrackState,
        sourceLang: String,
        targetLang: String,
        isSynced: Boolean,
        lines: List<LyricLine>
    ) {
        safeCatching {
            val existing = dao.getByKey(cacheKey)
            val existingFav = existing?.isFavorite ?: false
            val existingOffset = existing?.syncOffsetMs ?: if (MediaStateBus.lyricsState.value.trackId == track.trackId) {
                MediaStateBus.lyricsState.value.syncOffsetMs
            } else {
                0L
            }
            dao.insert(
                CachedLyricsEntity(
                    cacheKey = cacheKey,
                    artistName = track.artistName.trim(),
                    trackName = track.trackName.trim(),
                    sourceLang = sourceLang,
                    targetLang = targetLang,
                    isSynced = isSynced,
                    linesJson = gson.toJson(lines),
                    isFavorite = existingFav,
                    syncOffsetMs = existingOffset
                )
            )
            if (dao.count() > 500) {
                dao.pruneLeastRecentlyUsed(maxEntries = 500)
            }
        }
    }

    private inline fun <T> safeCatching(block: () -> T): Result<T> {
        return runCatching(block).onFailure {
            if (it is CancellationException) throw it
        }
    }
}

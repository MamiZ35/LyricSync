package com.lyricsync.app.data.remote

import android.content.Context
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val latestTag: String,
    val downloadUrl: String,
    val isUpdateAvailable: Boolean
)

object UpdateChecker {

    const val LATEST_APK_URL =
        "https://github.com/MamiZ35/LyricSync/releases/latest/download/LyricSync.apk"

    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/MamiZ35/LyricSync/releases/latest"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun checkLatestRelease(currentVersionName: String): ReleaseInfo? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(LATEST_RELEASE_API)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "LyricSyncTranslator")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body?.string() ?: return@withContext null
                    val obj = JsonParser.parseString(body).asJsonObject
                    val tagName = obj.get("tag_name")?.asString?.trim() ?: return@withContext null

                    var downloadUrl = LATEST_APK_URL
                    val assets = obj.getAsJsonArray("assets")
                    if (assets != null) {
                        for (element in assets) {
                            val asset = element.asJsonObject
                            val name = asset.get("name")?.asString.orEmpty()
                            val browserUrl = asset.get("browser_download_url")?.asString
                            if (name.endsWith(".apk", ignoreCase = true) &&
                                !browserUrl.isNullOrBlank() &&
                                isTrustedGithubUrl(browserUrl)
                            ) {
                                downloadUrl = browserUrl
                                break
                            }
                        }
                    }

                    ReleaseInfo(
                        latestTag = tagName,
                        downloadUrl = downloadUrl,
                        isUpdateAvailable = isNewerVersion(tagName, currentVersionName)
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                null
            }
        }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        if (!isTrustedGithubUrl(downloadUrl)) return@withContext null
        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val tmpFile = File(updateDir, "LyricSync-update.apk.tmp")
        val outFile = File(updateDir, "LyricSync-update.apk")
        tmpFile.delete()

        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "LyricSyncTranslator")
                .build()

            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                if (!isTrustedGithubUrl(response.request.url.toString())) return@withContext null
                val body = response.body ?: return@withContext null
                val totalBytes = body.contentLength()
                var bytesReadTotal = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var lastReportedPct = -1

                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesReadTotal += read
                            if (totalBytes > 0L) {
                                val pct = ((bytesReadTotal * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                if (pct != lastReportedPct) {
                                    lastReportedPct = pct
                                    withContext(Dispatchers.Main) { onProgress(pct) }
                                }
                            }
                        }
                        output.flush()
                    }
                }

                if (bytesReadTotal <= 0L || (totalBytes > 0L && bytesReadTotal != totalBytes)) {
                    tmpFile.delete()
                    return@withContext null
                }

                // Verify APK (ZIP) magic header "PK" before replacing final APK file
                val hasZipMagic = tmpFile.inputStream().use { stream ->
                    val header = ByteArray(2)
                    stream.read(header) == 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
                }
                if (!hasZipMagic) {
                    tmpFile.delete()
                    return@withContext null
                }

                @Suppress("DEPRECATION")
                val archiveInfo = runCatching {
                    context.packageManager.getPackageArchiveInfo(tmpFile.absolutePath, 0)
                }.getOrNull()
                if (archiveInfo == null || archiveInfo.packageName != context.packageName) {
                    tmpFile.delete()
                    return@withContext null
                }

                if (outFile.exists()) outFile.delete()
                if (!tmpFile.renameTo(outFile)) {
                    tmpFile.copyTo(outFile, overwrite = true)
                    tmpFile.delete()
                }
                withContext(Dispatchers.Main) { onProgress(100) }
                outFile
            }
        } catch (ce: CancellationException) {
            tmpFile.delete()
            throw ce
        } catch (_: Exception) {
            tmpFile.delete()
            null
        }
    }

    internal fun isTrustedGithubUrl(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        return host == "github.com" ||
            host.endsWith(".github.com") ||
            host == "githubusercontent.com" ||
            host.endsWith(".githubusercontent.com")
    }

    internal fun isNewerVersion(latestTag: String, currentVersion: String): Boolean {
        val latestParts = latestTag.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = currentVersion.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }
}

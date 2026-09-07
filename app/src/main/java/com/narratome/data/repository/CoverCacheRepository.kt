package com.narratome.data.repository

import android.content.Context
import com.narratome.data.remote.ServerBaseUrlResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverCacheRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val baseUrlResolver: ServerBaseUrlResolver,
) {

    private val memoryCache = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val coversDir: File
        get() = File(context.filesDir, "covers").also { it.mkdirs() }

    private val _coverRevision = MutableStateFlow(0L)
    val coverRevision: StateFlow<Long> = _coverRevision.asStateFlow()

    init {
        preloadMemoryCache()
    }

    private fun preloadMemoryCache() {
        coversDir.listFiles()?.forEach { file ->
            val name = file.name
            if (name.startsWith("cover_") && name.endsWith(".bin")) {
                val id = name.removePrefix("cover_").removeSuffix(".bin")
                memoryCache.add(id)
            }
        }
    }

    fun localCoverFile(libraryItemId: String): File? {
        if (!validCoverId(libraryItemId)) return null

        if (memoryCache.contains(libraryItemId)) {
            return File(coversDir, "cover_$libraryItemId.bin")
        }

        val f = File(coversDir, "cover_$libraryItemId.bin")
        return if (f.isFile && f.length() > 0L) f else null
    }

    /** Local cover file only — catalog UI must not load cover URLs from the server. */
    fun coverModelForItem(libraryItemId: String): Any? = localCoverFile(libraryItemId)

    suspend fun coverCacheStats(): CoverCacheStats = withContext(Dispatchers.IO) {
        val files = coversDir.listFiles()?.filter { it.isFile } ?: emptyList()
        CoverCacheStats(
            fileCount = files.count { it.name.startsWith("cover_") && it.name.endsWith(".bin") },
            bytes = files.sumOf { it.length() },
        )
    }

    suspend fun clearCoverCache(): CoverCacheStats = withContext(Dispatchers.IO) {
        val before = coverCacheStats()
        coversDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("cover_")) {
                file.delete()
            }
        }
        memoryCache.clear()
        _coverRevision.update { it + 1L }
        before
    }

    suspend fun prefetchCover(libraryItemId: String, force: Boolean = false) = withContext(Dispatchers.IO) {
        if (downloadCoverBytes(libraryItemId, force)) {
            _coverRevision.update { it + 1L }
        }
    }

    /**
     * Downloads covers only for ids that do not already have a non-empty local file.
     * Runs a small number of requests in parallel so list loads fill the disk cache quickly.
     */
    suspend fun prefetchCovers(itemIds: Collection<String>) = withContext(Dispatchers.IO) {
        val missing = itemIds.asSequence().distinct().filter { localCoverFile(it) == null }.toList()
        if (missing.isEmpty()) return@withContext
        val anyNew = coroutineScope {
            val sem = Semaphore(4)
            missing.map { id ->
                async {
                    sem.withPermit { downloadCoverBytes(id, force = false) }
                }
            }.awaitAll().any { it }
        }
        if (anyNew) {
            _coverRevision.update { it + 1L }
        }
    }

    /**
     * @return true if a new non-empty cover file is present after this call (download succeeded).
     */
    private fun downloadCoverBytes(libraryItemId: String, force: Boolean): Boolean {
        if (!validCoverId(libraryItemId)) return false
        if (!force && localCoverFile(libraryItemId) != null) return false
        val base = baseUrlResolver.resolveBlocking()?.trimEnd('/') ?: return false
        val url = "$base/api/items/$libraryItemId/cover?raw=1"
        val out = File(coversDir, "cover_$libraryItemId.bin.tmp")
        return try {
            val req = Request.Builder().url(url).get().build()
            val wrote = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val body = resp.body ?: return@use false
                body.byteStream().use { ins ->
                    out.outputStream().use { outs -> ins.copyTo(outs) }
                }
                true
            }
            if (!wrote) {
                out.delete()
                false
            } else if (out.length() > 0L) {
                val finalFile = File(coversDir, "cover_$libraryItemId.bin")
                if (finalFile.exists()) finalFile.delete()
                val renamed = out.renameTo(finalFile)
                if (renamed && finalFile.isFile && finalFile.length() > 0L) {
                    memoryCache.add(libraryItemId)
                }
                renamed && finalFile.isFile && finalFile.length() > 0L
            } else {
                out.delete()
                false
            }

        } catch (_: Exception) {
            out.delete()
            false
        }
    }
}

internal fun validCoverId(id: String): Boolean = id.isNotBlank() && id !in setOf(".", "..") &&
    id.none { it == '/' || it == '\\' || it.isISOControl() }

data class CoverCacheStats(
    val fileCount: Int,
    val bytes: Long,
)

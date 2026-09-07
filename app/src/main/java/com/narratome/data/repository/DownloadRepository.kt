package com.narratome.data.repository

import android.content.Context
import android.os.StatFs
import com.narratome.data.download.ResumableDownloader
import com.narratome.data.remote.HttpsRequiredException
import com.narratome.data.local.db.CatalogDao
import com.narratome.data.local.db.DownloadJobDao
import com.narratome.data.local.db.DownloadJobEntity
import com.narratome.data.local.db.LocalDownloadDao
import com.narratome.data.local.db.LocalDownloadManifestEntity
import com.narratome.data.local.db.LocalDownloadPartEntity
import com.narratome.data.remote.AudiobookshelfApi
import com.narratome.data.remote.PlayResponseParser
import com.narratome.data.remote.ServerBaseUrlResolver
import com.narratome.domain.download.DownloadJobState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class DownloadRepository @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val api: AudiobookshelfApi,
    private val baseUrlResolver: ServerBaseUrlResolver,
    private val resumableDownloader: ResumableDownloader,
    private val localDownloadDao: LocalDownloadDao,
    private val downloadJobDao: DownloadJobDao,
    private val catalogDao: CatalogDao,
) {
    private val downloadLocks = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

    suspend fun <T> withDownloadLock(key: String, block: suspend () -> T): T {
        val lock = downloadLocks.getOrPut(key) { kotlinx.coroutines.sync.Mutex() }
        lock.lock()
        return try { block() } finally { lock.unlock() }
    }

    suspend fun setDownloadJobState(key: String, state: String, error: String? = null) {
        downloadJobDao.setState(key, state, error, System.currentTimeMillis())
    }

    suspend fun queueNetworkPausedDownload(key: String): Boolean =
        downloadJobDao.queueNetworkPaused(key, System.currentTimeMillis()) > 0

    suspend fun downloadLibraryItem(
        libraryItemId: String,
        title: String?,
        isCancelled: () -> Boolean,
        strictCleanup: Boolean,
        maxParallelParts: Int,
        verifySizesFromApi: Boolean,
        resumeFromParts: Boolean = false,
        episodeId: String? = null,
        isNetworkPaused: () -> Boolean = { false },
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanEpisodeId = episodeId?.takeIf { it.isNotBlank() }
        val downloadKey = mediaProgressKey(libraryItemId, cleanEpisodeId)
        val existingJob = downloadJobDao.getJob(downloadKey)
        val startedAt = existingJob?.startedAtEpochMs
            ?.takeIf { it > 0L && existingJob.state in setOf(DownloadJobState.QUEUED, DownloadJobState.RUNNING) }
            ?: System.currentTimeMillis()

        suspend fun persistJob(
            state: String,
            partIndex: Int,
            totalParts: Int,
            bytesDone: Long,
            bytesTotal: Long,
            bytesDoneTotal: Long,
            bytesTotalAll: Long,
            err: String?,
        ) {
            downloadJobDao.saveProgress(
                DownloadJobEntity(
                    downloadKey = downloadKey,
                    libraryItemId = libraryItemId,
                    episodeId = cleanEpisodeId,
                    title = title,
                    state = state,
                    currentPartIndex = partIndex,
                    totalParts = totalParts,
                    bytesDownloadedThisPart = bytesDone,
                    bytesTotalThisPart = bytesTotal,
                    bytesDownloadedTotal = bytesDoneTotal,
                    bytesTotal = bytesTotalAll,
                    lastError = err,
                    startedAtEpochMs = startedAt,
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }

        try {
            val now = System.currentTimeMillis()
            persistJob(DownloadJobState.QUEUED, 0, 0, 0, 0, 0, 0, null)
            if (isCancelled()) throw CancellationException()

            // forceDirectPlay=true is required for downloads. Without it the server may return
            // an HLS transcoded stream (m3u8) instead of the actual file.
            val playBody = """{"forceDirectPlay":true,"forceTranscode":false,"mediaPlayer":"android","deviceInfo":{"clientName":"NarraTome","clientVersion":"1.0"}}"""
            val requestBody = playBody.toRequestBody("application/json".toMediaType())
            val play = if (cleanEpisodeId == null) {
                api.playItem(libraryItemId, requestBody)
            } else {
                api.playPodcastEpisode(libraryItemId, cleanEpisodeId, requestBody)
            }
            val base = baseUrlResolver.resolveBlocking()
            val tracks = PlayResponseParser.extractTrackDownloadInfos(play, base)
            if (tracks.isEmpty()) error("No audio tracks in play response")

            val knownSizes = tracks.mapNotNull { it.expectedBytes }
            if (knownSizes.isNotEmpty()) {
                val sum = knownSizes.sum()
                val avail = StatFs(appContext.filesDir.path).availableBytes
                if (avail < sum + 10L * 1024 * 1024) {
                    error("Not enough free space for download (need ~${sum / 1024 / 1024} MB)")
                }
            }

            val itemDir = itemDownloadDir(libraryItemId, cleanEpisodeId)

            val completedPartIndices: Set<Int>
            if (resumeFromParts) {
                completedPartIndices = localDownloadDao.listParts(downloadKey)
                    .filter { part ->
                        part.partIndex in tracks.indices &&
                            File(itemDir, diskNameForPart(part.partIndex, tracks[part.partIndex].url))
                                .let { it.isFile && it.length() > 0 }
                    }
                    .map { it.partIndex }.toSet()
                itemDir.mkdirs()
            } else {
                itemDir.deleteRecursively()
                localDownloadDao.deleteParts(downloadKey)
                localDownloadDao.deleteManifest(downloadKey)
                itemDir.mkdirs()
                completedPartIndices = emptySet()
            }

            val total = tracks.size
            val partDownloaded = LongArray(total)
            val partTotals = LongArray(total)
            val progressLock = Any()
            tracks.forEachIndexed { index, track ->
                partTotals[index] = track.expectedBytes ?: 0L
            }
            completedPartIndices.forEach { index ->
                if (index in tracks.indices) {
                    val fileName = diskNameForPart(index, tracks[index].url)
                    val existingFile = File(itemDir, fileName)
                    partDownloaded[index] = if (existingFile.isFile) existingFile.length() else 0L
                    if (partTotals[index] == 0L && partDownloaded[index] > 0L) {
                        partTotals[index] = partDownloaded[index]
                    }
                }
            }

            fun aggregateProgress(): Pair<Long, Long> {
                val done = partDownloaded.sum()
                val allTotalsKnown = partTotals.all { it > 0L }
                val totalBytes = if (allTotalsKnown) partTotals.sum() else 0L
                return done to totalBytes
            }

            suspend fun persistRunningProgress(
                partIndex: Int,
                bytesDoneThisPart: Long,
                bytesTotalThisPart: Long,
            ) {
                val (doneTotal, bytesTotalAll) = synchronized(progressLock) {
                    if (partIndex in partDownloaded.indices) {
                        partDownloaded[partIndex] = bytesDoneThisPart
                        if (bytesTotalThisPart > 0L) {
                            partTotals[partIndex] = bytesTotalThisPart
                        }
                    }
                    aggregateProgress()
                }
                persistJob(
                    state = DownloadJobState.RUNNING,
                    partIndex = partIndex,
                    totalParts = total,
                    bytesDone = bytesDoneThisPart,
                    bytesTotal = bytesTotalThisPart,
                    bytesDoneTotal = doneTotal,
                    bytesTotalAll = bytesTotalAll,
                    err = null,
                )
            }

            val (initialDone, initialTotal) = synchronized(progressLock) { aggregateProgress() }
            persistJob(DownloadJobState.RUNNING, 0, total, 0, 0, initialDone, initialTotal, null)

            val parallel = maxParallelParts.coerceIn(1, 16)
            val lastProgressPersistMs = LongArray(total)
            val lastProgressPersistBytes = LongArray(total)
            val sem = Semaphore(parallel)
            coroutineScope {
                tracks.mapIndexed { index, track ->
                    async {
                        if (index in completedPartIndices) return@async

                        sem.withPermit {
                            if (isCancelled()) throw CancellationException()
                            withContext(Dispatchers.IO) {
                                persistRunningProgress(index, 0, track.expectedBytes ?: 0L)
                                val fileName = diskNameForPart(index, track.url)
                                val outFile = File(itemDir, fileName)
                                resumableDownloader.download(
                                    url = track.url,
                                    outFile = outFile,
                                    isCancelled = isCancelled,
                                    onProgress = { done, totalBytes ->
                                        val nowMs = System.currentTimeMillis()
                                        val byteDelta = done - lastProgressPersistBytes[index]
                                        val dueByTime = nowMs - lastProgressPersistMs[index] >= PROGRESS_PERSIST_INTERVAL_MS
                                        val dueByBytes = byteDelta >= PROGRESS_PERSIST_BYTE_DELTA
                                        if (dueByTime || dueByBytes || done == totalBytes) {
                                            lastProgressPersistMs[index] = nowMs
                                            lastProgressPersistBytes[index] = done
                                            kotlinx.coroutines.runBlocking {
                                                persistRunningProgress(index, done, totalBytes)
                                            }
                                        }
                                    },
                                    strictCleanup = strictCleanup,
                                )
                                if (verifySizesFromApi && track.expectedBytes != null &&
                                    outFile.length() != track.expectedBytes
                                ) {
                                    error("Size mismatch for part $index (expected ${track.expectedBytes}, got ${outFile.length()})")
                                }
                                if (verifySizesFromApi && !track.sha256.isNullOrBlank()) {
                                    val actual = sha256Hex(outFile)
                                    if (!actual.equals(track.sha256.trim(), ignoreCase = true)) {
                                        error("Hash mismatch for part $index")
                                    }
                                }
                                localDownloadDao.upsertPart(
                                    LocalDownloadPartEntity(
                                        downloadKey = downloadKey,
                                        libraryItemId = libraryItemId,
                                        episodeId = cleanEpisodeId,
                                        partIndex = index,
                                        fileName = fileName,
                                        createdAtEpochMs = now,
                                    ),
                                )
                                persistRunningProgress(index, outFile.length(), track.expectedBytes ?: outFile.length())
                            }
                        }
                    }
                }.awaitAll()
            }

            if (isCancelled()) throw CancellationException()
            localDownloadDao.upsertManifest(
                LocalDownloadManifestEntity(
                    downloadKey = downloadKey,
                    libraryItemId = libraryItemId,
                    episodeId = cleanEpisodeId,
                    expectedPartCount = total,
                    completedAtEpochMs = System.currentTimeMillis(),
                ),
            )
            catalogDao.setDownloaded(libraryItemId, true)
            val completedBytes = synchronized(progressLock) {
                for (index in tracks.indices) {
                    val fileName = diskNameForPart(index, tracks[index].url)
                    val length = File(itemDir, fileName).takeIf { it.isFile }?.length() ?: partDownloaded[index]
                    partDownloaded[index] = length
                    if (partTotals[index] == 0L) partTotals[index] = length
                }
                aggregateProgress()
            }
            persistJob(DownloadJobState.COMPLETED, total, total, 0, 0, completedBytes.first, completedBytes.second, null)
            Result.success(Unit)
        } catch (e: CancellationException) {
            withContext(kotlinx.coroutines.NonCancellable) {
                // System/constraint cancellation is resumable. Explicit cancel is finalized by the coordinator.
                setDownloadJobState(downloadKey, DownloadJobState.PAUSED_NETWORK)
            }
            Result.failure(e)
        } catch (e: Exception) {
            val httpsBlocked = generateSequence<Throwable>(e) { it.cause }
                .any { it is HttpsRequiredException }
            setDownloadJobState(downloadKey, when {
                httpsBlocked -> DownloadJobState.PAUSED_NETWORK
                isNetworkPaused() -> DownloadJobState.PAUSED_NETWORK
                isCancelled() -> DownloadJobState.PAUSED_NETWORK
                else -> DownloadJobState.FAILED
            }, if (isCancelled()) null else e.message)
            if (strictCleanup && !resumeFromParts && !isCancelled() && !httpsBlocked) {
                itemDownloadDir(libraryItemId, cleanEpisodeId).deleteRecursively()
                localDownloadDao.deleteParts(downloadKey)
                localDownloadDao.deleteManifest(downloadKey)
                catalogDao.setDownloaded(libraryItemId, localDownloadDao.countManifestsForItem(libraryItemId) > 0)
            }
            Result.failure(e)
        }
    }

    suspend fun enqueueDownloadJob(libraryItemId: String, title: String?, episodeId: String? = null) = withContext(Dispatchers.IO) {
        val cleanEpisodeId = episodeId?.takeIf { it.isNotBlank() }
        val downloadKey = mediaProgressKey(libraryItemId, cleanEpisodeId)
        val existing = downloadJobDao.getJob(downloadKey)
        val now = System.currentTimeMillis()
        val startedAt = existing?.startedAtEpochMs
            ?.takeIf { it > 0L && existing.state in setOf(DownloadJobState.QUEUED, DownloadJobState.RUNNING) }
            ?: now

        downloadJobDao.upsert(
            DownloadJobEntity(
                downloadKey = downloadKey,
                libraryItemId = libraryItemId,
                episodeId = cleanEpisodeId,
                title = title ?: existing?.title,
                state = DownloadJobState.QUEUED,
                currentPartIndex = existing?.currentPartIndex ?: 0,
                totalParts = existing?.totalParts ?: 0,
                bytesDownloadedThisPart = existing?.bytesDownloadedThisPart ?: 0L,
                bytesTotalThisPart = existing?.bytesTotalThisPart ?: 0L,
                bytesDownloadedTotal = existing?.bytesDownloadedTotal ?: 0L,
                bytesTotal = existing?.bytesTotal ?: 0L,
                lastError = null,
                startedAtEpochMs = startedAt,
                updatedAtEpochMs = now,
            ),
        )
    }

    /** Delete all local files and DB records for a downloaded book item or a single podcast episode. */
    suspend fun deleteDownload(libraryItemId: String, episodeId: String? = null) = withContext(Dispatchers.IO) {
        val cleanEpisodeId = episodeId?.takeIf { it.isNotBlank() }
        val downloadKey = mediaProgressKey(libraryItemId, cleanEpisodeId)
        itemDownloadDir(libraryItemId, cleanEpisodeId).deleteRecursively()
        localDownloadDao.deleteParts(downloadKey)
        localDownloadDao.deleteManifest(downloadKey)
        downloadJobDao.delete(downloadKey)
        catalogDao.setDownloaded(libraryItemId, localDownloadDao.countManifestsForItem(libraryItemId) > 0)
    }

    suspend fun deleteDownloadJobRecord(libraryItemId: String, episodeId: String? = null) = withContext(Dispatchers.IO) {
        downloadJobDao.delete(mediaProgressKey(libraryItemId, episodeId))
    }

    fun observeAllDownloadJobs(): Flow<List<DownloadJobEntity>> = downloadJobDao.observeAllJobs()

    fun observeDownloadJobsForItem(libraryItemId: String): Flow<List<DownloadJobEntity>> =
        downloadJobDao.observeJobsForItem(libraryItemId)

    fun observeAllDownloadManifests(): Flow<List<LocalDownloadManifestEntity>> =
        localDownloadDao.observeAllManifests()

    fun observeDownloadJob(libraryItemId: String, episodeId: String? = null): Flow<DownloadJobEntity?> =
        downloadJobDao.observeJob(mediaProgressKey(libraryItemId, episodeId))

    fun observePartsForItem(libraryItemId: String, episodeId: String? = null): Flow<List<LocalDownloadPartEntity>> =
        if (episodeId == null) localDownloadDao.observeParts(mediaProgressKey(libraryItemId))
        else localDownloadDao.observeParts(mediaProgressKey(libraryItemId, episodeId))

    fun observePartsForCatalogItem(libraryItemId: String): Flow<List<LocalDownloadPartEntity>> =
        localDownloadDao.observePartsForItem(libraryItemId)

    /**
     * Total size on disk when the manifest exists and every expected part file is present and non-empty.
     * Matches the contract used for offline playback readiness.
     */
    suspend fun getLocalDownloadTotalBytesIfComplete(libraryItemId: String, episodeId: String? = null): Long? = withContext(Dispatchers.IO) {
        val cleanEpisodeId = episodeId?.takeIf { it.isNotBlank() }
        val downloadKey = mediaProgressKey(libraryItemId, cleanEpisodeId)
        val manifest = localDownloadDao.getManifest(downloadKey) ?: return@withContext null
        val parts = localDownloadDao.listParts(downloadKey)
        if (parts.size != manifest.expectedPartCount) return@withContext null
        val dir = itemDownloadDir(libraryItemId, cleanEpisodeId)
        var sum = 0L
        for (p in parts) {
            val f = File(dir, p.fileName)
            if (!f.isFile || f.length() == 0L) return@withContext null
            sum += f.length()
        }
        sum
    }

    /** Sum of existing part file lengths (incomplete downloads may be non-zero). */
    suspend fun sumOnDiskPartBytes(libraryItemId: String, episodeId: String? = null): Long = withContext(Dispatchers.IO) {
        val cleanEpisodeId = episodeId?.takeIf { it.isNotBlank() }
        val downloadKey = mediaProgressKey(libraryItemId, cleanEpisodeId)
        val parts = localDownloadDao.listParts(downloadKey)
        val dir = itemDownloadDir(libraryItemId, cleanEpisodeId)
        parts.sumOf { p ->
            val f = File(dir, p.fileName)
            if (f.isFile) f.length() else 0L
        }
    }

    suspend fun listDownloadedEpisodeIds(libraryItemId: String): Set<String> = withContext(Dispatchers.IO) {
        localDownloadDao.listManifestsForItem(libraryItemId)
            .mapNotNull { it.episodeId }
            .toSet()
    }

    fun observeActiveDownloadCount(): Flow<Int> = downloadJobDao.observeActiveCount()

    fun observeFailedDownloadCount(): Flow<Int> = downloadJobDao.observeFailedCount()

    suspend fun clearFailedDownloadRecords(): Int = withContext(Dispatchers.IO) {
        downloadJobDao.deleteByStates(listOf(DownloadJobState.FAILED, DownloadJobState.CANCELLED))
    }

    fun itemDownloadDir(libraryItemId: String): File = itemDownloadDir(libraryItemId, episodeId = null)

    fun itemDownloadDir(libraryItemId: String, episodeId: String?): File {
        val root = File(appContext.filesDir, "downloads")
        return if (episodeId.isNullOrBlank()) {
            File(root, "items/${safeDiskSegment(libraryItemId)}")
        } else {
            File(File(root, "podcast_episodes/${safeDiskSegment(libraryItemId)}"), safeDiskSegment(episodeId))
        }
    }

    private fun safeDiskSegment(value: String): String =
        value.replace(Regex("[^a-zA-Z0-9._-]+"), "_").trim('_').ifBlank { "item" }

    private fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = ins.read(buf)
                if (r <= 0) break
                md.update(buf, 0, r)
            }
        }
        return md.digest().joinToString("") { b -> "%02x".format(b) }
    }

    private fun diskNameForPart(index: Int, url: String): String {
        val path = url.substringBefore('?').substringAfterLast('/')
        val dot = path.lastIndexOf('.')
        val ext = if (dot >= 0) path.substring(dot + 1).take(8).ifBlank { "m4b" } else "m4b"
        val rawBase = if (dot >= 0) path.substring(0, dot) else path
        val base = rawBase.ifBlank { "part" }
            .replace(Regex("[^a-zA-Z0-9._-]+"), "_")
            .trim('_')
            .take(48)
        return "${index.toString().padStart(3, '0')}_$base.$ext"
    }

    private companion object {
        const val PROGRESS_PERSIST_INTERVAL_MS = 500L
        const val PROGRESS_PERSIST_BYTE_DELTA = 1L * 1024L * 1024L
    }
}

package com.narratome.data.download

import com.narratome.data.remote.HttpsRequiredException

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.math.min

/** Partial files are never published as playable: the repository commits a manifest only on completion. */
class ResumableDownloader(private val client: OkHttpClient) {
    fun download(url: String, outFile: File, isCancelled: () -> Boolean,
                 onProgress: (Long, Long) -> Unit, strictCleanup: Boolean) {
        // Recover partial files written by the previous downloader version.
        val legacyPart = File(outFile.parentFile, outFile.name + ".part")
        if (!outFile.exists() && legacyPart.exists() && !legacyPart.renameTo(outFile)) {
            throw IOException("Could not recover partial download")
        }
        repeat(5) { attempt ->
            if (isCancelled()) throw IOException("Download paused or cancelled")
            try {
                downloadOnce(url, outFile, isCancelled, onProgress)
                return
            } catch (error: IOException) {
                if (error is HttpsRequiredException) throw error
                if (isCancelled()) throw error
                if (attempt == 4) {
                    if (strictCleanup) outFile.delete()
                    throw error
                }
                var remaining = min(8000L, 500L shl attempt)
                while (remaining > 0 && !isCancelled()) {
                    Thread.sleep(min(remaining, 100L))
                    remaining -= 100L
                }
            }
        }
    }

    private fun downloadOnce(url: String, outFile: File, isCancelled: () -> Boolean, onProgress: (Long, Long) -> Unit) {
        val existing = outFile.takeIf { it.isFile }?.length() ?: 0L
        val request = Request.Builder().url(url).header("Accept-Encoding", "identity")
            .apply { if (existing > 0) header("Range", "bytes=$existing-") }.build()
        if (isCancelled()) throw IOException("Download paused or cancelled")
        client.newCall(request).execute().use { response ->
            if (isCancelled()) throw IOException("Download paused or cancelled")
            val range = response.header("Content-Range")
            val total = range?.substringAfterLast('/')?.toLongOrNull()
            if (response.code == 416 && total == existing && existing > 0) {
                onProgress(existing, existing)
                return
            }
            if (response.code == 416 && total != null && existing > total) {
                outFile.delete() // The saved partial no longer matches the server file.
            }
            if (!response.isSuccessful) throw IOException("GET failed ${response.code}")
            val partial = response.code == 206
            if (partial && range?.substringAfter("bytes ")?.substringBefore('-')?.toLongOrNull() != existing) {
                throw IOException("Invalid resume range")
            }
            val body = response.body
            var written = if (partial) existing else 0L
            val expected = total ?: body.contentLength().takeIf { it >= 0 }?.plus(written)
            RandomAccessFile(outFile, "rw").use { file ->
                if (!partial) file.setLength(0) // Server ignored Range; consume this full response.
                file.seek(written)
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (!isCancelled()) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        file.write(buffer, 0, count)
                        written += count
                        onProgress(written, expected ?: written)
                    }
                }
            }
            if (isCancelled()) throw IOException("Download paused or cancelled")
            if (expected != null && written != expected) throw IOException("Size mismatch expected=$expected actual=$written")
        }
    }
}

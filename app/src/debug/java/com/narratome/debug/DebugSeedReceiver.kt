package com.narratome.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import com.narratome.data.local.db.CatalogDao
import com.narratome.data.local.db.CatalogItemEntity
import com.narratome.data.local.db.LocalDownloadDao
import com.narratome.data.local.db.LocalDownloadManifestEntity
import com.narratome.data.local.db.LocalDownloadPartEntity
import com.narratome.data.local.db.MediaProgressEntity
import com.narratome.data.local.db.ProgressDao
import com.narratome.data.repository.mediaProgressKey
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.sin

/**
 * Debug-only: seeds one fake "downloaded" audiobook with saved progress so playback
 * resumption (Android Auto, media buttons) can be tested without a server.
 *
 *   adb shell am broadcast -n com.narratome/.debug.DebugSeedReceiver
 *
 * Optional extras: --ei positionSec 60 (resume position), --ez remove true (delete seed audio),
 * --es mediaButton PLAY (simulate a car/headset key: PLAY, PAUSE, PLAY_PAUSE, ...).
 */
@AndroidEntryPoint
class DebugSeedReceiver : BroadcastReceiver() {

    @Inject lateinit var catalogDao: CatalogDao
    @Inject lateinit var progressDao: ProgressDao
    @Inject lateinit var localDownloadDao: LocalDownloadDao

    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("mediaButton")?.let { name ->
            sendMediaButton(context, name)
            return
        }
        val pending = goAsync()
        val positionSec = intent.getIntExtra("positionSec", DEFAULT_POSITION_SEC)
        val remove = intent.getBooleanExtra("remove", false)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (remove) remove(context) else seed(context, positionSec)
            } catch (e: Exception) {
                Log.e(TAG, "Seeding failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun seed(context: Context, positionSec: Int) {
        val now = System.currentTimeMillis()
        val dir = File(context.filesDir, "downloads/items/$ITEM_ID").apply { mkdirs() }
        val audio = File(dir, FILE_NAME)
        if (!audio.isFile || audio.length() == 0L) writeToneWav(audio, DURATION_SEC)

        catalogDao.upsertItem(
            CatalogItemEntity(
                libraryItemId = ITEM_ID,
                libraryId = LIBRARY_ID,
                serverUpdatedAtEpochMs = now,
                title = "Debug Test Book",
                author = "NarraTome Debug",
                seriesName = null,
                seriesId = null,
                addedAtEpochMs = now,
                coverPath = null,
                progress = positionSec.toFloat() / DURATION_SEC,
                hydrated = false,
                payloadJson = null,
                isDownloaded = true,
            ),
        )
        val key = mediaProgressKey(ITEM_ID)
        localDownloadDao.upsertManifest(
            LocalDownloadManifestEntity(
                downloadKey = key,
                libraryItemId = ITEM_ID,
                expectedPartCount = 1,
                completedAtEpochMs = now,
            ),
        )
        localDownloadDao.upsertPart(
            LocalDownloadPartEntity(
                downloadKey = key,
                libraryItemId = ITEM_ID,
                partIndex = 0,
                fileName = FILE_NAME,
                createdAtEpochMs = now,
                durationSec = DURATION_SEC.toDouble(),
            ),
        )
        progressDao.upsert(
            MediaProgressEntity(
                progressKey = key,
                libraryItemId = ITEM_ID,
                currentTimeSec = positionSec.toDouble(),
                durationSec = DURATION_SEC.toDouble(),
                lastKnownServerLastUpdate = null,
                localDirty = false,
                localRevision = 0,
                libraryId = LIBRARY_ID,
                startedAt = now,
                lastInteractionTime = now,
            ),
        )
        Log.i(TAG, "Seeded $ITEM_ID at ${positionSec}s (${audio.absolutePath})")
    }

    /**
     * Simulates a hardware/car media key the way the system delivers it to a stopped app:
     * a MEDIA_BUTTON broadcast to Media3's MediaButtonReceiver. Name is a KeyEvent suffix, e.g. PLAY.
     */
    private fun sendMediaButton(context: Context, name: String) {
        val keyCode = KeyEvent.keyCodeFromString("KEYCODE_MEDIA_$name")
            .takeIf { it != KeyEvent.KEYCODE_UNKNOWN }
            ?: KeyEvent.keyCodeFromString("KEYCODE_$name")
        listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach { action ->
            context.sendBroadcast(
                Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setClassName(context, "androidx.media3.session.MediaButtonReceiver")
                    .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, keyCode)),
            )
        }
        Log.i(TAG, "Sent media button ${KeyEvent.keyCodeToString(keyCode)}")
    }

    private fun remove(context: Context) {
        File(context.filesDir, "downloads/items/$ITEM_ID").deleteRecursively()
        Log.i(TAG, "Removed seed audio for $ITEM_ID; clear app data to drop its DB rows")
    }

    /** 16-bit mono PCM WAV with a quiet tone that steps in pitch every 10s, so seeks are audible. */
    private fun writeToneWav(file: File, durationSec: Int) {
        val sampleRate = 8_000
        val samples = sampleRate * durationSec
        val dataBytes = samples * 2
        RandomAccessFile(file, "rw").use { out ->
            out.setLength(0)
            fun intLE(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun shortLE(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            out.writeBytes("RIFF"); intLE(36 + dataBytes); out.writeBytes("WAVE")
            out.writeBytes("fmt "); intLE(16); shortLE(1); shortLE(1); intLE(sampleRate); intLE(sampleRate * 2); shortLE(2); shortLE(16)
            out.writeBytes("data"); intLE(dataBytes)
            val buffer = ByteArray(sampleRate * 2)
            for (second in 0 until durationSec) {
                val freq = 300.0 + (second / 10) * 40.0
                for (i in 0 until sampleRate) {
                    val t = (second * sampleRate + i).toDouble() / sampleRate
                    val v = (sin(2 * PI * freq * t) * 3_000).toInt()
                    buffer[i * 2] = v.toByte()
                    buffer[i * 2 + 1] = (v shr 8).toByte()
                }
                out.write(buffer)
            }
        }
    }

    private companion object {
        const val TAG = "NarraTomeDebugSeed"
        const val ITEM_ID = "debug-test-book"
        const val LIBRARY_ID = "debug-library"
        const val FILE_NAME = "part0.wav"
        const val DURATION_SEC = 180
        const val DEFAULT_POSITION_SEC = 60
    }
}

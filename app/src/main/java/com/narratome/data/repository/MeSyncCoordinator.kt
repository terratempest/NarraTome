package com.narratome.data.repository

import android.os.SystemClock
import com.narratome.domain.model.SyncConflictPolicy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debounces duplicate [MeSyncRepository.syncFromServer] calls (e.g. Home then Library on cold start).
 */
@Singleton
class MeSyncCoordinator @Inject constructor(
    private val meSyncRepository: MeSyncRepository,
) {
    private val mutex = Mutex()
    private val lastSyncElapsedMs = AtomicLong(0L)

    suspend fun syncFromServer(policy: SyncConflictPolicy, force: Boolean = false) {
        mutex.withLock {
            if (!force) {
                val last = lastSyncElapsedMs.get()
                val now = SystemClock.elapsedRealtime()
                if (last != 0L && now - last < MIN_INTERVAL_MS) return
            }
            runCatching { meSyncRepository.syncFromServer(policy) }
            lastSyncElapsedMs.set(SystemClock.elapsedRealtime())
        }
    }

    companion object {
        private const val MIN_INTERVAL_MS = 45_000L
    }
}

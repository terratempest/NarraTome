package com.narratome.domain.progress

import com.narratome.data.local.db.MediaProgressEntity
import kotlin.math.abs
import kotlin.math.max

object ProgressConflictRules {

    fun meaningfulProgressDelta(localSec: Double, serverSec: Double, durationSec: Double): Boolean {
        val threshold = max(3.0, durationSec * 0.005)
        return abs(localSec - serverSec) >= threshold
    }

    fun hasDirtyConflict(
        local: MediaProgressEntity?,
        serverTimeSec: Double,
        durationSec: Double,
        serverLastUpdate: Long?,
    ): Boolean {
        if (local == null || !local.localDirty) return false

        // 1. If local is significantly further along, keep local (no conflict to resolve)
        // This handles the "offline progress" case where the server is lagging behind the reality of the device.
        if (local.currentTimeSec > serverTimeSec + 5.0) return false

        // 2. If the local interaction is newer than the server's last update, keep local.
        // serverLastUpdate is usually epoch MS from the server.
        // local.lastInteractionTime is epoch MS on the device.
        val localTime = local.lastInteractionTime ?: 0L
        val serverTime = serverLastUpdate ?: 0L
        if (localTime > serverTime + 2000) { // 2s buffer for clock drift/sync delays
            return false
        }

        // Otherwise, if there's a "meaningful" difference, it's a conflict the user or policy should decide.
        return meaningfulProgressDelta(local.currentTimeSec, serverTimeSec, durationSec)
    }
}

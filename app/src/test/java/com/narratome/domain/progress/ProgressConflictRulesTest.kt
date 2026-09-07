package com.narratome.domain.progress

import com.narratome.data.local.db.MediaProgressEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressConflictRulesTest {

    @Test
    fun meaningfulDelta_smallDifference_returnsFalse() {
        assertFalse(ProgressConflictRules.meaningfulProgressDelta(10.0, 11.0, 3600.0))
    }

    @Test
    fun meaningfulDelta_largeDifference_returnsTrue() {
        assertTrue(ProgressConflictRules.meaningfulProgressDelta(10.0, 120.0, 3600.0))
    }

    @Test
    fun hasDirtyConflict_notDirty_returnsFalse() {
        val local = MediaProgressEntity(
            progressKey = "x",
            libraryItemId = "x",
            currentTimeSec = 99.0,
            durationSec = 100.0,
            lastKnownServerLastUpdate = null,
            localDirty = false,
            localRevision = 0L,
        )
        assertFalse(ProgressConflictRules.hasDirtyConflict(local, 5.0, 100.0, serverLastUpdate = null))
    }

    @Test
    fun hasDirtyConflict_localAheadOfServer_returnsFalse() {
        val local = MediaProgressEntity(
            progressKey = "x",
            libraryItemId = "x",
            currentTimeSec = 200.0,
            durationSec = 3600.0,
            lastKnownServerLastUpdate = null,
            localDirty = true,
            localRevision = 1L,
        )
        assertFalse(ProgressConflictRules.hasDirtyConflict(local, 10.0, 3600.0, serverLastUpdate = null))
    }

    @Test
    fun hasDirtyConflict_serverNewerAndLargeDelta_returnsTrue() {
        val local = MediaProgressEntity(
            progressKey = "x",
            libraryItemId = "x",
            currentTimeSec = 10.0,
            durationSec = 3600.0,
            lastKnownServerLastUpdate = null,
            localDirty = true,
            localRevision = 1L,
            lastInteractionTime = 1_000L,
        )
        assertTrue(ProgressConflictRules.hasDirtyConflict(local, 200.0, 3600.0, serverLastUpdate = 10_000L))
    }
}

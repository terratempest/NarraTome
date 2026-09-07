package com.narratome.data.repository

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogSyncActivityTest {
    @Test
    fun completionFailureAndCancellationResetActivity() = runTest {
        val activity = CatalogSyncActivity()
        assertThat(activity.running.first()).isFalse()
        val result = activity.track {
            assertThat(activity.running.first()).isTrue()
            "done"
        }
        assertThat(result).isEqualTo("done")
        assertThat(activity.running.first()).isFalse()

        val failure = IllegalStateException("sync failed")
        val thrown = runCatching {
            activity.track {
                assertThat(activity.running.first()).isTrue()
                throw failure
            }
        }.exceptionOrNull()
        assertThat(thrown).isSameInstanceAs(failure)
        assertThat(activity.running.first()).isFalse()

        val job = launch { activity.track { awaitCancellation() } }
        runCurrent()
        assertThat(activity.running.first()).isTrue()
        job.cancelAndJoin()
        assertThat(activity.running.first()).isFalse()
    }

    @Test
    fun overlappingSyncsRemainActiveUntilLastSyncExits() = runTest {
        val activity = CatalogSyncActivity()
        val finishFirst = CompletableDeferred<Unit>()
        val first = launch { activity.track { finishFirst.await() } }
        val second = launch { activity.track { awaitCancellation() } }
        runCurrent()
        assertThat(activity.running.first()).isTrue()

        // A new process starts idle even if the previous process died during sync.
        assertThat(CatalogSyncActivity().running.first()).isFalse()

        finishFirst.complete(Unit)
        first.join()
        assertThat(activity.running.first()).isTrue()
        second.cancelAndJoin()
        assertThat(activity.running.first()).isFalse()
    }
}

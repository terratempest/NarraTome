package com.narratome.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.narratome.data.remote.AudiobookshelfApi
import com.narratome.data.remote.ServerBaseUrlResolver
import com.narratome.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerReachabilityRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: AudiobookshelfApi,
    private val networkPolicy: MediaNetworkPolicy,
    private val resolver: ServerBaseUrlResolver,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val _serverReachable = MutableStateFlow(false)
    val serverReachable: StateFlow<Boolean> = _serverReachable.asStateFlow()

    init {
        scope.launch {
            networkPolicy.state.collectLatest { refresh() }
        }
    }
    suspend fun refresh() {
        withContext(Dispatchers.IO) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            if (network == null) {
                _serverReachable.value = false
                return@withContext
            }
            val caps = cm.getNetworkCapabilities(network)
            if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                _serverReachable.value = false
                return@withContext
            }
            val generation = networkPolicy.state.value.generation
            val reachable = probeServer(api, resolver)
            if (networkPolicy.state.value.generation != generation) return@withContext
            val wasReachable = _serverReachable.value
            _serverReachable.value = reachable
            if (reachable && !wasReachable) onBackOnline()
        }
    }

    private fun onBackOnline() {
        // Enqueue an immediate progress push when we regain connectivity to the server.
        // This ensures offline progress is uploaded as soon as possible.
        try {
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "progress_push_on_reconnect",
                androidx.work.ExistingWorkPolicy.REPLACE,
                androidx.work.OneTimeWorkRequestBuilder<com.narratome.sync.ProgressPushWorker>()
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                            .build()
                    )
                    .build(),
            )
        } catch (e: Exception) {
            android.util.Log.e("Reachability", "Failed to enqueue progress push on reconnect", e)
        }
    }
}

/** Separate cancellable requests also bound DNS lookup time before trying the other endpoint. */
internal suspend fun probeServer(api: AudiobookshelfApi, resolver: ServerBaseUrlResolver, timeoutMs: Long = 5_000): Boolean {
    val first = resolver.selected() ?: return false
    for (endpoint in listOfNotNull(first, resolver.alternate(first))) {
        try {
            if (withTimeout(timeoutMs) { api.ping(endpoint) }.success != false) return true
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            // The next endpoint gets its own deadline.
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Try the other configured endpoint if automatic selection permits it.
        }
    }
    return false
}

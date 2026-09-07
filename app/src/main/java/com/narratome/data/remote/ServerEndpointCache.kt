package com.narratome.data.remote

import com.narratome.data.local.preferences.AppPreferencesRepository
import com.narratome.di.ApplicationScope
import com.narratome.domain.model.EndpointMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory mirror of server URL preferences so OkHttp interceptors never block on DataStore.
 */
data class ServerEndpointPrefsSnapshot(
    val primaryUrl: String,
    val secondaryUrl: String,
    val endpointMode: EndpointMode,
    val homeSsid: String,
) {
    companion object {
        val Initial = ServerEndpointPrefsSnapshot("", "", EndpointMode.AUTO, "")
    }
}

@Singleton
class ServerEndpointCache @Inject constructor(
    private val preferences: AppPreferencesRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val snapshot = AtomicReference(ServerEndpointPrefsSnapshot.Initial)

    init {
        // One blocking read so OkHttp never sees empty prefs before the collector emits.
        runBlocking(Dispatchers.IO) {
            snapshot.set(prefsSnapshotFlow().first())
        }
        scope.launch {
            prefsSnapshotFlow().collect { snap -> snapshot.set(snap) }
        }
    }

    private fun prefsSnapshotFlow() = combine(
        preferences.primaryUrl,
        preferences.secondaryUrl,
        preferences.endpointMode,
        preferences.homeSsid,
    ) { primary, secondary, mode, homeSsid ->
        ServerEndpointPrefsSnapshot(
            primaryUrl = primary.trim(),
            secondaryUrl = secondary.trim(),
            endpointMode = mode,
            homeSsid = homeSsid.trim(),
        )
    }

    fun current(): ServerEndpointPrefsSnapshot = snapshot.get()
}

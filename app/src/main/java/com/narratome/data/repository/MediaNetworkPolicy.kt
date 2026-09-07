package com.narratome.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.narratome.data.local.preferences.AppPreferencesRepository
import com.narratome.data.remote.ServerBaseUrlResolver
import com.narratome.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class MediaNetworkState(
    val connected: Boolean = false,
    val metered: Boolean = true,
    val alwaysAllow: Boolean = false,
    val generation: Long = 0,
) {
    val unrestricted: Boolean get() = connected && (!metered || alwaysAllow)
}

data class MeteredAction(val key: String, val title: String, val generation: Long)

/** Consent belongs to one action and one network generation, never to all media. */
class MediaConsent {
    private val grants = mutableMapOf<String, Long>()
    @Synchronized fun allow(key: String, state: MediaNetworkState) { grants[key] = state.generation }
    @Synchronized fun revoke(key: String) { grants.remove(key) }
    @Synchronized fun clear() { grants.clear() }
    @Synchronized fun allowed(key: String, state: MediaNetworkState): Boolean =
        state.connected && (state.unrestricted || grants[key] == state.generation)
}

@Singleton
class MediaNetworkPolicy @Inject constructor(
    @ApplicationContext context: Context,
    preferences: AppPreferencesRepository,
    private val resolver: ServerBaseUrlResolver,
    @ApplicationScope scope: CoroutineScope,
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val consent = MediaConsent()
    private val mutableState = MutableStateFlow(MediaNetworkState())
    val state = mutableState.asStateFlow()
    private val mutablePending = MutableStateFlow<List<MeteredAction>>(emptyList())
    val pending = mutablePending.asStateFlow()
    private val continuations = mutableMapOf<String, () -> Unit>()
    private var network: Network? = null
    private val ready = kotlinx.coroutines.CompletableDeferred<Unit>()

    init {
        refresh()
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh()
            override fun onLost(network: Network) = refresh()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
        })
        scope.launch {
            preferences.alwaysAllowMetered.collect { allow ->
                synchronized(this@MediaNetworkPolicy) {
                    val old = mutableState.value
                    if (old.alwaysAllow != allow) {
                        consent.clear()
                        mutableState.value = old.copy(alwaysAllow = allow, generation = old.generation + 1)
                    }
                    ready.complete(Unit)
                }
            }
        }
    }

    @Synchronized fun refresh() {
        val active = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(active)
        val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true
        val old = mutableState.value
        if (network != active || old.connected != connected || old.metered != metered) {
            network = active
            consent.clear()
            resolver.reset()
            mutableState.value = old.copy(connected = connected, metered = metered, generation = old.generation + 1)
        }
    }

    fun allowed(key: String): Boolean { refresh(); return consent.allowed(key, state.value) }
    suspend fun awaitReady() = ready.await()
    fun revoke(key: String) { consent.revoke(key); dismiss(key) }

    @Synchronized fun request(key: String, title: String, proceed: () -> Unit) {
        refresh()
        if (consent.allowed(key, state.value)) { proceed(); return }
        continuations[key] = proceed
        mutablePending.value = mutablePending.value.filterNot { it.key == key } +
            MeteredAction(key, title, state.value.generation)
    }

    @Synchronized fun acknowledge(key: String) {
        refresh()
        val action = mutablePending.value.firstOrNull { it.key == key } ?: return
        if (!state.value.connected) return
        if (action.generation != state.value.generation && !state.value.unrestricted) {
            mutablePending.value = mutablePending.value.map {
                if (it.key == key) it.copy(generation = state.value.generation) else it
            }
            return
        }
        val proceed = continuations[key]
        dismiss(key)
        consent.allow(key, state.value)
        proceed?.invoke()
    }

    @Synchronized fun dismiss(key: String) {
        continuations.remove(key)
        mutablePending.value = mutablePending.value.filterNot { it.key == key }
    }
}

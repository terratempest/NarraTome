package com.narratome.presentation.server

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narratome.data.local.auth.TokenStore
import com.narratome.data.local.db.CatalogSyncStateEntity
import com.narratome.data.local.preferences.AppPreferencesRepository
import com.narratome.data.repository.AuthRepository
import com.narratome.data.repository.CatalogSyncRunner
import com.narratome.data.repository.ItemRepository
import com.narratome.data.repository.SelectedLibraryRepository
import com.narratome.data.repository.ServerReachabilityRepository
import com.narratome.domain.model.EndpointMode
import com.narratome.sync.BackgroundWorkScheduler
import com.narratome.util.ErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

data class EndpointTestResult(
    val label: String,
    val ok: Boolean,
    val message: String,
    val checkedAtEpochMs: Long = System.currentTimeMillis(),
)

data class ServerUiState(
    val requireHttps: Boolean = false,
    val primaryUrl: String = "",
    val secondaryUrl: String = "",
    val homeSsid: String = "",
    val endpointMode: EndpointMode = EndpointMode.AUTO,
    val username: String = "",
    val password: String = "",
    val message: String? = null,
    val busy: Boolean = false,
    val testingPrimary: Boolean = false,
    val testingSecondary: Boolean = false,
    val reauthorizing: Boolean = false,
    val syncingNow: Boolean = false,
    val serverReachable: Boolean = false,
    val catalogSyncRunning: Boolean = false,
    val selectedLibraryId: String? = null,
    val selectedLibraryName: String? = null,
    val lastSuccessfulFullSyncAtEpochMs: Long? = null,
    val lastDeltaSyncAtEpochMs: Long? = null,
    val lastSyncStartedAtEpochMs: Long? = null,
    val lastSyncCompletedAtEpochMs: Long? = null,
    val lastSyncPhase: String? = null,
    val lastSyncError: String? = null,
    val primaryTestResult: EndpointTestResult? = null,
    val secondaryTestResult: EndpointTestResult? = null,
    val credentialStatus: String? = null,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ServerViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val authRepository: AuthRepository,
    private val preferences: AppPreferencesRepository,
    private val workScheduler: BackgroundWorkScheduler,
    private val tokenStore: TokenStore,
    private val reachabilityRepository: ServerReachabilityRepository,
    private val selectedLibraryRepository: SelectedLibraryRepository,
    private val itemRepository: ItemRepository,
    private val catalogSyncRunner: CatalogSyncRunner,
) : ViewModel() {

    private val _ui = MutableStateFlow(ServerUiState())
    val ui: StateFlow<ServerUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.requireHttps.collect { required ->
                _ui.update { it.copy(requireHttps = required) }
            }
        }
        viewModelScope.launch {
            combine(
                preferences.primaryUrl,
                preferences.secondaryUrl,
                preferences.homeSsid,
                preferences.endpointMode,
            ) { primary, secondary, ssid, mode ->
                EndpointData(primary, secondary, ssid, mode)
            }.collect { data ->
                _ui.update {
                    it.copy(
                        primaryUrl = data.primary,
                        secondaryUrl = data.secondary,
                        homeSsid = data.ssid,
                        endpointMode = data.mode,
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(tokenStore.username, tokenStore.token) { user, token ->
                CredentialData(user, token)
            }.collect { data ->
                _ui.update {
                    it.copy(
                        // Initialize credentials from store if they are currently empty
                        username = it.username.ifBlank { data.user.orEmpty() },
                        password = if (it.password.isBlank() && data.token != null) "*****" else it.password
                    )
                }
            }
        }
        viewModelScope.launch {
            reachabilityRepository.serverReachable.collect { reachable ->
                _ui.update { it.copy(serverReachable = reachable) }
            }
        }
        viewModelScope.launch {
            catalogSyncRunner.syncRunning.collect { running ->
                _ui.update { it.copy(catalogSyncRunning = running) }
            }
        }
        viewModelScope.launch {
            combine(
                selectedLibraryRepository.selectedLibraryId,
                reachabilityRepository.serverReachable,
            ) { libraryId, reachable -> libraryId to reachable }
                .distinctUntilChanged()
                .collectLatest { (libraryId, reachable) ->
                    paintSelectedLibrary(libraryId, reachable)
                }
        }
        viewModelScope.launch {
            selectedLibraryRepository.selectedLibraryId
                .distinctUntilChanged()
                .flatMapLatest { libraryId ->
                    if (libraryId.isNullOrBlank()) flowOf(null) else itemRepository.observeSyncState(libraryId)
                }
                .collect { state ->
                    _ui.update { it.withSyncState(state) }
                }
        }
    }

    private data class EndpointData(
        val primary: String,
        val secondary: String,
        val ssid: String,
        val mode: EndpointMode,
    )

    private data class CredentialData(
        val user: String?,
        val token: String?
    )

    fun onPrimaryChange(v: String) {
        _ui.update { it.copy(primaryUrl = v) }
    }

    fun setRequireHttps(required: Boolean) {
        viewModelScope.launch { preferences.setRequireHttps(required) }
    }

    fun onSecondaryChange(v: String) {
        _ui.update { it.copy(secondaryUrl = v) }
    }

    fun onSsidChange(v: String) {
        _ui.update { it.copy(homeSsid = v) }
    }

    fun setEndpointMode(mode: EndpointMode) {
        _ui.update { it.copy(endpointMode = mode) }
    }

    fun onUsernameChange(v: String) {
        _ui.update {
            // If username changes and password was masked, clear password to force re-entry
            val nextPassword = if (it.password == "*****") "" else it.password
            it.copy(username = v, password = nextPassword)
        }
    }

    fun onPasswordChange(v: String) {
        _ui.update {
            // Handle clearing the masked password when the user starts typing
            val nextValue = if (it.password == "*****" && v != "*****") {
                if (v.startsWith("*****")) v.substring(5) else v
            } else {
                v
            }
            it.copy(password = nextValue)
        }
    }

    fun saveAndSync() {
        val currentState = _ui.value
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null) }

            // 1. Save Server URLs
            preferences.setServerConfig(
                primaryUrl = currentState.primaryUrl,
                secondaryUrl = currentState.secondaryUrl,
                homeSsid = currentState.homeSsid,
                endpointMode = currentState.endpointMode,
            )

            // 2. Auth / Re-create credentials
            val r = if (currentState.password == "*****") {
                // Password is still masked, try to authorize with existing token
                authRepository.authorize()
            } else {
                // Password changed or new, perform full login (this will overwrite/re-create credentials)
                authRepository.login(currentState.username, currentState.password)
            }

            // 3. Trigger sync if successful
            _ui.update {
                it.copy(
                    busy = false,
                    message = r.fold(
                        onSuccess = {
                            workScheduler.schedulePeriodicMaintenance()
                            workScheduler.enqueueImmediatePullAndBookmarks()
                            workScheduler.enqueueSyncWork()
                            reachabilityRepository.refresh()
                            "Saved and synced"
                        },
                        onFailure = { e -> ErrorMapper.map(appContext, e) },
                    ),
                )
            }
        }
    }

    fun testPrimary() {
        testEndpoint(_ui.value.primaryUrl.trim().trimEnd('/'), "Primary") { state, result ->
            state.copy(testingPrimary = false, primaryTestResult = result, message = result.message)
        }
    }

    fun testBackup() {
        testEndpoint(_ui.value.secondaryUrl.trim().trimEnd('/'), "Backup") { state, result ->
            state.copy(testingSecondary = false, secondaryTestResult = result, message = result.message)
        }
    }

    fun pingPrimary() {
        testPrimary()
    }

    fun reauthorize() {
        viewModelScope.launch {
            _ui.update { it.copy(reauthorizing = true, message = null, credentialStatus = "Checking credentials...") }
            val result = authRepository.authorize()
            _ui.update {
                it.copy(
                    reauthorizing = false,
                    credentialStatus = result.fold(
                        onSuccess = { "Credentials verified" },
                        onFailure = { "Credential check failed" },
                    ),
                    message = result.fold(
                        onSuccess = { "Credentials verified" },
                        onFailure = { e -> ErrorMapper.map(appContext, e) },
                    ),
                )
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _ui.update { it.copy(syncingNow = true, message = null) }
            workScheduler.schedulePeriodicMaintenance()
            workScheduler.enqueueImmediatePullAndBookmarks()
            workScheduler.enqueueProgressPush()
            _ui.update {
                it.copy(
                    syncingNow = false,
                    message = "Sync requested",
                )
            }
        }
    }

    private fun testEndpoint(
        url: String,
        label: String,
        updateResult: (ServerUiState, EndpointTestResult) -> ServerUiState,
    ) {
        if (url.isBlank()) {
            _ui.update { it.copy(message = "Enter ${label.lowercase()} URL first") }
            return
        }
        viewModelScope.launch {
            _ui.update {
                if (label == "Primary") {
                    it.copy(testingPrimary = true, message = null)
                } else {
                    it.copy(testingSecondary = true, message = null)
                }
            }
            val checkedAt = System.currentTimeMillis()
            val result = runCatching {
                val req = Request.Builder().url("$url/ping").get().build()
                withTimeout(5_000L) {
                    withContext(Dispatchers.IO) {
                        OkHttpClient().newCall(req).execute().use { response ->
                            EndpointTestResult(
                                label = label,
                                ok = response.isSuccessful,
                                message = if (response.isSuccessful) {
                                    "$label endpoint is reachable"
                                } else {
                                    "$label endpoint returned HTTP ${response.code}"
                                },
                                checkedAtEpochMs = checkedAt,
                            )
                        }
                    }
                }
            }.getOrElse { e ->
                EndpointTestResult(
                    label = label,
                    ok = false,
                    message = "$label endpoint failed: ${e.message ?: e::class.java.simpleName}",
                    checkedAtEpochMs = checkedAt,
                )
            }
            _ui.update {
                updateResult(it, result)
            }
        }
    }

    private suspend fun paintSelectedLibrary(libraryId: String?, reachable: Boolean) {
        if (libraryId.isNullOrBlank()) {
            _ui.update { it.copy(selectedLibraryId = null, selectedLibraryName = null) }
            return
        }
        val name = if (reachable) {
            selectedLibraryRepository.loadBookLibraries()
                .getOrNull()
                .orEmpty()
                .firstOrNull { it.id == libraryId }
                ?.name
                ?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        _ui.update {
            it.copy(
                selectedLibraryId = libraryId,
                selectedLibraryName = name ?: "Cached library",
            )
        }
    }

    private fun ServerUiState.withSyncState(state: CatalogSyncStateEntity?): ServerUiState =
        copy(
            lastSuccessfulFullSyncAtEpochMs = state?.lastSuccessfulFullSyncAtEpochMs,
            lastDeltaSyncAtEpochMs = state?.lastDeltaSyncAtEpochMs,
            lastSyncStartedAtEpochMs = state?.lastSyncStartedAtEpochMs,
            lastSyncCompletedAtEpochMs = state?.lastSyncCompletedAtEpochMs,
            lastSyncPhase = state?.lastSyncPhase,
            lastSyncError = state?.lastSyncError,
        )
}

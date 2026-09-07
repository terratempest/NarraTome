package com.narratome.presentation.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narratome.data.local.preferences.AppPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StartupPhase {
    /** Waiting for first DataStore read */
    Loading,

    /** No primary server URL saved yet — show setup */
    NeedsServerSetup,

    /** Primary URL configured — main app */
    Ready,
}

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val preferences: AppPreferencesRepository,
) : ViewModel() {

    private val _phase = MutableStateFlow(StartupPhase.Loading)
    val startupPhase: StateFlow<StartupPhase> = _phase.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.primaryUrl.collect { url ->
                _phase.update {
                    if (url.isBlank()) StartupPhase.NeedsServerSetup else StartupPhase.Ready
                }
            }
        }
    }
}

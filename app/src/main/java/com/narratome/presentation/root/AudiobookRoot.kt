package com.narratome.presentation.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narratome.presentation.server.ServerScreen

@Composable
fun AudiobookRoot() {
    val startupVm: StartupViewModel = hiltViewModel()
    val phase by startupVm.startupPhase.collectAsStateWithLifecycle()

    when (phase) {
        StartupPhase.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        StartupPhase.NeedsServerSetup -> {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                ServerScreen(setupMode = true)
            }
        }
        StartupPhase.Ready -> {
            AudiobookMainShell()
        }
    }
}

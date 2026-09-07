package com.narratome.presentation.root

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.narratome.R
import com.narratome.data.repository.MeteredAction
import com.narratome.data.repository.MediaNetworkState

@Composable
fun MeteredMediaDialog(action: MeteredAction, network: MediaNetworkState, acknowledge: () -> Unit,
                      dismiss: () -> Unit) {
    val download = action.key.startsWith("download:")
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Text(stringResource(if (download) R.string.metered_download_title else R.string.metered_playback_title))
        },
        text = {
            val message = when {
                !network.connected -> R.string.media_waiting_network
                network.unrestricted -> R.string.media_ready
                download -> R.string.metered_download_message
                else -> R.string.metered_media_acknowledgement
            }
            Text(stringResource(message, action.title))
        },
        confirmButton = {
            TextButton(onClick = acknowledge, enabled = network.connected) {
                Text(stringResource(if (download) R.string.download_now else R.string.media_approve))
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss) {
                Text(stringResource(if (download) R.string.download_when_unmetered else R.string.media_cancel))
            }
        },
    )
}

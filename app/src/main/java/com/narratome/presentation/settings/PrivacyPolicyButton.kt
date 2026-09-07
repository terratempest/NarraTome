package com.narratome.presentation.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.narratome.R

@Composable
fun PrivacyPolicyButton() {
    val resources = LocalResources.current
    val uriHandler = LocalUriHandler.current
    var visible by remember { mutableStateOf(false) }
    TextButton(onClick = { visible = true }) { Text("Privacy policy") }
    if (visible) {
        val contact = stringResource(R.string.developer_contact)
        val text = remember(resources, contact) {
            resources.openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readText() }
                .replace("{developer_contact}", contact.ifBlank {
            "Contact the maintainers through the NarraTome GitHub repository where you downloaded this app. Never post credentials or private server details."
                })
        }
        val url = stringResource(R.string.privacy_policy_url)
        AlertDialog(
            onDismissRequest = { visible = false },
            title = { Text("Privacy policy") },
            text = {
                SelectionContainer {
                    Text(text, modifier = Modifier.verticalScroll(rememberScrollState()))
                }
            },
            confirmButton = { TextButton(onClick = { visible = false }) { Text("Close") } },
            dismissButton = {
                if (url.isNotBlank()) TextButton(onClick = { uriHandler.openUri(url) }) { Text("Public policy") }
            },
        )
    }
}

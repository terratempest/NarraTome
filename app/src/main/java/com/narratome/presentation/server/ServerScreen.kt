package com.narratome.presentation.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narratome.domain.model.EndpointMode
import java.text.DateFormat
import java.util.Date

@Composable
fun ServerScreen(
    modifier: Modifier = Modifier,
    setupMode: Boolean = false,
    viewModel: ServerViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    if (setupMode) {
        ServerSetupScreen(
            ui = ui,
            viewModel = viewModel,
            modifier = modifier,
        )
    } else {
        ServerDashboardScreen(
            ui = ui,
            viewModel = viewModel,
            modifier = modifier,
        )
    }
}

@Composable
private fun ServerSetupScreen(
    ui: ServerUiState,
    viewModel: ServerViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.height(40.dp))

        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Public,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Welcome to NarraTome",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Enter your Audiobookshelf address and account. You can add a backup URL for failover later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        ServerConfigFields(ui = ui, viewModel = viewModel, showAdvanced = false)
        SaveButton(ui = ui, onClick = viewModel::saveAndSync)
        ServerMessage(ui.message)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ServerDashboardScreen(
    ui: ServerUiState,
    viewModel: ServerViewModel,
    modifier: Modifier = Modifier,
) {
    var showAdvanced by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatusCard(ui)

        DashboardCard(
            title = "Endpoints",
            icon = Icons.Filled.Public,
            trailing = {
                EndpointModeDropdown(
                    selected = ui.endpointMode,
                    onSelected = viewModel::setEndpointMode,
                )
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ServerTextField(
                    value = ui.primaryUrl,
                    onValueChange = viewModel::onPrimaryChange,
                    label = "Primary server URL",
                    placeholder = "http://192.168.1.10:13378",
                )
                EndpointTestRow(
                    label = "Primary",
                    url = ui.primaryUrl,
                    testing = ui.testingPrimary,
                    result = ui.primaryTestResult,
                    onTest = viewModel::testPrimary,
                )
                ServerTextField(
                    value = ui.secondaryUrl,
                    onValueChange = viewModel::onSecondaryChange,
                    label = "Backup server URL",
                    placeholder = "Same library on another host or tailnet URL",
                )
                EndpointTestRow(
                    label = "Backup",
                    url = ui.secondaryUrl,
                    testing = ui.testingSecondary,
                    result = ui.secondaryTestResult,
                    onTest = viewModel::testBackup,
                )
                HttpsSetting(ui, viewModel::setRequireHttps)
                SaveButton(ui = ui, onClick = viewModel::saveAndSync)
            }
        }

        DashboardCard(title = "Account", icon = Icons.Filled.AccountCircle) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                InfoRow(label = "Signed in as", value = ui.username.ifBlank { "Not configured" })
                ui.credentialStatus?.let { StatusLine(text = it, success = !it.contains("failed", ignoreCase = true)) }
                ServerTextField(
                    value = ui.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = "Username",
                )
                ServerTextField(
                    value = ui.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = "Password",
                    isPassword = true,
                )
                ActionButton(
                    text = "Reauthorize",
                    icon = Icons.Filled.Key,
                    busy = ui.reauthorizing,
                    enabled = ui.primaryUrl.isNotBlank() && ui.username.isNotBlank(),
                    onClick = viewModel::reauthorize,
                )
            }
        }

        DashboardCard(title = "Sync", icon = Icons.Filled.Sync) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow(label = "Library", value = ui.selectedLibraryName ?: ui.selectedLibraryId ?: "No library selected")
                InfoRow(label = "State", value = syncStateText(ui))
                InfoRow(label = "Last full sync", value = formatEpoch(ui.lastSuccessfulFullSyncAtEpochMs))
                InfoRow(label = "Last delta sync", value = formatEpoch(ui.lastDeltaSyncAtEpochMs))
                ui.lastSyncError?.takeIf { it.isNotBlank() }?.let {
                    StatusLine(text = it, success = false)
                }
                ActionButton(
                    text = "Sync now",
                    icon = Icons.Filled.Sync,
                    busy = ui.syncingNow || ui.catalogSyncRunning,
                    enabled = ui.serverReachable,
                    onClick = viewModel::syncNow,
                )
            }
        }

        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(if (showAdvanced) "Hide advanced diagnostics" else "Show advanced diagnostics")
        }
        if (showAdvanced) {
            DashboardCard(title = "Diagnostics", icon = Icons.Filled.Wifi) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ServerTextField(
                        value = ui.homeSsid,
                        onValueChange = viewModel::onSsidChange,
                        label = "Home Wi-Fi SSID",
                        placeholder = "Used with Auto endpoint mode",
                    )
                    InfoRow(label = "Normalized primary", value = normalizeUrl(ui.primaryUrl))
                    InfoRow(label = "Normalized backup", value = normalizeUrl(ui.secondaryUrl))
                    InfoRow(label = "Failover", value = "Backup is tried automatically after primary connection errors or 502/503/504 responses.")
                    val latest = listOfNotNull(ui.primaryTestResult, ui.secondaryTestResult)
                        .maxByOrNull { it.checkedAtEpochMs }
                    InfoRow(label = "Last endpoint test", value = latest?.message ?: "Not tested yet")
                }
            }
        }

        ServerMessage(ui.message)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StatusCard(ui: ServerUiState) {
    val connected = ui.serverReachable
    val syncing = ui.catalogSyncRunning
    val statusColor = when {
        syncing -> Color(0xFFFFA000)
        connected -> Color(0xFF2E7D32)
        else -> Color(0xFFC62828)
    }
    val statusText = when {
        syncing -> "Syncing"
        connected -> "Connected"
        else -> "Offline"
    }
    val icon = when {
        connected || syncing -> Icons.Filled.CheckCircle
        else -> Icons.Filled.CloudOff
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(statusColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = statusColor)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Mode: ${endpointModeLabel(ui.endpointMode)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = ui.selectedLibraryName ?: "No library",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DashboardCard(
    title: String,
    icon: ImageVector,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun ServerConfigFields(
    ui: ServerUiState,
    viewModel: ServerViewModel,
    showAdvanced: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HttpsSetting(ui, viewModel::setRequireHttps)
        ServerTextField(
            value = ui.primaryUrl,
            onValueChange = viewModel::onPrimaryChange,
            label = "Primary server URL",
            placeholder = "http://192.168.1.10:13378",
        )
        ServerTextField(
            value = ui.secondaryUrl,
            onValueChange = viewModel::onSecondaryChange,
            label = "Backup server URL (optional)",
            placeholder = "Same library on another host or tailnet URL",
        )
        ServerTextField(
            value = ui.username,
            onValueChange = viewModel::onUsernameChange,
            label = "Username",
        )
        ServerTextField(
            value = ui.password,
            onValueChange = viewModel::onPasswordChange,
            label = "Password",
            isPassword = true,
        )
        if (showAdvanced) {
            ServerTextField(
                value = ui.homeSsid,
                onValueChange = viewModel::onSsidChange,
                label = "Home Wi-Fi SSID",
                placeholder = "Used with Auto endpoint mode",
            )
        }
    }
}

@Composable
private fun HttpsSetting(ui: ServerUiState, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        com.narratome.presentation.settings.PrivacyPolicyButton()
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Require HTTPS", modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(
                checked = ui.requireHttps,
                onCheckedChange = onChange,
                modifier = Modifier.semantics { contentDescription = "Require HTTPS" },
            )
        }
        Text("HTTP traffic is unencrypted. Use HTTPS across untrusted networks.",
            style = MaterialTheme.typography.bodySmall)
        if (ui.requireHttps) {
            val blocked = listOf("Primary" to ui.primaryUrl, "Backup" to ui.secondaryUrl)
                .filter { it.second.trim().startsWith("http://", ignoreCase = true) }
                .joinToString { it.first }
            Text(if (blocked.isEmpty()) "HTTP media and redirects are blocked." else
                "$blocked blocked: change to HTTPS or turn off Require HTTPS. Saved data is retained.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EndpointModeDropdown(
    selected: EndpointMode,
    onSelected: (EndpointMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(endpointModeLabel(selected))
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            EndpointMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(endpointModeLabel(mode)) },
                    onClick = {
                        expanded = false
                        onSelected(mode)
                    },
                )
            }
        }
    }
}

@Composable
private fun EndpointTestRow(
    label: String,
    url: String,
    testing: Boolean,
    result: EndpointTestResult?,
    onTest: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (url.isBlank()) "$label endpoint not configured" else normalizeUrl(url),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            result?.let {
                StatusLine(text = it.message, success = it.ok)
            }
        }
        OutlinedButton(
            onClick = onTest,
            enabled = !testing && url.isNotBlank(),
            shape = RoundedCornerShape(8.dp),
        ) {
            if (testing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Test")
            }
        }
    }
}

@Composable
private fun SaveButton(ui: ServerUiState, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(8.dp),
        enabled = !ui.busy,
    ) {
        if (ui.busy) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
        } else {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Save and sync", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled && !busy,
        shape = RoundedCornerShape(8.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ServerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    isPassword: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            ),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun StatusLine(text: String, success: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (success) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = if (success) Color(0xFF2E7D32) else Color(0xFFC62828),
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (success) Color(0xFF2E7D32) else Color(0xFFC62828),
        )
    }
}

@Composable
private fun ServerMessage(message: String?) {
    message?.let {
        val isError = it.contains("failed", true) || it.contains("error", true)
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) Color(0xFFC62828) else MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

private fun endpointModeLabel(mode: EndpointMode): String =
    when (mode) {
        EndpointMode.AUTO -> "Auto"
        EndpointMode.PRIMARY -> "Primary"
        EndpointMode.SECONDARY -> "Backup"
    }

private fun syncStateText(ui: ServerUiState): String =
    when {
        ui.catalogSyncRunning -> "Syncing ${ui.lastSyncPhase ?: ""}".trim()
        ui.lastSyncError != null -> "Last sync failed"
        ui.lastSyncPhase != null -> ui.lastSyncPhase.replace('_', ' ')
        else -> "Not synced yet"
    }

private fun normalizeUrl(url: String): String =
    url.trim().trimEnd('/').ifBlank { "Not configured" }

private fun formatEpoch(epochMs: Long?): String =
    epochMs?.takeIf { it > 0L }?.let {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
    } ?: "Never"

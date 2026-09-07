package com.narratome.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narratome.data.local.preferences.PLAYBACK_NOTIFICATION_RETENTION_MINUTES
import com.narratome.domain.model.SyncConflictPolicy

private data class SettingHelp(
    val title: String,
    val body: String,
    val warning: String? = null,
)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var activeHelp by remember { mutableStateOf<SettingHelp?>(null) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }

    activeHelp?.let { help ->
        SettingInfoDialog(help = help, onDismiss = { activeHelp = null })
    }

    confirmAction?.let { action ->
        ConfirmActionDialog(
            action = action,
            onDismiss = { confirmAction = null },
            onConfirm = {
                confirmAction = null
                when (action) {
                    ConfirmAction.ClearCoverCache -> viewModel.clearCoverCache()
                    ConfirmAction.ClearFailedDownloads -> viewModel.clearFailedDownloadRecords()
                }
            },
        )
    }

    ui.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            confirmButton = {
                TextButton(onClick = viewModel::clearMessage) { Text("OK") }
            },
            title = { Text("Settings") },
            text = { Text(message) },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PrivacyPolicyButton()
        SettingsGroup(title = "Playback", icon = Icons.Filled.PlayArrow) {
            StepperSettingRow(
                title = "Seek back",
                value = ui.seekBackSeconds,
                step = 5,
                min = 5,
                max = 120,
                suffix = "s",
                onValueChange = viewModel::setSeekBackSeconds,
                onInfo = { activeHelp = SettingsHelp.seekBack },
            )
            StepperSettingRow(
                title = "Seek forward",
                value = ui.seekForwardSeconds,
                step = 5,
                min = 5,
                max = 120,
                suffix = "s",
                onValueChange = viewModel::setSeekForwardSeconds,
                onInfo = { activeHelp = SettingsHelp.seekForward },
            )
            StepperSettingRow(
                title = "Sleep fade",
                value = ui.sleepTimerFadeSeconds,
                step = 15,
                min = 0,
                max = 180,
                suffix = "s",
                onValueChange = viewModel::setSleepTimerFadeSeconds,
                onInfo = { activeHelp = SettingsHelp.sleepFade },
            )
            DropdownSettingRow(
                title = "Paused notification",
                options = PLAYBACK_NOTIFICATION_RETENTION_MINUTES,
                selected = ui.playbackNotificationRetentionMinutes,
                label = ::retentionMinutesLabel,
                onSelected = viewModel::setPlaybackNotificationRetentionMinutes,
                onInfo = { activeHelp = SettingsHelp.pausedNotification },
            )
            SwitchSettingRow(
                title = "Shake to extend sleep timer",
                checked = ui.sleepTimerShakeToExtend,
                onCheckedChange = viewModel::setSleepTimerShakeToExtend,
                onInfo = { activeHelp = SettingsHelp.shakeToExtend },
            )
        }

        SettingsGroup(title = "Sync", icon = Icons.Filled.Sync) {
            DropdownSettingRow(
                title = "Progress conflicts",
                options = SyncConflictPolicy.entries,
                selected = ui.policy,
                label = ::syncPolicyLabel,
                onSelected = viewModel::setPolicy,
                onInfo = { activeHelp = SettingsHelp.syncPolicy },
            )
        }

        SettingsGroup(title = "Downloads", icon = Icons.Filled.Download) {
            SwitchSettingRow(
                title = "Always allow playback and downloads on metered connections",
                checked = ui.alwaysAllowMetered,
                onCheckedChange = viewModel::setAlwaysAllowMetered,
                onInfo = { activeHelp = SettingsHelp.metered },
            )
            StepperSettingRow(
                title = "Concurrent books",
                value = ui.downloadMaxParallelBooks,
                step = 1,
                min = 1,
                max = 8,
                suffix = "",
                onValueChange = viewModel::setDownloadMaxParallelBooks,
                onInfo = { activeHelp = SettingsHelp.parallelBooks },
            )
            StepperSettingRow(
                title = "Concurrent part files",
                value = ui.downloadMaxParallelParts,
                step = 1,
                min = 1,
                max = 16,
                suffix = "",
                onValueChange = viewModel::setDownloadMaxParallelParts,
                onInfo = { activeHelp = SettingsHelp.parallelParts },
            )
            SwitchSettingRow(
                title = "Verify sizes and hashes",
                checked = ui.downloadVerifySizes,
                onCheckedChange = viewModel::setDownloadVerifySizes,
                onInfo = { activeHelp = SettingsHelp.verifyDownloads },
            )
            SwitchSettingRow(
                title = "Strict cleanup on failure",
                checked = ui.downloadStrictCleanup,
                onCheckedChange = viewModel::setDownloadStrictCleanup,
                onInfo = { activeHelp = SettingsHelp.strictCleanup },
            )
        }

        AdvancedSettingsGroup(
            ui = ui,
            expanded = advancedExpanded,
            onExpandedChange = { advancedExpanded = it },
            onRefresh = viewModel::refreshDiagnostics,
            onClearCoverCache = { confirmAction = ConfirmAction.ClearCoverCache },
            onClearFailedDownloads = { confirmAction = ConfirmAction.ClearFailedDownloads },
            onInfo = { activeHelp = it },
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingText(
    title: String,
    onInfo: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (onInfo != null) {
            IconButton(onClick = onInfo, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "More information about $title",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onInfo: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingText(title = title, onInfo = onInfo, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StepperSettingRow(
    title: String,
    value: Int,
    step: Int,
    min: Int,
    max: Int,
    suffix: String,
    onValueChange: (Int) -> Unit,
    onInfo: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingText(title = title, onInfo = onInfo, modifier = Modifier.weight(1f))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(
                    onClick = { onValueChange((value - step).coerceAtLeast(min)) },
                    enabled = value > min,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease $title", modifier = Modifier.size(18.dp))
                }
                Text(
                    text = "$value$suffix",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(46.dp),
                    maxLines = 1,
                )
                IconButton(
                    onClick = { onValueChange((value + step).coerceAtMost(max)) },
                    enabled = value < max,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase $title", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun <T> DropdownSettingRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    onInfo: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingText(title = title, onInfo = onInfo, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { expanded = true }, shape = RoundedCornerShape(8.dp)) {
                Text(label(selected))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(label(option)) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadOnlySettingRow(
    title: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onInfo: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingText(title = title, onInfo = onInfo, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = valueColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AdvancedSettingsGroup(
    ui: SettingsUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onClearCoverCache: () -> Unit,
    onClearFailedDownloads: () -> Unit,
    onInfo: (SettingHelp) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Advanced",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Diagnostics and development cleanup tools.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse advanced settings" else "Expand advanced settings",
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                ReadOnlySettingRow(
                    title = "App version",
                    value = ui.appVersion,
                    onInfo = { onInfo(SettingsHelp.appVersion) },
                )
                ReadOnlySettingRow(
                    title = "Cover cache",
                    value = ui.coverCacheSizeLabel,
                    onInfo = { onInfo(SettingsHelp.coverCache) },
                )
                ReadOnlySettingRow(
                    title = "Download records",
                    value = "${ui.activeDownloadCount} active / ${ui.failedDownloadCount} failed",
                    onInfo = { onInfo(SettingsHelp.downloadRecords) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onRefresh,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Refresh")
                    }
                    OutlinedButton(
                        onClick = onClearFailedDownloads,
                        enabled = ui.failedDownloadCount > 0,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Clear failed")
                    }
                }
                Button(
                    onClick = onClearCoverCache,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444).copy(alpha = 0.14f),
                        contentColor = Color(0xFFFF8A80),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clear cover cache", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SettingInfoDialog(help: SettingHelp, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
        title = { Text(help.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(help.body)
                help.warning?.let {
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = it,
                            color = Color(0xFFFF8A80),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun ConfirmActionDialog(
    action: ConfirmAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C))) {
                Text(action.confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text(action.title) },
        text = { Text(action.body) },
    )
}

private enum class ConfirmAction(
    val title: String,
    val body: String,
    val confirmLabel: String,
) {
    ClearCoverCache(
        title = "Clear cover cache?",
        body = "This removes downloaded cover art files. The app can fetch covers again later when the server is reachable.",
        confirmLabel = "Clear cache",
    ),
    ClearFailedDownloads(
        title = "Clear failed download records?",
        body = "This removes failed and cancelled job records from the local queue. It does not delete completed audiobook downloads.",
        confirmLabel = "Clear records",
    ),
}

private object SettingsHelp {
    val seekBack = SettingHelp(
        title = "Seek back",
        body = "Controls how far the app jumps backward when you use NarraTome's rewind controls. Shorter jumps are useful for missed phrases; longer jumps are better when you often lose your place after interruptions.",
    )
    val seekForward = SettingHelp(
        title = "Seek forward",
        body = "Controls how far the app jumps ahead when you use NarraTome's forward controls. This is handy for skipping repeated intros, credits, silence, or sections you have already heard.",
    )
    val sleepFade = SettingHelp(
        title = "Sleep fade",
        body = "Controls how gently playback ends when the sleep timer expires. A longer fade slowly lowers the volume before pausing, while 0 seconds pauses immediately.",
    )
    val shakeToExtend = SettingHelp(
        title = "Shake to extend sleep timer",
        body = "When enabled, shaking the device restarts the current sleep timer duration. It is designed for bedtime listening when you want to keep listening without looking at the screen.",
        warning = "This uses the device accelerometer while a sleep timer is active.",
    )
    val pausedNotification = SettingHelp(
        title = "Paused notification",
        body = "Controls how long Android keeps playback controls available after audio is paused. Media3 supports up to 10 minutes before the service can leave foreground playback.",
    )
    val syncPolicy = SettingHelp(
        title = "Progress conflicts",
        body = "Controls what happens when this device and the server disagree about your listening position. Ask me each time is safest. Prefer server follows the server copy. Prefer this device keeps the local position.",
    )
    val metered = SettingHelp(
        title = "Metered media",
        body = "Unmetered connections are unrestricted. Otherwise each Play, Download or Resume needs acknowledgement. Switching to a metered connection pauses media transfers. Downloads resume automatically on an unmetered connection; playback waits for Play. This switch always allows media on metered connections.",
    )
    val parallelBooks = SettingHelp(
        title = "Concurrent book downloads",
        body = "Controls how many audiobooks may download at the same time. Higher values can fill your device faster on strong networks, but they may use more battery, bandwidth, and server resources.",
    )
    val parallelParts = SettingHelp(
        title = "Concurrent part files",
        body = "Controls how many audio files within a single audiobook can download in parallel. Higher values can speed up multi-file books but may make weak networks less stable.",
    )
    val verifyDownloads = SettingHelp(
        title = "Verify sizes and hashes",
        body = "When enabled, NarraTome checks downloaded files against size or hash metadata when the server provides it. This can catch corrupted files before offline playback.",
    )
    val strictCleanup = SettingHelp(
        title = "Strict cleanup on failure",
        body = "When enabled, a failed fresh download removes partial files. When disabled, partial files may remain so retries or development debugging have more local evidence to work with.",
    )
    val appVersion = SettingHelp(
        title = "App version",
        body = "Shows the version name from the current build. This is mainly useful while comparing behavior across development installs.",
    )
    val coverCache = SettingHelp(
        title = "Cover cache",
        body = "Cover art is cached locally so lists can stay visual while offline and avoid re-downloading the same images repeatedly. Clearing it is safe; covers will be fetched again later.",
    )
    val downloadRecords = SettingHelp(
        title = "Download records",
        body = "Download records describe queued, running, failed, cancelled, and completed jobs in the local database. Clearing failed records is mostly a development and cleanup tool.",
    )
}

private fun syncPolicyLabel(policy: SyncConflictPolicy): String =
    when (policy) {
        SyncConflictPolicy.ALWAYS_ASK -> "Ask each time"
        SyncConflictPolicy.PREFER_SERVER -> "Prefer server"
        SyncConflictPolicy.PREFER_LOCAL -> "Prefer this device"
    }

private fun retentionMinutesLabel(minutes: Int): String =
    if (minutes == 1) "1 minute" else "$minutes minutes"

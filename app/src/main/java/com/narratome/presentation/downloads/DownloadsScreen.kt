package com.narratome.presentation.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.narratome.data.local.db.DownloadJobEntity
import com.narratome.domain.download.DownloadJobState

@Composable
fun DownloadsScreen(
    onOpenBook: (libraryId: String, itemId: String) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val hasAny = ui.activeJobs.isNotEmpty() || ui.failedJobs.isNotEmpty() || ui.completed.isNotEmpty()

    if (!hasAny) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No downloads yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (ui.activeJobs.isNotEmpty()) {
            item {
                SectionHeader("In Progress")
            }
            items(ui.activeJobs, key = { it.job.downloadKey }) { row ->
                ActiveDownloadCard(
                    row = row,
                    onCancel = { viewModel.cancelDownload(row.job.libraryItemId, row.job.episodeId) },
                    onResume = { viewModel.retryDownload(row.job.libraryItemId, row.job.title, row.job.episodeId) },
                    onOpenBook = onOpenBook,
                )
            }
        }

        if (ui.failedJobs.isNotEmpty()) {
            item {
                SectionHeader("Failed / Cancelled")
            }
            items(ui.failedJobs, key = { it.job.downloadKey }) { row ->
                FailedDownloadCard(
                    row = row,
                    onRetry = { viewModel.retryDownload(row.job.libraryItemId, row.job.title, row.job.episodeId) },
                    onClear = { viewModel.clearJob(row.job.libraryItemId, row.job.episodeId) },
                    onOpenBook = onOpenBook,
                )
            }
        }

        if (ui.completed.isNotEmpty()) {
            item {
                SectionHeader("Downloaded")
            }
            items(ui.completed, key = { it.libraryItemId }) { item ->
                CompletedDownloadCard(
                    item = item,
                    onDelete = { viewModel.deleteDownload(item.libraryItemId) },
                    onOpenBook = onOpenBook,
                )
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun CoverThumb(model: Any?, contentDescription: String?, modifier: Modifier = Modifier.size(48.dp)) {
    val shape = RoundedCornerShape(4.dp)
    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
        )
    } else {
        Box(
            modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

private fun downloadPercentText(job: DownloadJobEntity): String? =
    when (job.state) {
        DownloadJobState.QUEUED -> "0%"
        DownloadJobState.RUNNING -> when {
            job.bytesTotal > 0 -> {
                val p = (100.0 * job.bytesDownloadedTotal / job.bytesTotal).toInt().coerceIn(0, 100)
                "$p%"
            }
            job.totalParts > 0 -> {
                val p = (100.0 * job.currentPartIndex / job.totalParts).toInt().coerceIn(0, 99)
                "$p%"
            }
            else -> null
        }
        else -> null
    }

@Composable
private fun ActiveDownloadCard(
    row: ActiveDownloadRow,
    onCancel: () -> Unit,
    onResume: () -> Unit,
    onOpenBook: (libraryId: String, itemId: String) -> Unit,
) {
    val job = row.job
    val libraryId = row.libraryId
    val openModifier: Modifier = if (libraryId != null) {
        Modifier.clickable { onOpenBook(libraryId, job.libraryItemId) }
    } else {
        Modifier
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(openModifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverThumb(row.coverModel, job.title ?: job.libraryItemId)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.title ?: job.libraryItemId,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                val statusText = when (job.state) {
                    DownloadJobState.PAUSED_NETWORK -> androidx.compose.ui.res.stringResource(com.narratome.R.string.download_waiting_unmetered)
                    DownloadJobState.QUEUED -> "Pending"
                    DownloadJobState.RUNNING -> if (job.totalParts > 0) {
                        "Part ${job.currentPartIndex + 1} of ${job.totalParts}"
                    } else {
                        "Downloading…"
                    }
                    else -> job.state
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                row.sizeSubtitle?.let { sub ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (job.state == DownloadJobState.PAUSED_NETWORK) {
                    androidx.compose.material3.TextButton(onClick = onResume) {
                        Text(androidx.compose.ui.res.stringResource(com.narratome.R.string.download_resume_metered))
                    }
                }
                val pct = downloadPercentText(job)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (pct != null) {
                        Text(
                            text = pct,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (job.state == DownloadJobState.RUNNING && job.bytesTotal > 0) {
                        LinearProgressIndicator(
                            progress = {
                                (job.bytesDownloadedTotal.toFloat() / job.bytesTotal).coerceIn(0f, 1f)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                    } else if (job.state != DownloadJobState.PAUSED_NETWORK) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                    }
                }
            }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Cancel, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun FailedDownloadCard(
    row: FailedDownloadRow,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    onOpenBook: (libraryId: String, itemId: String) -> Unit,
) {
    val job = row.job
    val libraryId = row.libraryId

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = if (libraryId != null) {
                        Modifier.clickable { onOpenBook(libraryId, job.libraryItemId) }
                    } else {
                        Modifier
                    },
                ) {
                    CoverThumb(row.coverModel, job.title ?: job.libraryItemId)
                }
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (libraryId != null) {
                                Modifier.clickable { onOpenBook(libraryId, job.libraryItemId) }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = job.title ?: job.libraryItemId,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    val reason = when (job.state) {
                        DownloadJobState.CANCELLED -> "Cancelled"
                        else -> job.lastError?.take(80) ?: "Failed"
                    }
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    row.sizeSubtitle?.let { sub ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Retry")
                }
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear")
                }
            }
        }
    }
}

@Composable
private fun CompletedDownloadCard(
    item: CompletedDownloadItem,
    onDelete: () -> Unit,
    onOpenBook: (libraryId: String, itemId: String) -> Unit,
) {
    val libraryId = item.libraryId
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = if (libraryId != null) {
                    Modifier.clickable { onOpenBook(libraryId, item.libraryItemId) }
                } else {
                    Modifier
                },
            ) {
                CoverThumb(item.coverModel, item.title)
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (libraryId != null) {
                            Modifier.clickable { onOpenBook(libraryId, item.libraryItemId) }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val partsLabel = if (item.partCount > 0) "${item.partCount} file${if (item.partCount != 1) "s" else ""}" else "Ready"
                Text(
                    text = partsLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.localSizeLabel?.let { sz ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = sz,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete local files",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}


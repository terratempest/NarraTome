package com.narratome.presentation.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.request.ImageRequest
import com.narratome.R
import com.narratome.data.local.db.BookmarkEntity
import com.narratome.data.local.db.DownloadJobEntity
import com.narratome.data.local.db.PlaybackHistoryEntity
import com.narratome.domain.download.DownloadJobState
import com.narratome.domain.model.BookChapter
import com.narratome.domain.model.BookDetail
import com.narratome.domain.model.PodcastEpisode
import com.narratome.domain.model.displayTitle
import com.narratome.domain.model.sortedByStart
import com.narratome.presentation.components.AmbientCoverArt
import com.narratome.presentation.player.CreateBookmarkDialog
import com.narratome.presentation.theme.ProgressComplete
import com.narratome.presentation.theme.ProgressIndicator
import com.narratome.util.formatTime
import com.narratome.util.formatTimeLong
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onOpenSeries: (String, String) -> Unit,
    onOpenAuthor: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val coverRevision by viewModel.coverRevision.collectAsStateWithLifecycle()

    DetailScreenContent(
        ui = ui,
        bookmarks = bookmarks,
        coverRevision = coverRevision,
        onBack = onBack,
        onOpenSeries = onOpenSeries,
        onOpenAuthor = onOpenAuthor,
        onPlay = viewModel::play,
        onPause = viewModel::pause,
        onToggleFinished = viewModel::toggleFinished,
        onEnqueueDownload = viewModel::enqueueDownload,
        onDeleteDownload = viewModel::deleteDownload,
        onCancelDownload = viewModel::cancelDownload,
        onRetryDownload = viewModel::retryDownload,
        onPlayFromTime = viewModel::playFromTime,
        onPlayEpisode = viewModel::playEpisode,
        onEnqueueEpisodeDownload = viewModel::enqueueEpisodeDownload,
        onCancelEpisodeDownload = viewModel::cancelEpisodeDownload,
        onRetryEpisodeDownload = viewModel::retryEpisodeDownload,
        onDeleteEpisodeDownload = viewModel::deleteEpisodeDownload,
        onRemoveBookmark = viewModel::removeBookmark,
        onAddBookmark = viewModel::addBookmarkAtCurrentPosition,
        resolveCoverModel = viewModel::resolveCoverModel,
        onForceRefresh = viewModel::forceRefresh,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreenContent(
    ui: DetailUiState,
    bookmarks: List<BookmarkEntity>,
    coverRevision: Long,
    onBack: () -> Unit,
    onOpenSeries: (String, String) -> Unit,
    onOpenAuthor: (String, String) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onToggleFinished: () -> Unit,
    onEnqueueDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetryDownload: () -> Unit,
    onPlayFromTime: (Double) -> Unit,
    onPlayEpisode: (String) -> Unit,
    onEnqueueEpisodeDownload: (String) -> Unit,
    onCancelEpisodeDownload: (String) -> Unit,
    onRetryEpisodeDownload: (String) -> Unit,
    onDeleteEpisodeDownload: (String) -> Unit,
    onRemoveBookmark: (Double) -> Unit,
    onAddBookmark: (String) -> Unit,
    resolveCoverModel: (String) -> Any?,
    onForceRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var showPlaybackHistoryDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val detail = ui.detail
    val isPodcast = detail?.isPodcast == true

    if (showAddBookmarkDialog && detail != null) {
        CreateBookmarkDialog(
            defaultTitle = "Bookmark at ${formatTime(detail.currentTimeSec.toLong())}",
            onDismiss = { showAddBookmarkDialog = false },
            onConfirm = { title ->
                onAddBookmark(title)
                showAddBookmarkDialog = false
            },
        )
    }

    if (showPlaybackHistoryDialog) {
        PlaybackHistoryDialog(
            history = ui.playbackHistory,
            onDismiss = { showPlaybackHistoryDialog = false },
            onPlayFromTime = { positionSec ->
                showPlaybackHistoryDialog = false
                onPlayFromTime(positionSec)
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (isPodcast) "Podcast" else "Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Playback History") },
                            onClick = {
                                showMenu = false
                                showPlaybackHistoryDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Force Refresh") },
                            onClick = {
                                showMenu = false
                                onForceRefresh()
                            },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (ui.unavailableOffline) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.detail_not_available_offline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp, top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val coverData = remember(detail?.id, coverRevision) { detail?.let { resolveCoverModel(it.id) } }
            AmbientCoverArt(
                model = remember(detail?.id, coverData, coverRevision) {
                    ImageRequest.Builder(context)
                        .data(coverData)
                        .memoryCacheKey("${detail?.id}_${coverRevision}_full")
                        .build()
                },
                contentDescription = detail?.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = RoundedCornerShape(24.dp),
                contentScale = ContentScale.Crop,
            )

            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { if (ui.isPlaying) onPause() else onPlay() },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (ui.isPlaying) "PAUSE" else if (isPodcast) "PLAY EPISODE" else "PLAY")
                }

                Spacer(Modifier.width(8.dp))
                FinishButton(isFinished = ui.isFinished, onToggleFinished = onToggleFinished)

                if (!isPodcast && ui.downloadJob == null) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onEnqueueDownload,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(42.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (detail != null && !detail.isPodcast && detail.durationSec > 0 && !ui.isFinished) {
                ProgressCard(
                    currentTimeSec = detail.currentTimeSec,
                    durationSec = detail.durationSec,
                    title = "Your Progress",
                )
                Spacer(Modifier.height(16.dp))
            }

            HeaderBlock(detail, isPodcast, onOpenSeries, onOpenAuthor)
            DetailFacts(detail)
            DescriptionBlock(detail)

            ui.downloadJob?.let { job ->
                DownloadStatusCard(
                    job = job,
                    sizeSubtitle = ui.downloadSizeSubtitle,
                    onDelete = onDeleteDownload,
                    onCancel = onCancelDownload,
                    onRetry = onRetryDownload,
                )
                Spacer(Modifier.height(8.dp))
            }

            if (detail != null && detail.isPodcast) {
                PodcastEpisodesCard(
                    episodes = detail.episodes,
                    activeEpisodeId = ui.activePodcastEpisodeId,
                    isPlaying = ui.isPlaying,
                    jobsByEpisodeId = ui.episodeDownloadJobs,
                    downloadedEpisodeIds = ui.downloadedEpisodeIds,
                    sizeSubtitles = ui.episodeDownloadSizeSubtitles,
                    onPlayEpisode = onPlayEpisode,
                    onEnqueueDownload = onEnqueueEpisodeDownload,
                    onCancelDownload = onCancelEpisodeDownload,
                    onRetryDownload = onRetryEpisodeDownload,
                    onDeleteDownload = onDeleteEpisodeDownload,
                )
                Spacer(Modifier.height(8.dp))
            } else if (detail != null && detail.chapters.isNotEmpty()) {
                ChaptersCard(book = detail, onPlayFromTime = onPlayFromTime)
                Spacer(Modifier.height(8.dp))
            }

            if (detail != null && !detail.isPodcast) {
                BookmarksCard(
                    detail = detail,
                    bookmarks = bookmarks,
                    onPlayFromTime = onPlayFromTime,
                    onRemoveBookmark = onRemoveBookmark,
                    onAddBookmark = { showAddBookmarkDialog = true },
                    busy = ui.busy,
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun FinishButton(isFinished: Boolean, onToggleFinished: () -> Unit) {
    if (isFinished) {
        Button(
            onClick = onToggleFinished,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(42.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ProgressComplete, contentColor = Color.White),
        ) {
            Icon(Icons.Default.Check, contentDescription = "Unmark finished")
        }
    } else {
        OutlinedButton(
            onClick = onToggleFinished,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(42.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Mark as finished")
        }
    }
}

@Composable
private fun HeaderBlock(
    detail: BookDetail?,
    isPodcast: Boolean,
    onOpenSeries: (String, String) -> Unit,
    onOpenAuthor: (String, String) -> Unit,
) {
    val d = detail
    if (d != null && !d.seriesName.isNullOrBlank()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${d.seriesName}${if (!d.seriesSequence.isNullOrBlank()) ": Book ${d.seriesSequence}" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable { d.seriesId?.let { sid -> onOpenSeries(d.libraryId, sid) } },
            )
        }
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = d?.title ?: "Loading...",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Left,
        )
    }

    if (d != null && !d.author.isNullOrBlank()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (isPodcast) d.author.orEmpty() else "by ${d.author}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(enabled = !isPodcast) { d.author?.let { onOpenAuthor(d.libraryId, it) } },
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ProgressCard(currentTimeSec: Double, durationSec: Double, title: String) {
    if (durationSec <= 0.0 || currentTimeSec <= 0.0) return
    val progress = (currentTimeSec / durationSec).coerceIn(0.0, 1.0)
    val progressPercent = (progress * 100).toInt()
    val remainingSec = (durationSec - currentTimeSec).coerceAtLeast(0.0)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$title: $progressPercent%",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = remainingLabel(remainingSec),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = ProgressIndicator,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
            )
        }
    }
}

private fun remainingLabel(remainingSec: Double): String {
    if (remainingSec <= 1) return "Completed"
    val hours = (remainingSec / 3600).toInt()
    val minutes = ((remainingSec % 3600) / 60).toInt()
    return buildString {
        if (hours > 0) append("$hours hr ")
        append("$minutes min remaining")
    }
}

@Composable
private fun DetailFacts(detail: BookDetail?) {
    val d = detail ?: return
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (d.durationSec > 0) DetailColumn(if (d.isPodcast) "TOTAL" else "DURATION", formatTimeLong(d.durationSec))
        if (d.narrators.isNotEmpty()) DetailColumn("NARRATOR", d.narrators.joinToString(", "))
        if (!d.publishedYear.isNullOrBlank()) DetailColumn("RELEASED", d.publishedYear)
        if (d.genres.isNotEmpty()) DetailColumn("GENRE", d.genres.joinToString(" • "))
        if (d.isPodcast) DetailColumn("EPISODES", d.episodes.size.toString())
    }
}

@Composable
private fun DescriptionBlock(detail: BookDetail?) {
    val d = detail ?: return
    if (d.description.isNullOrBlank()) return
    var expanded by remember(d.id) { mutableStateOf(false) }
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Description",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = d.description.orEmpty(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (expanded) Int.MAX_VALUE else 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun PodcastEpisodesCard(
    episodes: List<PodcastEpisode>,
    activeEpisodeId: String?,
    isPlaying: Boolean,
    jobsByEpisodeId: Map<String, DownloadJobEntity>,
    downloadedEpisodeIds: Set<String>,
    sizeSubtitles: Map<String, String>,
    onPlayEpisode: (String) -> Unit,
    onEnqueueDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onDeleteDownload: (String) -> Unit,
) {
    var expanded by remember(episodes.size) { mutableStateOf(true) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            ExpandableHeader(
                title = "Episodes",
                subtitle = if (episodes.size == 1) "1 episode" else "${episodes.size} episodes",
                expanded = expanded,
                onToggle = { expanded = !expanded },
            )
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                if (episodes.isEmpty()) {
                    Text(
                        text = "No episodes found for this podcast.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    episodes.forEachIndexed { index, episode ->
                        PodcastEpisodeRow(
                            episode = episode,
                            active = episode.id == activeEpisodeId,
                            isPlaying = isPlaying && episode.id == activeEpisodeId,
                            job = jobsByEpisodeId[episode.id],
                            downloaded = episode.id in downloadedEpisodeIds,
                            sizeSubtitle = sizeSubtitles[episode.id],
                            onClick = { onPlayEpisode(episode.id) },
                            onEnqueueDownload = { onEnqueueDownload(episode.id) },
                            onCancelDownload = { onCancelDownload(episode.id) },
                            onRetryDownload = { onRetryDownload(episode.id) },
                            onDeleteDownload = { onDeleteDownload(episode.id) },
                        )
                        if (index < episodes.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun PodcastEpisodeRow(
    episode: PodcastEpisode,
    active: Boolean,
    isPlaying: Boolean,
    job: DownloadJobEntity?,
    downloaded: Boolean,
    sizeSubtitle: String?,
    onClick: () -> Unit,
    onEnqueueDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetryDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
        ) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildEpisodeMeta(episode, downloaded, sizeSubtitle)
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            episode.progress?.takeIf { it > 0.0 && episode.finishedAt == null }?.let { progress ->
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = ProgressIndicator,
                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                )
            }
            if (job?.state == DownloadJobState.RUNNING && job.bytesTotal > 0L) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (job.bytesDownloadedTotal.toFloat() / job.bytesTotal).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        EpisodePlaybackIcon(episode, isPlaying)
        Spacer(Modifier.width(4.dp))
        EpisodeDownloadButton(
            job = job,
            downloaded = downloaded,
            onEnqueueDownload = onEnqueueDownload,
            onCancelDownload = onCancelDownload,
            onRetryDownload = onRetryDownload,
            onDeleteDownload = onDeleteDownload,
        )
    }
}

@Composable
private fun EpisodePlaybackIcon(episode: PodcastEpisode, isPlaying: Boolean) {
    if (episode.finishedAt != null) {
        Icon(Icons.Default.CheckCircle, contentDescription = "Finished", tint = ProgressComplete)
    } else {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play episode",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun EpisodeDownloadButton(
    job: DownloadJobEntity?,
    downloaded: Boolean,
    onEnqueueDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetryDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
) {
    when (job?.state) {
        DownloadJobState.PAUSED_NETWORK -> IconButton(onClick = onRetryDownload) {
            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.download_resume_metered))
        }
        DownloadJobState.QUEUED, DownloadJobState.RUNNING -> IconButton(onClick = onCancelDownload) {
            Icon(Icons.Default.Cancel, contentDescription = "Cancel episode download", tint = MaterialTheme.colorScheme.error)
        }
        DownloadJobState.FAILED, DownloadJobState.CANCELLED -> IconButton(onClick = onRetryDownload) {
            Icon(Icons.Default.Refresh, contentDescription = "Retry episode download", tint = MaterialTheme.colorScheme.primary)
        }
        DownloadJobState.COMPLETED -> IconButton(onClick = onDeleteDownload) {
            Icon(Icons.Default.Delete, contentDescription = "Delete episode download", tint = MaterialTheme.colorScheme.error)
        }
        else -> if (downloaded) {
            IconButton(onClick = onDeleteDownload) {
                Icon(Icons.Default.Delete, contentDescription = "Delete episode download", tint = MaterialTheme.colorScheme.error)
            }
        } else {
            IconButton(onClick = onEnqueueDownload) {
                Icon(Icons.Default.Download, contentDescription = "Download episode", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun buildEpisodeMeta(episode: PodcastEpisode, downloaded: Boolean, sizeSubtitle: String?): String = buildString {
    val numbering = listOfNotNull(episode.season?.let { "S$it" }, episode.episode?.let { "E$it" })
        .joinToString(" ")
        .takeIf { it.isNotBlank() }
    if (numbering != null) append(numbering)
    if (!episode.pubDate.isNullOrBlank()) {
        if (isNotEmpty()) append(" • ")
        append(episode.pubDate)
    }
    if (episode.durationSec > 0) {
        if (isNotEmpty()) append(" • ")
        append(formatTimeLong(episode.durationSec))
    }
    if (downloaded || !sizeSubtitle.isNullOrBlank()) {
        if (isNotEmpty()) append(" • ")
        append(sizeSubtitle ?: "Downloaded")
    }
}

@Composable
private fun ChaptersCard(book: BookDetail, onPlayFromTime: (Double) -> Unit) {
    var expanded by remember(book.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            ExpandableHeader(
                title = "Chapters",
                subtitle = if (book.chapters.size == 1) "1 chapter" else "${book.chapters.size} chapters",
                expanded = expanded,
                onToggle = { expanded = !expanded },
            )
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                book.chapters.sortedByStart().forEachIndexed { index, ch ->
                    ChapterRow(ch, index, onPlayFromTime)
                    if (index < book.chapters.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ChapterRow(ch: BookChapter, index: Int, onPlayFromTime: (Double) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlayFromTime(ch.startSec) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = ch.displayTitle(index),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = buildString {
                append(formatTime(ch.startSec))
                if (ch.endSec > ch.startSec) {
                    append(" – ")
                    append(formatTime(ch.endSec))
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BookmarksCard(
    detail: BookDetail,
    bookmarks: List<BookmarkEntity>,
    onPlayFromTime: (Double) -> Unit,
    onRemoveBookmark: (Double) -> Unit,
    onAddBookmark: () -> Unit,
    busy: Boolean,
) {
    var expanded by remember(detail.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded },
                ) {
                    Text("Bookmarks", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = when (bookmarks.size) {
                            0 -> "No bookmarks"
                            1 -> "1 bookmark"
                            else -> "${bookmarks.size} bookmarks"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onAddBookmark, enabled = !busy) { Text("Add") }
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                if (bookmarks.isEmpty()) {
                    Text(
                        text = "No bookmarks yet. Tap Add to create one at your current position.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                } else {
                    bookmarks.forEachIndexed { index, b ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onPlayFromTime(b.timeSec) }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                            ) {
                                Text(text = b.title.ifBlank { formatTime(b.timeSec) }, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = formatTime(b.timeSec),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onRemoveBookmark(b.timeSec) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete bookmark", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (index < bookmarks.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ExpandableHeader(title: String, subtitle: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaybackHistoryDialog(
    history: List<PlaybackHistoryEntity>,
    onDismiss: () -> Unit,
    onPlayFromTime: (Double) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback History") },
        text = {
            if (history.isEmpty()) {
                Text(
                    text = "No playback history yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                ) {
                    items(history, key = { it.id }) { event ->
                        PlaybackHistoryRow(event = event, onClick = { onPlayFromTime(event.positionSec) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun PlaybackHistoryRow(event: PlaybackHistoryEntity, onClick: () -> Unit) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val timestamp = remember(event.occurredAtEpochMs, locale) {
        SimpleDateFormat("MMM d, h:mm a", locale).format(Date(event.occurredAtEpochMs))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = timestamp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = playbackHistoryEventLabel(event.eventType),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        Text(
            text = formatTime(event.positionSec),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

private fun playbackHistoryEventLabel(eventType: String): String =
    when (eventType) {
        "PLAY" -> "Play"
        "PAUSE" -> "Pause"
        "SEEK" -> "Seek"
        "SERVER_UPDATE" -> "Server update"
        else -> eventType.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
    }

@Composable
private fun DownloadStatusCard(
    job: DownloadJobEntity,
    sizeSubtitle: String?,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    when (job.state) {
        DownloadJobState.PAUSED_NETWORK, DownloadJobState.QUEUED, DownloadJobState.RUNNING ->
            ActiveDownloadCard(job, sizeSubtitle, onCancel, onRetry)
        DownloadJobState.FAILED, DownloadJobState.CANCELLED -> FailedDownloadCard(job, sizeSubtitle, onRetry, onDelete)
        DownloadJobState.COMPLETED -> CompletedDownloadCard(job, sizeSubtitle, onDelete)
    }
}

private fun downloadPercentText(job: DownloadJobEntity): String? =
    when (job.state) {
        DownloadJobState.QUEUED -> "0%"
        DownloadJobState.RUNNING -> when {
            job.bytesTotal > 0 -> "${(100.0 * job.bytesDownloadedTotal / job.bytesTotal).toInt().coerceIn(0, 100)}%"
            job.totalParts > 0 -> "${(100.0 * job.currentPartIndex / job.totalParts).toInt().coerceIn(0, 99)}%"
            else -> null
        }
        else -> null
    }

@Composable
private fun ActiveDownloadCard(job: DownloadJobEntity, sizeSubtitle: String?, onCancel: () -> Unit, onResume: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                val statusText = when (job.state) {
                    DownloadJobState.PAUSED_NETWORK -> stringResource(R.string.download_waiting_unmetered)
                    DownloadJobState.QUEUED -> "Pending"
                    DownloadJobState.RUNNING -> if (job.totalParts > 0) "Part ${job.currentPartIndex + 1} of ${job.totalParts}" else "Downloading…"
                    else -> job.state
                }
                Text(text = statusText, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                sizeSubtitle?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                if (job.state == DownloadJobState.PAUSED_NETWORK) {
                    TextButton(onClick = onResume) { Text(stringResource(R.string.download_resume_metered)) }
                } else Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    downloadPercentText(job)?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    LinearProgressIndicator(
                        progress = {
                            if (job.state == DownloadJobState.RUNNING && job.bytesTotal > 0) {
                                (job.bytesDownloadedTotal.toFloat() / job.bytesTotal).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
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
private fun FailedDownloadCard(job: DownloadJobEntity, sizeSubtitle: String?, onRetry: () -> Unit, onClear: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                val reason = when (job.state) {
                    DownloadJobState.CANCELLED -> "Cancelled"
                    else -> job.lastError?.take(80) ?: "Failed"
                }
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            sizeSubtitle?.let {
                Spacer(Modifier.height(4.dp))
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun CompletedDownloadCard(job: DownloadJobEntity, localSizeLabel: String?, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Download Complete", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                val partsLabel = if (job.totalParts > 0) "${job.totalParts} file${if (job.totalParts != 1) "s" else ""}" else "Ready"
                Text(text = partsLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                localSizeLabel?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(text = it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete local files", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun DetailColumn(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
    }
}

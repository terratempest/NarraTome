package com.narratome.presentation.player

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import com.narratome.player.PlayerState
import com.narratome.player.SleepTimerOption
import com.narratome.util.formatTime
import androidx.compose.ui.tooling.preview.Preview
import com.narratome.data.local.db.BookmarkEntity
import com.narratome.domain.model.BookChapter
import com.narratome.player.SleepTimerState
import androidx.compose.ui.res.painterResource
import com.narratome.R
import com.narratome.presentation.components.AmbientCoverArt
import com.narratome.presentation.theme.AudiobookTheme
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    player: PlayerState,
    viewModel: MiniPlayerViewModel,
    onDismiss: () -> Unit,
    onOpenSeries: (() -> Unit)? = null,
    onOpenAuthor: (() -> Unit)? = null,
) {
    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarksForActiveItem.collectAsStateWithLifecycle()
    val coverRevision by viewModel.coverRevision.collectAsStateWithLifecycle()

    FullPlayerSheetContent(
        player = player,
        sleepTimerState = sleepTimerState,
        bookmarks = bookmarks,
        coverRevision = coverRevision,
        onDismiss = onDismiss,
        onStop = { viewModel.stop() },
        onSeekTo = { viewModel.seekTo(it) },
        onPreviousChapter = { viewModel.previousChapter() },
        onSkipBack = { viewModel.skipBack() },
        onPause = { viewModel.pause() },
        onResume = { viewModel.resume() },
        onSkipForward = { viewModel.skipForward() },
        onNextChapter = { viewModel.nextChapter() },
        onCancelSleepTimer = { viewModel.cancelSleepTimer() },
        onStartSleepTimer = { viewModel.startSleepTimer(it) },
        onDeleteBookmark = { viewModel.deleteBookmark(it) },
        onCreateBookmark = { viewModel.createBookmarkAtCurrentPosition(it) },
        onSetSpeed = { viewModel.setSpeed(it) },
        resolveCoverModel = { viewModel.resolveCoverModel(it) },
        onOpenSeries = onOpenSeries,
        onOpenAuthor = onOpenAuthor,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheetContent(
    player: PlayerState,
    sleepTimerState: SleepTimerState,
    bookmarks: List<BookmarkEntity>,
    coverRevision: Long,
    onDismiss: () -> Unit,
    onStop: suspend () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPreviousChapter: () -> Unit,
    onSkipBack: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkipForward: () -> Unit,
    onNextChapter: () -> Unit,
    onCancelSleepTimer: () -> Unit,
    onStartSleepTimer: (SleepTimerOption) -> Unit,
    onDeleteBookmark: (Double) -> Unit,
    onCreateBookmark: (String) -> Unit,
    onSetSpeed: (Float) -> Unit,
    resolveCoverModel: (String) -> Any?,
    onOpenSeries: (() -> Unit)? = null,
    onOpenAuthor: (() -> Unit)? = null,
) {
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showChapterDialog by remember { mutableStateOf(false) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var showCreateBookmarkDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val offsetY = remember { Animatable(0f) }

    val draggableState = rememberDraggableState { delta ->
        scope.launch {
            offsetY.snapTo(offsetY.value + delta)
        }
    }

    if (showChapterDialog) {
        ChapterListDialog(
            chapters = player.chapters,
            onDismiss = { showChapterDialog = false },
            onPick = { ch -> onSeekTo((ch.startSec * 1000).toLong()) },
        )
    }
    if (showBookmarkDialog) {
        BookmarkListDialog(
            bookmarks = bookmarks,
            onDismiss = { showBookmarkDialog = false },
            onPick = { b -> onSeekTo((b.timeSec * 1000).toLong()) },
            onDelete = { b -> onDeleteBookmark(b.timeSec) },
            onAddBookmark = {
                showBookmarkDialog = false
                showCreateBookmarkDialog = true
            },
        )
    }
    if (showCreateBookmarkDialog) {
        CreateBookmarkDialog(
            defaultTitle = "Bookmark at ${formatTime(player.positionMs / 1000)}",
            onDismiss = { showCreateBookmarkDialog = false },
            onConfirm = { title ->
                onCreateBookmark(title)
                showCreateBookmarkDialog = false
            },
        )
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            onDismiss = { showSleepTimerDialog = false },
            onPick = {
                onStartSleepTimer(it)
                showSleepTimerDialog = false
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = offsetY.value.coerceAtLeast(0f)
            }
            .background(MaterialTheme.colorScheme.surface)
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (offsetY.value > 400f || velocity > 1000f) {
                        onDismiss()
                    } else {
                        scope.launch {
                            offsetY.animateTo(0f)
                        }
                    }
                }
            )
            // Full-bleed overlay sits under the status bar; inset first so controls clear system UI.
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Minimize")
            }
            IconButton(
                onClick = {
                    scope.launch {
                        onStop()
                        onDismiss()
                    }
                }
            ) {

                Icon(Icons.Filled.Stop,
                    contentDescription = "Stop Playback",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Cover Image
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            val coverData = remember(player.libraryItemId, coverRevision) {
                player.libraryItemId?.let { resolveCoverModel(it) }
            }
            AmbientCoverArt(
                model = remember(player.libraryItemId, coverData, coverRevision) {
                    coverData as? androidx.compose.ui.graphics.painter.Painter
                        ?: ImageRequest.Builder(context)
                            .data(coverData)
                            .memoryCacheKey("${player.libraryItemId}_${coverRevision}_full")
                            .build()
                },
                contentDescription = "Cover",
                contentScale = ContentScale.Fit,
                placeholder = painterResource(R.drawable.narratomeicon),
                error = painterResource(R.drawable.narratomeicon),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxSize(),
                imageModifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Metadata
        Row( //series
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!player.seriesName.isNullOrBlank()) {
                val canOpen =
                onOpenSeries != null &&
                    player.seriesId != null &&
                    !player.libraryId.isNullOrBlank()

                Text(
                    text = "${player.seriesName}${if (!player.seriesSequence.isNullOrBlank()) ": Book ${player.seriesSequence}" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Left,
                    modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (canOpen) Modifier.clickable { onOpenSeries() } else Modifier,
                    ),
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        Row( // Title
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = player.title ?: "Unknown Title",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = androidx.compose.ui.text.style.TextAlign.Left
            )
        }

        Row( // Author
            modifier = Modifier.fillMaxWidth()
        ) {
            val author = player.author?.takeIf { it.isNotBlank() }
            val canOpenAuthor =
                onOpenAuthor != null &&
                    author != null &&
                    !player.libraryId.isNullOrBlank()
            Text(
                text = author?.let { "by $it" } ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Left,
                modifier = Modifier.then(
                    if (canOpenAuthor) Modifier.clickable { onOpenAuthor() } else Modifier,
                ),
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        Spacer(Modifier.height(16.dp))

        // Chapter
        player.currentChapterTitle?.takeIf { it.isNotBlank() }?.let { chTitle ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = chTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Progress Bar
        var sliderPosition by remember(player.positionMs) { mutableFloatStateOf(player.positionMs.toFloat()) }
        var isDragging by remember { mutableStateOf(false) }

        Slider(
            value = if (isDragging) sliderPosition else player.positionMs.toFloat(),
            onValueChange = {
                isDragging = true
                sliderPosition = it
            },
            onValueChangeFinished = {
                isDragging = false
                onSeekTo(sliderPosition.toLong())
            },
            valueRange = 0f..(player.durationMs.toFloat().coerceAtLeast(1f)),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(if (isDragging) (sliderPosition / 1000).toLong() else player.positionMs / 1000),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = formatTime((player.durationMs-player.positionMs) / 1000),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Primary Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousChapter) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous Chapter", modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = onSkipBack) {
                Icon(Icons.Filled.Replay30, contentDescription = "Rewind 30s", modifier = Modifier.size(32.dp))
            }
            
            FilledIconButton(
                onClick = {
                    if (player.isPlaying) onPause() else onResume()
                },
                modifier = Modifier.size(64.dp),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (player.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(40.dp)
                )
            }

            IconButton(onClick = onSkipForward) {
                Icon(Icons.Filled.Forward30, contentDescription = "Forward 30s", modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = onNextChapter) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next Chapter", modifier = Modifier.size(32.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Secondary Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bookmarks
            IconButton(onClick = { showBookmarkDialog = true }) {
                Icon(Icons.Filled.Bookmark, contentDescription = "Bookmarks")
            }
            
            // Speed
            PlaybackSpeedControl(
                playbackSpeed = player.playbackSpeed,
                onSetSpeed = onSetSpeed,
            )

            // Sleep Timer
            Box(contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = {
                        if (sleepTimerState.isActive) {
                            onCancelSleepTimer()
                        } else {
                            showSleepTimerDialog = true
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = "Sleep Timer",
                        tint = if (sleepTimerState.isActive) Color.Green else LocalContentColor.current
                    )
                }
                if (sleepTimerState.isActive) {
                    val minutesLeft = (sleepTimerState.timeLeftMs / 60000).coerceAtLeast(1)
                    Text(
                        text = "$minutesLeft",
                        color = Color.Green,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomCenter).offset(y = 8.dp)
                    )
                }
            }

            // Chapter Select
            IconButton(onClick = { showChapterDialog = true }) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Chapters")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun FullPlayerSheetPreview() {
    AudiobookTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            val previewCover = previewCoverPainter()
            FullPlayerSheetContent(
                player = PlayerState(
                    isPlaying = true,
                    positionMs = 1234000L,
                    durationMs = 3600000L,
                    title = "The Stolen Realm",
                    author = "A. J. Everwood",
                    currentChapterTitle = "Chapter 12",
                    seriesName = "The Chronicles of Eldria",
                    seriesSequence = "1",
                    libraryItemId = "item1",
                    libraryId = "lib1",
                    seriesId = "ser1",
                    chapters = listOf(
                        BookChapter("Introduction", 0.0, 600.0),
                        BookChapter("Chapter 1", 600.0, 1200.0),
                        BookChapter("Chapter 12", 1200.0, 1800.0)
                    )
                ),
                sleepTimerState = SleepTimerState(isActive = true, timeLeftMs = 300000L),
                bookmarks = listOf(
                    BookmarkEntity("item1", 100.0, "Great moment", false, false, null),
                    BookmarkEntity("item1", 500.0, "Interesting", false, false, null)
                ),
                coverRevision = 0L,
                onDismiss = {},
                onStop = {},
                onSeekTo = {},
                onPreviousChapter = {},
                onSkipBack = {},
                onPause = {},
                onResume = {},
                onSkipForward = {},
                onNextChapter = {},
                onCancelSleepTimer = {},
                onStartSleepTimer = {},
                onDeleteBookmark = {},
                onCreateBookmark = {},
                onSetSpeed = {},
                resolveCoverModel = { previewCover },
            )
        }
    }
}

@Composable
private fun previewCoverPainter(): androidx.compose.ui.graphics.painter.Painter? {
    val context = LocalContext.current
    val resourceId = remember(context) {
        context.resources.getIdentifier("example_audiobook", "drawable", context.packageName)
    }
    return if (resourceId != 0) painterResource(resourceId) else null
}

@Composable
private fun PlaybackSpeedControl(
    playbackSpeed: Float,
    onSetSpeed: (Float) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var draftSpeed by remember { mutableFloatStateOf(playbackSpeed.roundedToTenth()) }
    var editingSpeed by remember { mutableStateOf(false) }
    var speedInput by remember { mutableStateOf(formatSpeed(playbackSpeed.roundedToTenth())) }
    val normalizedSpeed = playbackSpeed.roundedToTenth()
    val speedModified = normalizedSpeed != 1f

    fun setDraft(speed: Float) {
        draftSpeed = speed.roundedToTenth().coerceIn(MinPlaybackSpeed, MaxPlaybackSpeed)
        speedInput = formatSpeed(draftSpeed)
    }

    Box(contentAlignment = Alignment.Center) {
        TextButton(
            onClick = {
                if (speedModified) {
                    expanded = false
                    editingSpeed = false
                    onSetSpeed(1f)
                } else {
                    setDraft(normalizedSpeed)
                    editingSpeed = false
                    expanded = true
                }
            },
            colors = ButtonDefaults.textButtonColors(
                contentColor = if (speedModified) {
                    MaterialTheme.colorScheme.primary
                } else {
                    LocalContentColor.current
                },
            ),
        ) {
            Text("${formatSpeed(normalizedSpeed)}x", style = MaterialTheme.typography.labelLarge)
        }

        if (expanded) {
            Dialog(
                onDismissRequest = {
                    expanded = false
                    editingSpeed = false
                },
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.ime)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .heightIn(max = 480.dp),
                    ) {
                        Text("Playback speed", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            IconButton(
                                onClick = { setDraft(draftSpeed - PlaybackSpeedStep) },
                                enabled = draftSpeed > MinPlaybackSpeed,
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Decrease playback speed")
                            }

                            if (editingSpeed) {
                                OutlinedTextField(
                                    value = speedInput,
                                    onValueChange = { speedInput = it },
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                TextButton(
                                    onClick = { editingSpeed = true },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = "${formatSpeed(draftSpeed)}x",
                                        style = MaterialTheme.typography.titleMedium,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }

                            IconButton(
                                onClick = { setDraft(draftSpeed + PlaybackSpeedStep) },
                                enabled = draftSpeed < MaxPlaybackSpeed,
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Increase playback speed")
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Button(
                                onClick = {
                                    val typedSpeed = speedInput.toFloatOrNull()
                                    val confirmedSpeed = typedSpeed?.roundedToTenth()?.coerceIn(
                                        MinPlaybackSpeed,
                                        MaxPlaybackSpeed,
                                    ) ?: draftSpeed
                                    onSetSpeed(confirmedSpeed)
                                    setDraft(confirmedSpeed)
                                    expanded = false
                                    editingSpeed = false
                                },
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Confirm")
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MinPlaybackSpeed = 0.5f
private const val MaxPlaybackSpeed = 3.0f
private const val PlaybackSpeedStep = 0.1f

private fun Float.roundedToTenth(): Float = ((this * 10f).roundToInt() / 10f)

private fun formatSpeed(speed: Float): String = String.format(Locale.US, "%.1f", speed)

@Composable
private fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onPick: (SleepTimerOption) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            Column(
                Modifier
                    .widthIn(max = 320.dp)
                    .padding(16.dp)
                    .heightIn(max = 480.dp),
            ) {
                Text("Sleep Timer", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                val options = listOf(
                    SleepTimerOption.FiveMinutes to "5 Minutes",
                    SleepTimerOption.FifteenMinutes to "15 Minutes",
                    SleepTimerOption.ThirtyMinutes to "30 Minutes",
                    SleepTimerOption.FortyFiveMinutes to "45 Minutes",
                    SleepTimerOption.OneHour to "60 Minutes",
                )
                options.forEach { (opt, label) ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(opt) }
                            .padding(vertical = 16.dp),
                    )
                    HorizontalDivider()
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}

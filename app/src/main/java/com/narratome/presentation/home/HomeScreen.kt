package com.narratome.presentation.home

import android.R.attr.theme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.narratome.data.repository.BookProgressUi
import com.narratome.domain.model.LibraryItemSummary
import com.narratome.presentation.theme.ProgressComplete
import com.narratome.presentation.theme.ProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenItem: (String, String) -> Unit,
    onOpenDrawer: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progressMap by viewModel.progressMap.collectAsStateWithLifecycle()
    val coverRevision by viewModel.coverRevision.collectAsStateWithLifecycle()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else if (state.error != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(state.error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
        }
    } else {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refreshFromPull() },
            modifier = Modifier.fillMaxSize(),
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            if (state.recentlyPlayed.isNotEmpty()) {
                item {
                    HomeSection(
                        title = "CONTINUE LISTENING",
                        items = state.recentlyPlayed,
                        progressMap = progressMap,
                        coverRevision = coverRevision,
                        onItemClick = onOpenItem,
                        onOpenDrawer = onOpenDrawer,
                        resolveCoverModel = viewModel::resolveCoverModel,
                    )
                }
            }

            if (state.continueSeries.isNotEmpty()) {
                item {
                    HomeSection(
                        title = "CONTINUE SERIES",
                        items = state.continueSeries,
                        progressMap = progressMap,
                        coverRevision = coverRevision,
                        onItemClick = onOpenItem,
                        onOpenDrawer = onOpenDrawer,
                        resolveCoverModel = viewModel::resolveCoverModel,
                    )
                }
            }

            if (state.recentlyAdded.isNotEmpty()) {
                item {
                    HomeSection(
                        title = "RECENTLY ADDED",
                        items = state.recentlyAdded,
                        progressMap = progressMap,
                        coverRevision = coverRevision,
                        onItemClick = onOpenItem,
                        onOpenDrawer = onOpenDrawer,
                        resolveCoverModel = viewModel::resolveCoverModel,
                    )
                }
            }
        }
        }
    }
}

@Composable
fun HomeSection(
    title: String,
    items: List<LibraryItemSummary>,
    progressMap: Map<String, BookProgressUi>,
    coverRevision: Long,
    onItemClick: (String, String) -> Unit,
    onOpenDrawer: () -> Unit,
    resolveCoverModel: (String) -> Any?,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
        )

        val listState = rememberLazyListState()

        // Connection to pass scroll to drawer when at start
        val nestedScrollConnection = remember(listState, onOpenDrawer) {
            var isDraggingToOpenDrawer = false
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    // Check if we are at the start and swiping right
                    val isAtStart = listState.firstVisibleItemIndex == 0 &&
                            listState.firstVisibleItemScrollOffset == 0

                    if (isAtStart && available.x > 0 && !isDraggingToOpenDrawer) {
                        // Lower threshold for better responsiveness
                        if (available.x > 5f) {
                            isDraggingToOpenDrawer = true
                            onOpenDrawer()
                        }
                    }

                    if (isDraggingToOpenDrawer) {
                        // Once we've triggered the drawer, consume all horizontal scroll
                        // to prevent the LazyRow from moving or hitching.
                        return Offset(x = available.x, y = 0f)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (isDraggingToOpenDrawer) {
                        isDraggingToOpenDrawer = false
                        // Consume the flung velocity so the LazyRow doesn't bounce/scroll
                        // when the user lifts their finger during a drawer-open gesture.
                        // This fixes the "mid-way stop" by ensuring the drawer animation
                        // continues without interference.
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        LazyRow(
            state = listState,
            modifier = Modifier.nestedScroll(nestedScrollConnection),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.id }) { item ->
                HomeBookCard(
                    item = item,
                    progressUi = progressMap[item.id],
                    coverModel = remember(item.id, coverRevision) {
                        ImageRequest.Builder(context)
                            .data(resolveCoverModel(item.id))
                            .memoryCacheKey("${item.id}_${coverRevision}_thumb")
                            .build()
                    },
                    onClick = { onItemClick(item.libraryId, item.id) },
                )
            }
        }
    }
}

@Composable
fun HomeBookCard(
    item: LibraryItemSummary,
    progressUi: BookProgressUi?,
    coverModel: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(140.dp),
    coverBadgeText: String? = null,
) {
    val isFinished = progressUi?.isFinished == true
    val displayProgress = if (isFinished) 1f else progressUi?.progress ?: item.progress
    Column(
        modifier = modifier.clickable { onClick() }
    ) {
        Card(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = coverModel ?: "",
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (!coverBadgeText.isNullOrBlank()) {
                    Text(
                        text = coverBadgeText,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(
                                color = Color(0xFF111111).copy(alpha = 0.88f),
                                shape = RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }

                if (displayProgress != null && displayProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.White.copy(alpha = 0.25f))
                            .align(Alignment.BottomCenter)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(displayProgress)
                                .fillMaxHeight()
                                .background(if (isFinished) ProgressComplete else ProgressIndicator)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = item.author ?: "Unknown Author",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

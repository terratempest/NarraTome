package com.narratome.presentation.filtered

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narratome.data.repository.BookProgressUi
import com.narratome.domain.model.LibraryItemSummary
import com.narratome.presentation.home.HomeBookCard
import com.narratome.presentation.theme.AudiobookTheme

@Composable
fun FilteredBooksScreen(
    onBack: () -> Unit,
    onOpenItem: (libraryId: String, itemId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FilteredBooksViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val progressMap by viewModel.progressMap.collectAsStateWithLifecycle()
    val coverRevision by viewModel.coverRevision.collectAsStateWithLifecycle()

    FilteredBooksContent(
        ui = ui,
        progressMap = progressMap,
        coverRevision = coverRevision,
        onBack = onBack,
        onRefresh = { viewModel.refreshFromPull() },
        onOpenItem = onOpenItem,
        resolveCoverModel = { viewModel.resolveCoverModel(it) },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilteredBooksContent(
    ui: FilteredBooksUiState,
    progressMap: Map<String, BookProgressUi>,
    coverRevision: Long,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenItem: (libraryId: String, itemId: String) -> Unit,
    resolveCoverModel: (String) -> Any?,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = ui.title.ifBlank { "Books" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = ui.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                ui.isLoading && !ui.isRefreshing && ui.books.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                ui.error != null && ui.books.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(ui.error, color = MaterialTheme.colorScheme.error)
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        items(
                            items = ui.books,
                            key = { "${it.id}_$coverRevision" },
                        ) { item ->
                            HomeBookCard(
                                item = item,
                                progressUi = progressMap[item.id] ?: item.progress?.let { BookProgressUi(it, false) },
                                coverModel = resolveCoverModel(item.id),
                                coverBadgeText = ui.seriesNumbersByItemId[item.id],
                                onClick = { onOpenItem(item.libraryId, item.id) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun FilteredBooksPreview() {
    AudiobookTheme {
        FilteredBooksContent(
            ui = FilteredBooksUiState(
                title = "The Way of Kings",
                books = listOf(
                    LibraryItemSummary(
                        id = "1",
                        libraryId = "lib1",
                        title = "Book 1",
                        author = "Brandon Sanderson",
                        mediaType = "book",
                        coverPath = null,
                        progress = 0.5f
                    ),
                    LibraryItemSummary(
                        id = "2",
                        libraryId = "lib1",
                        title = "Book 2",
                        author = "Brandon Sanderson",
                        mediaType = "book",
                        coverPath = null,
                        progress = 0.1f
                    ),
                    LibraryItemSummary(
                        id = "3",
                        libraryId = "lib1",
                        title = "Book 3",
                        author = "Brandon Sanderson",
                        mediaType = "book",
                        coverPath = null,
                        progress = null
                    ),
                    LibraryItemSummary(
                        id = "4",
                        libraryId = "lib1",
                        title = "Ender's Game",
                        author = "Brandon Sanderson",
                        mediaType = "book",
                        coverPath = null,
                        progress = 1f
                    )
                ),
                isLoading = false
            ),
            progressMap = emptyMap(),
            coverRevision = 0L,
            onBack = {},
            onRefresh = {},
            onOpenItem = { _, _ -> },
            resolveCoverModel = { null }
        )
    }
}

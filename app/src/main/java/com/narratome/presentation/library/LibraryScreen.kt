package com.narratome.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewComfy
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.narratome.R
import com.narratome.data.local.db.CatalogItemEntity
import com.narratome.data.local.preferences.LibraryViewMode
import com.narratome.data.repository.BookProgressUi
import com.narratome.data.repository.toLibraryItemSummary
import com.narratome.domain.model.LibraryItemSummary
import com.narratome.presentation.home.HomeBookCard
import com.narratome.presentation.theme.ProgressComplete
import com.narratome.presentation.theme.ProgressIndicator

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    onOpenItem: (libraryId: String, itemId: String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val paged = viewModel.pagedItems.collectAsLazyPagingItems()
    val progressMap by viewModel.progressMap.collectAsStateWithLifecycle()
    val coverRevision by viewModel.coverRevision.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = {
                    search = it
                    if (it.isEmpty()) viewModel.clearSearch()
                    else viewModel.search(it)
                },
                placeholder = {
                    Text(
                        stringResource(R.string.library_search_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            LibraryViewModeControl(
                selected = ui.viewMode,
                onSelect = viewModel::setViewMode,
            )
            if (!ui.searchMode && ui.selectedLibraryId != null) {
                IconButton(
                    onClick = { menuExpanded = true },
                    enabled = !ui.busy,
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Library options")
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_rebuild_catalog)) },
                            onClick = {
                                menuExpanded = false
                                viewModel.resyncCatalogClearAndReload()
                            },
                        )
                    }
                }
            }
        }

        if (!ui.searchMode) {
            LibraryFilterSortBar(
                selectedSort = ui.sort,
                selectedFilter = ui.filterOption,
                onSortSelected = viewModel::setSortOption,
                onFilterSelected = viewModel::setFilterOption,
            )
        }

        ui.message?.let { msg ->
            Text(
                text = msg,
                modifier = Modifier
                    .clickable { viewModel.dismissMessage() }
                    .padding(vertical = 2.dp),
                color = Color.Red,
                fontSize = 12.sp,
            )
        }

        PullToRefreshBox(
            isRefreshing = ui.busy,
            onRefresh = { viewModel.refreshFromPull() },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (ui.searchMode) {
                LibrarySearchItems(
                    items = ui.searchItems,
                    viewMode = ui.viewMode,
                    progressMap = progressMap,
                    coverRevision = coverRevision,
                    resolveCoverModel = viewModel::resolveCoverModel,
                    onOpenItem = onOpenItem,
                )
            } else {
                LibraryPagedItems(
                    itemCount = paged.itemCount,
                    itemAt = { index -> runCatching { paged[index] }.getOrNull() },
                    viewMode = ui.viewMode,
                    progressMap = progressMap,
                    coverRevision = coverRevision,
                    resolveCoverModel = viewModel::resolveCoverModel,
                    onOpenItem = onOpenItem,
                )
            }
        }
    }
}

@Composable
private fun LibraryViewModeControl(
    selected: LibraryViewMode,
    onSelect: (LibraryViewMode) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.size(44.dp),
        ) {
            Icon(selected.icon, contentDescription = "Library view")
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            LibraryViewMode.entries.forEach { mode ->
                DropdownMenuItem(
                    leadingIcon = { Icon(mode.icon, contentDescription = null) },
                    text = { Text(mode.label) },
                    onClick = {
                        menuExpanded = false
                        onSelect(mode)
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryFilterSortBar(
    selectedSort: LibrarySortState,
    selectedFilter: LibraryFilterOption,
    onSortSelected: (LibrarySortOption) -> Unit,
    onFilterSelected: (LibraryFilterOption) -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            OutlinedButton(onClick = { sortMenuExpanded = true }) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(selectedSort.label)
            }
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false },
            ) {
                LibrarySortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(if (selectedSort.option == option) selectedSort.label else option.label) },
                        onClick = {
                            sortMenuExpanded = false
                            onSortSelected(option)
                        },
                    )
                }
            }
        }
        Box {
            OutlinedButton(onClick = { filterMenuExpanded = true }) {
                Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(selectedFilter.label)
            }
            DropdownMenu(
                expanded = filterMenuExpanded,
                onDismissRequest = { filterMenuExpanded = false },
            ) {
                LibraryFilterOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            filterMenuExpanded = false
                            onFilterSelected(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibrarySearchItems(
    items: List<LibraryItemSummary>,
    viewMode: LibraryViewMode,
    progressMap: Map<String, BookProgressUi>,
    coverRevision: Long,
    resolveCoverModel: (String) -> Any?,
    onOpenItem: (libraryId: String, itemId: String) -> Unit,
) {
    when (viewMode) {
        LibraryViewMode.List -> {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { item ->
                    LibraryBookListRow(
                        item = item,
                        progressUi = progressMap[item.id] ?: item.progress?.let { BookProgressUi(it, false) },
                        coverModel = remember(item.id, coverRevision) { resolveCoverModel(item.id) },
                        isDownloaded = null,
                        onClick = { onOpenItem(item.libraryId, item.id) },
                    )
                }
            }
        }
        else -> {
            LibraryBookGrid(
                columns = if (viewMode == LibraryViewMode.CompactGrid) 3 else 2,
                compact = viewMode == LibraryViewMode.CompactGrid,
            ) {
                items(items, key = { it.id }) { item ->
                    HomeBookCard(
                        item = item,
                        progressUi = progressMap[item.id] ?: item.progress?.let { BookProgressUi(it, false) },
                        coverModel = remember(item.id, coverRevision) { resolveCoverModel(item.id) },
                        onClick = { onOpenItem(item.libraryId, item.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryPagedItems(
    itemCount: Int,
    itemAt: (Int) -> CatalogItemEntity?,
    viewMode: LibraryViewMode,
    progressMap: Map<String, BookProgressUi>,
    coverRevision: Long,
    resolveCoverModel: (String) -> Any?,
    onOpenItem: (libraryId: String, itemId: String) -> Unit,
) {
    when (viewMode) {
        LibraryViewMode.List -> {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    count = itemCount,
                    key = { index -> "library-list-slot-$index" },
                ) { index ->
                    val entity = itemAt(index) ?: return@items
                    val item = entity.toLibraryItemSummary()
                    LibraryBookListRow(
                        item = item,
                        progressUi = progressMap[item.id] ?: item.progress?.let { BookProgressUi(it, false) },
                        coverModel = remember(item.id, coverRevision) { resolveCoverModel(item.id) },
                        isDownloaded = entity.isDownloaded,
                        onClick = { onOpenItem(item.libraryId, item.id) },
                    )
                }
            }
        }
        else -> {
            LibraryBookGrid(
                columns = if (viewMode == LibraryViewMode.CompactGrid) 3 else 2,
                compact = viewMode == LibraryViewMode.CompactGrid,
            ) {
                items(
                    count = itemCount,
                    key = { index -> "library-grid-slot-$index" },
                ) { index ->
                    val entity = itemAt(index) ?: return@items
                    val item = entity.toLibraryItemSummary()
                    HomeBookCard(
                        item = item,
                        progressUi = progressMap[item.id] ?: item.progress?.let { BookProgressUi(it, false) },
                        coverModel = remember(item.id, coverRevision) { resolveCoverModel(item.id) },
                        onClick = { onOpenItem(item.libraryId, item.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryBookGrid(
    columns: Int,
    compact: Boolean,
    content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
        modifier = Modifier.fillMaxSize(),
        content = content,
    )
}

@Composable
private fun LibraryBookListRow(
    item: LibraryItemSummary,
    progressUi: BookProgressUi?,
    coverModel: Any?,
    isDownloaded: Boolean?,
    onClick: () -> Unit,
) {
    val isFinished = progressUi?.isFinished == true
    val displayProgress = if (isFinished) 1f else progressUi?.progress ?: item.progress
    val statusText = when {
        isFinished -> "Finished"
        displayProgress != null && displayProgress > 0f -> "${(displayProgress * 100).toInt()}%"
        isDownloaded == true -> "Downloaded"
        else -> null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Card(
                modifier = Modifier
                    .size(52.dp)
                    .aspectRatio(1f),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = coverModel ?: "",
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.author ?: "Unknown Author",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (displayProgress != null && displayProgress > 0f) {
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(displayProgress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(if (isFinished) ProgressComplete else ProgressIndicator),
                        )
                    }
                }
            }
            if (statusText != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    if (isDownloaded == true && !isFinished && (displayProgress == null || displayProgress <= 0f)) {
                        Icon(
                            Icons.Default.DownloadDone,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private val LibrarySortOption.label: String
    get() = when (this) {
        LibrarySortOption.Title -> "Title"
        LibrarySortOption.RecentlyAdded -> "Recently Added"
        LibrarySortOption.Author -> "Author"
    }

private val LibrarySortState.label: String
    get() = when (option) {
        null -> "Sort off"
        LibrarySortOption.Title -> if (reversed) "Title Z-A" else "Title A-Z"
        LibrarySortOption.RecentlyAdded -> if (reversed) "Oldest added" else "Newest added"
        LibrarySortOption.Author -> if (reversed) "Author Z-A" else "Author A-Z"
    }

private val LibraryFilterOption.label: String
    get() = when (this) {
        LibraryFilterOption.All -> "All"
        LibraryFilterOption.InProgress -> "In Progress"
        LibraryFilterOption.Downloaded -> "Downloaded"
    }

private val LibraryViewMode.label: String
    get() = when (this) {
        LibraryViewMode.LargeGrid -> "Large grid"
        LibraryViewMode.CompactGrid -> "Compact grid"
        LibraryViewMode.List -> "List"
    }

private val LibraryViewMode.icon
    get() = when (this) {
        LibraryViewMode.LargeGrid -> Icons.Default.ViewComfy
        LibraryViewMode.CompactGrid -> Icons.Default.ViewModule
        LibraryViewMode.List -> Icons.AutoMirrored.Filled.ViewList
    }

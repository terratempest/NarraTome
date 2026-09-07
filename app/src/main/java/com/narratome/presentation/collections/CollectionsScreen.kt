package com.narratome.presentation.collections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narratome.domain.model.CollectionSummary
import com.narratome.presentation.components.BrowseSummaryCard

@Composable
fun CollectionsScreen(
    onCollectionClick: (libraryId: String, collectionId: String) -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val coverRevision by viewModel.coverRevision.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { viewModel.reload() },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.isOffline -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Offline", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.error != null && state.collections.isEmpty() && !state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                }
            }
            state.collections.isEmpty() && state.isLoading -> {
                Box(Modifier.fillMaxSize())
            }
            state.collections.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No collections found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                Column(Modifier.fillMaxSize()) {
                    state.error?.let { msg ->
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.collections) { collection ->
                            CollectionItem(
                                collection = collection,
                                enabled = state.libraryId != null,
                                coverRevision = coverRevision,
                                resolveCoverModel = viewModel::resolveCoverModel,
                                onClick = {
                                    state.libraryId?.let { libraryId ->
                                        onCollectionClick(libraryId, collection.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollectionItem(
    collection: CollectionSummary,
    enabled: Boolean,
    coverRevision: Long,
    resolveCoverModel: (String) -> Any?,
    onClick: () -> Unit,
) {
    BrowseSummaryCard(
        title = collection.name,
        quantityText = "${collection.bookCount} Books",
        description = collection.description,
        coverItemIds = collection.coverItemIds,
        coverRevision = coverRevision,
        enabled = enabled,
        resolveCoverModel = resolveCoverModel,
        onClick = onClick,
    )
}

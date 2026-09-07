package com.narratome.presentation.authors

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
import com.narratome.domain.model.AuthorSummary
import com.narratome.presentation.components.BrowseSummaryCard

@Composable
fun AuthorsScreen(
    onAuthorClick: (String) -> Unit,
    viewModel: AuthorsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val coverRevision by viewModel.coverRevision.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { viewModel.reload() },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.error != null && state.authors.isEmpty() && !state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                }
            }
            state.authors.isEmpty() && state.isLoading -> {
                Box(Modifier.fillMaxSize())
            }
            state.authors.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No authors found", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        items(state.authors) { author ->
                            AuthorItem(
                                author = author,
                                coverItemIds = state.coverItemIdsByAuthor[author.name].orEmpty(),
                                coverRevision = coverRevision,
                                resolveCoverModel = viewModel::resolveCoverModel,
                                onClick = { onAuthorClick(author.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthorItem(
    author: AuthorSummary,
    coverItemIds: List<String>,
    coverRevision: Long,
    resolveCoverModel: (String) -> Any?,
    onClick: () -> Unit,
) {
    BrowseSummaryCard(
        title = author.name,
        quantityText = "${author.bookCount} Books",
        description = author.description,
        coverItemIds = coverItemIds,
        coverRevision = coverRevision,
        enabled = true,
        resolveCoverModel = resolveCoverModel,
        onClick = onClick,
    )
}

package com.narratome.presentation.series

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
import com.narratome.domain.model.SeriesSummary
import com.narratome.presentation.components.BrowseSummaryCard

@Composable
fun SeriesScreen(
    onSeriesClick: (libraryId: String, seriesId: String) -> Unit,
    viewModel: SeriesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val coverRevision by viewModel.coverRevision.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { viewModel.reload() },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.error != null && state.series.isEmpty() && !state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                }
            }
            state.series.isEmpty() && state.isLoading -> {
                Box(Modifier.fillMaxSize())
            }
            state.series.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No series found", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        items(state.series) { series ->
                            val libId = state.libraryId
                            SeriesItem(
                                series = series,
                                enabled = libId != null,
                                coverItemIds = state.coverItemIdsBySeries[series.id].orEmpty(),
                                coverRevision = coverRevision,
                                resolveCoverModel = viewModel::resolveCoverModel,
                                onClick = {
                                    if (libId != null) onSeriesClick(libId, series.id)
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
fun SeriesItem(
    series: SeriesSummary,
    enabled: Boolean,
    coverItemIds: List<String>,
    coverRevision: Long,
    resolveCoverModel: (String) -> Any?,
    onClick: () -> Unit,
) {
    BrowseSummaryCard(
        title = series.name,
        quantityText = "${series.bookCount} Books",
        description = null,
        coverItemIds = coverItemIds,
        coverRevision = coverRevision,
        enabled = enabled,
        resolveCoverModel = resolveCoverModel,
        onClick = onClick,
    )
}

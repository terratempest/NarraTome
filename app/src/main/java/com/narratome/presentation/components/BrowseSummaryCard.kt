package com.narratome.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun BrowseSummaryCard(
    title: String,
    quantityText: String,
    description: String?,
    coverItemIds: List<String>,
    coverRevision: Long,
    enabled: Boolean,
    resolveCoverModel: (String) -> Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cleanDescription = description.cleanBrowseDescription()
    val context = LocalContext.current
    val coverRequests = remember(context, coverItemIds, coverRevision) {
        coverItemIds.distinct().take(5).mapNotNull { itemId ->
            val model = resolveCoverModel(itemId) ?: return@mapNotNull null
            ImageRequest.Builder(context)
                .data(model)
                .memoryCacheKey("${itemId}_${coverRevision}_browse_bg")
                .build()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(90.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (coverRequests.isNotEmpty()) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = maxWidth * 0.25f),
                    ) {
                        CoverCollage(coverRequests = coverRequests)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF18191C).copy(alpha = 0.74f)),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (coverRequests.isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = quantityText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (coverRequests.isNotEmpty()) {
                        Color.White.copy(alpha = 0.78f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = cleanDescription.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (coverRequests.isNotEmpty()) {
                        Color.White.copy(alpha = if (cleanDescription == null) 0f else 0.88f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (cleanDescription == null) 0f else 1f,
                        )
                    },
                    maxLines = 1,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CoverCollage(coverRequests: List<ImageRequest>) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .blur(1.5.dp),
    ) {
        coverRequests.forEachIndexed { index, request ->
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .align(coverAlignment(index))
                    .offset(
                        x = coverOffsetX(index),
                        y = if (index % 2 == 0) (-3).dp else 5.dp,
                    )
                    .graphicsLayer {
                        rotationZ = when (index % 5) {
                            0 -> -10f
                            1 -> 6f
                            2 -> -4f
                            3 -> 10f
                            else -> 3f
                        }
                    }
                    .clip(RoundedCornerShape(4.dp))
                    .alpha(0.72f),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private fun coverAlignment(index: Int): Alignment =
    when (index % 5) {
        0 -> Alignment.CenterStart
        1 -> Alignment.TopCenter
        2 -> Alignment.Center
        3 -> Alignment.BottomCenter
        else -> Alignment.CenterEnd
    }

private fun coverOffsetX(index: Int) =
    when (index % 5) {
        0 -> (-10).dp
        1 -> (-74).dp
        2 -> 8.dp
        3 -> 86.dp
        else -> 12.dp
    }

private fun String?.cleanBrowseDescription(): String? =
    this
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

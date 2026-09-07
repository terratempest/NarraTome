package com.narratome.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.narratome.presentation.theme.AudiobookTheme

@Composable
fun AmbientCoverArt(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier.fillMaxSize(),
    contentScale: ContentScale = ContentScale.Crop,
    shape: Shape = RoundedCornerShape(24.dp),
    placeholder: Painter? = null,
    error: Painter? = null,
    showCover: Boolean = true,
    glowPadding: Dp = 64.dp,
) {
    val painterModel = model as? Painter
    AmbientCoverArtLayout(
        modifier = modifier,
        contentScale = contentScale,
        shape = shape,
        cover = { imageModifier ->
            if (painterModel != null) {
                Image(
                    painter = painterModel,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = imageModifier,
                )
            } else {
                AsyncImage(
                    model = model ?: "",
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    placeholder = placeholder,
                    error = error,
                    modifier = imageModifier,
                )
            }
        },
        glow = { glowModifier ->
            if (painterModel != null) {
                Image(
                    painter = painterModel,
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = glowModifier,
                )
            } else {
                AsyncImage(
                    model = model ?: "",
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = glowModifier,
                )
            }
        },
        imageModifier = imageModifier,
        showCover = showCover,
        glowPadding = glowPadding,
    )
}

@Composable
private fun AmbientCoverArtLayout(
    modifier: Modifier,
    contentScale: ContentScale,
    shape: Shape,
    cover: @Composable (Modifier) -> Unit,
    glow: @Composable (Modifier) -> Unit,
    imageModifier: Modifier,
    showCover: Boolean,
    glowPadding: Dp,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val glowBlur = 36.dp
        val glowAlpha = 1f
        val coverSize = maxWidth.coerceAtMost(maxHeight)
        val coverModifier = imageModifier
            .clip(shape)
        val glowLayerModifier = Modifier
            .requiredSize(coverSize + glowPadding * 2)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .blur(
                radius = glowBlur,
                edgeTreatment = BlurredEdgeTreatment.Unbounded,
            )
            .alpha(glowAlpha)
        Box(
            modifier = glowLayerModifier,
            contentAlignment = Alignment.Center,
        ) {
            glow(Modifier.requiredSize(coverSize))
        }
        if (showCover) {
            cover(coverModifier)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101014)
@Composable
private fun AmbientCoverArtPreview() {
    AudiobookTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(
                modifier = Modifier
                    .size(360.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                AmbientCoverArt(
                    model = previewCoverPainter(),
                    contentDescription = "Cover",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    showCover = true,
                )
            }
        }
    }
}

@Composable
private fun previewCoverPainter(): Painter {
    val context = LocalContext.current
    val resourceId = context.resources.getIdentifier(
        "example_audiobook",
        "drawable",
        context.packageName,
    )
    return if (resourceId != 0) {
        painterResource(resourceId)
    } else {
        ColorPainter(MaterialTheme.colorScheme.primary)
    }
}

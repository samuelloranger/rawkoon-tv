package com.github.damontecres.wholphin.rawkoon.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.PreviewTvSpec

private val PosterShape = RoundedCornerShape(RawkoonTokens.RadiusXLarge)
private val LandscapeShape = RoundedCornerShape(RawkoonTokens.RadiusLarge)

/** The poster shape: 2:3 */
const val RAWKOON_POSTER_ASPECT = 2f / 3f

/** The landscape shape: 16:9 */
const val RAWKOON_LANDSCAPE_ASPECT = 16f / 9f

/**
 * A 2:3 poster card: 16dp radius, 1dp border, the image filling it, and at the bottom a darkened panel with the
 * [title], a [year] pill and an optional [StatusPill]. A 3dp bar in the status color runs along the bottom edge when
 * [status] is not null. The card does not scale on focus: it gets the Rawkoon ring and a brighter border.
 *
 * The panel is a scrim, not a blur, as real blur is not available on older TVs. Size it by width through [modifier].
 */
@Composable
fun RawkoonPosterCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    year: String? = null,
    status: RawkoonStatus? = null,
    statusLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val idle = BorderStroke(1.dp, RawkoonTokens.white(0.10f))
    val focused = BorderStroke(1.dp, RawkoonTokens.white(0.20f))
    RawkoonSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = PosterShape,
        container = RawkoonTokens.Neutral800,
        focusedContainer = RawkoonTokens.Neutral800,
        contentColor = Color.White,
        focusedContentColor = Color.White,
        border = idle,
        focusedBorder = focused,
        interactionSource = interactionSource,
        modifier = modifier.aspectRatio(RAWKOON_POSTER_ASPECT),
    ) {
        Box(Modifier.fillMaxSize()) {
            RawkoonImage(imageUrl, title, Modifier.fillMaxSize())
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                        ),
                    ),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.32f))
                            .padding(start = 9.dp, end = 9.dp, top = 7.dp, bottom = if (status != null) 12.dp else 9.dp),
                ) {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    if (year != null || status != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            if (year != null) {
                                Text(
                                    text = year,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.5.sp,
                                    color = RawkoonTokens.white(0.75f),
                                    modifier =
                                        Modifier
                                            .background(RawkoonTokens.white(0.12f), CircleShape)
                                            .padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                            if (status != null) {
                                StatusPill(status, label = statusLabel ?: stringResource(status.labelRes))
                            }
                        }
                    }
                }
            }
            if (status != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(status.barColor),
                )
            }
        }
    }
}

/**
 * A 16:9 card: 12dp radius, 1dp border, and a 4dp progress track along the bottom of the image when [progress] is
 * set. The [title] and [subtitle] sit under the image. No scale on focus: ring and brighter border.
 *
 * @param progress watched fraction, 0 to 1, or null for no track
 */
@Composable
fun RawkoonLandscapeCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    progress: Float? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val idle = BorderStroke(1.dp, RawkoonTokens.white(0.10f))
    val focused = BorderStroke(1.dp, RawkoonTokens.white(0.20f))
    Column(modifier = modifier) {
        RawkoonSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = LandscapeShape,
            container = RawkoonTokens.Neutral800,
            focusedContainer = RawkoonTokens.Neutral800,
            contentColor = Color.White,
            focusedContentColor = Color.White,
            border = idle,
            focusedBorder = focused,
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth().aspectRatio(RAWKOON_LANDSCAPE_ASPECT),
        ) {
            Box(Modifier.fillMaxSize()) {
                RawkoonImage(imageUrl, title, Modifier.fillMaxSize())
                if (progress != null) {
                    ProgressTrack(
                        fraction = progress,
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp),
                    )
                }
            }
        }
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = RawkoonTokens.Neutral50,
            modifier = Modifier.padding(top = 9.dp),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 12.sp,
                color = RawkoonTokens.Neutral400,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

/** A white-at-15% track with an apricot fill of [fraction] of its width (clamped to 0..1) */
@Composable
internal fun ProgressTrack(
    fraction: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = RawkoonTokens.white(0.15f),
    fillColor: Color = RawkoonTokens.Primary500,
) {
    Box(modifier.background(trackColor)) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxSize()
                .background(fillColor, RoundedCornerShape(topEnd = 99.dp, bottomEnd = 99.dp)),
        )
    }
}

/** An image that shows a plain surface behind it and while it is missing or failed to load */
@Composable
private fun RawkoonImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(modifier.background(RawkoonTokens.Neutral700)) {
        if (!url.isNullOrBlank() && !failed) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@PreviewTvSpec
@Composable
private fun RawkoonCardsPreview() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.background(RawkoonTokens.Background).padding(24.dp),
    ) {
        RawkoonPosterCard("North of Nine", null, {}, Modifier.width(118.dp), year = "2024", status = RawkoonStatus.Downloaded)
        RawkoonPosterCard("Low Tide", null, {}, Modifier.width(118.dp), year = "2021")
        RawkoonLandscapeCard("Episode title", null, {}, Modifier.width(214.dp), subtitle = "S1 E4 · 42 min", progress = 0.4f)
    }
}

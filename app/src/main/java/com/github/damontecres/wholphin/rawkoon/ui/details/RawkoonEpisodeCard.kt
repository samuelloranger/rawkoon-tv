package com.github.damontecres.wholphin.rawkoon.ui.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.rawkoon.ui.kit.ProgressTrack
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonMonoText
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTokens
import com.github.damontecres.wholphin.rawkoon.ui.kit.rawkoonFocusRing
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.roundMinutes
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.extensions.ticks
import androidx.compose.ui.layout.ContentScale
import kotlin.time.Duration

/** Room around a card for the focus ring, which is drawn outside the card and would be clipped by a tight row */
private val RingRoom = 4.dp

/**
 * An episode card: 12dp radius, a 1dp border, a 3dp left status edge (watched, in progress or unwatched), the
 * still, then the title with the mono episode number and duration over a bottom scrim, a watched check, and a
 * progress track along the bottom. Focus draws the Rawkoon ring and does not scale.
 *
 * Replaces Wholphin's `ui.cards.BannerCard` where it shows episodes (same parameters).
 */
@Composable
fun RawkoonEpisodeCard(
    name: String?,
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerText: String? = null,
    played: Boolean = false,
    favorite: Boolean = false,
    playPercent: Double = 0.0,
    cardHeight: Dp = 120.dp,
    aspectRatio: Float = AspectRatios.WIDE,
    interactionSource: MutableInteractionSource? = null,
    imageType: ImageType = ImageType.PRIMARY,
    imageContentScale: ContentScale = ContentScale.FillBounds,
    useSeriesForPrimary: Boolean = true,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(RawkoonTokens.RadiusLarge)
    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    val resources = LocalResources.current
    val fillHeight = remember(cardHeight, density) { if (cardHeight.isSpecified) with(density) { cardHeight.roundToPx() } else null }
    val imageUrl =
        remember(item, fillHeight, imageType, useSeriesForPrimary) {
            item?.let {
                it.imageUrlOverride
                    ?: imageUrlService.getItemImageUrl(
                        it,
                        imageType,
                        fillWidth = null,
                        fillHeight = fillHeight,
                        useSeriesForPrimary = useSeriesForPrimary,
                    )
            }
        }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val edge = episodeEdge(played, playPercent)
    val duration =
        item
            ?.data
            ?.runTimeTicks
            ?.ticks
            ?.roundMinutes
            ?.takeIf { it > Duration.ZERO }
            ?.let { resources.formatDuration(it) }

    // The ring goes around the card, so the box leaves room for it above and below
    Box(modifier = Modifier.size(cardHeight * aspectRatio, cardHeight + RingRoom * 2).padding(vertical = RingRoom)) {
        Surface(
            onClick = { currentOnClick() },
            onLongClick = { currentOnLongClick() },
            shape = ClickableSurfaceDefaults.shape(shape),
            scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = RawkoonTokens.Neutral800,
                    focusedContainerColor = RawkoonTokens.Neutral800,
                    pressedContainerColor = RawkoonTokens.Neutral800,
                    contentColor = Color.White,
                    focusedContentColor = Color.White,
                ),
            border =
                ClickableSurfaceDefaults.border(
                    border = Border(BorderStroke(1.dp, RawkoonTokens.white(0.10f)), shape = shape),
                    focusedBorder = Border(BorderStroke(1.dp, RawkoonTokens.white(0.20f)), shape = shape),
                    pressedBorder = Border(BorderStroke(1.dp, RawkoonTokens.white(0.20f)), shape = shape),
                ),
            interactionSource = source,
            modifier = modifier.fillMaxSize().rawkoonFocusRing(shape, source),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (imageUrl != null) {
                    DetailsImage(imageUrl, null, Modifier.fillMaxSize())
                } else {
                    Text(
                        text = name ?: "",
                        color = RawkoonTokens.Neutral400,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp).align(Alignment.Center),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                ) {
                    if (imageUrl != null && !name.isNullOrBlank()) {
                        Text(
                            text = name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RawkoonTokens.Neutral50,
                        )
                    }
                    val sub = listOfNotNull(cornerText, duration).joinToString(" · ")
                    if (sub.isNotEmpty()) {
                        RawkoonMonoText(text = sub, fontSize = 11.sp, color = RawkoonTokens.Neutral300)
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(3.dp)
                        .background(edge.color),
                )
                if (showWatchedCheck(played, playPercent)) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = RawkoonTokens.Emerald400,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                .padding(3.dp)
                                .size(14.dp),
                    )
                }
                if (playPercent > 0 && playPercent < 100) {
                    ProgressTrack(
                        fraction = (playPercent / 100).toFloat(),
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp),
                    )
                }
            }
        }
    }
}

@PreviewTvSpec
@Composable
private fun RawkoonEpisodeCardPreview() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.background(RawkoonTokens.Background).padding(24.dp),
    ) {
        RawkoonEpisodeCard(name = "Pilot", item = null, onClick = {}, onLongClick = {}, cornerText = "E1", played = true)
        RawkoonEpisodeCard(name = "Second", item = null, onClick = {}, onLongClick = {}, cornerText = "E2", playPercent = 40.0)
    }
}

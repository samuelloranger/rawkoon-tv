package com.github.damontecres.wholphin.rawkoon.ui.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.rawkoon.ui.kit.ProgressTrack
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonPosterCard
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonStatus
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonSurface
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTokens
import com.github.damontecres.wholphin.rawkoon.ui.kit.StatusPill
import com.github.damontecres.wholphin.rawkoon.ui.kit.TypeChip
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.ViewOptionImageType
import org.jellyfin.sdk.model.api.ImageType

private val PosterShape = RoundedCornerShape(RawkoonTokens.RadiusXLarge)
private val LandscapeShape = RoundedCornerShape(RawkoonTokens.RadiusLarge)

/**
 * Rawkoon replacement for [com.github.damontecres.wholphin.ui.cards.BannerCard], with the same parameters: the image
 * only (no title under it), the Rawkoon ring on focus and no scale. In-progress items get the apricot track along the
 * bottom edge and watched items a Watched pill.
 */
@Composable
fun RawkoonBannerCard(
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
    val imageUrl = rememberCardImageUrl(item, imageType, null, cardHeight, useSeriesForPrimary)
    val ratio = aspectRatio.coerceAtLeast(AspectRatios.MIN)
    RawkoonImageCard(
        imageUrl = imageUrl,
        name = name,
        onClick = onClick,
        onLongClick = onLongClick,
        shape = if (RawkoonCardLogic.isPoster(ratio)) PosterShape else LandscapeShape,
        contentScale = imageContentScale,
        progress = RawkoonCardLogic.progress(playPercent),
        status = RawkoonCardLogic.status(played, playPercent),
        favorite = favorite,
        cornerText = cornerText,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        modifier = modifier.size(cardHeight * ratio, cardHeight),
    )
}

/**
 * Rawkoon replacement for [com.github.damontecres.wholphin.ui.cards.BannerCardWithTitle], with the same parameters.
 * Tall cards use [RawkoonPosterCard] (title and year in the panel over the image), the others put a 14sp title and a
 * 12sp subtitle under the image.
 */
@Composable
fun RawkoonBannerCardWithTitle(
    title: String?,
    subtitle: String?,
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
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    imageType: ImageType = ImageType.PRIMARY,
    imageContentScale: ContentScale = ContentScale.FillBounds,
    useSeriesForPrimary: Boolean = item?.useSeriesForPrimary ?: true,
) {
    val ratio = aspectRatio.coerceAtLeast(AspectRatios.MIN)
    val width = cardHeight * ratio
    if (RawkoonCardLogic.isPoster(ratio)) {
        val imageUrl = rememberCardImageUrl(item, imageType, null, cardHeight, useSeriesForPrimary)
        RawkoonPosterWithProgress(
            title = title ?: "",
            imageUrl = imageUrl,
            year = RawkoonCardLogic.yearText(item?.data?.productionYear),
            played = played,
            playPercent = playPercent,
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interactionSource,
            modifier = modifier.width(width),
        )
    } else {
        Column(modifier = modifier.width(width)) {
            RawkoonBannerCard(
                name = null,
                item = item,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = Modifier,
                cornerText = cornerText,
                played = played,
                favorite = favorite,
                playPercent = playPercent,
                cardHeight = cardHeight,
                aspectRatio = aspectRatio,
                interactionSource = interactionSource,
                imageType = imageType,
                imageContentScale = imageContentScale,
                useSeriesForPrimary = useSeriesForPrimary,
            )
            CardTexts(title = title, subtitle = subtitle)
        }
    }
}

/**
 * Rawkoon replacement for [com.github.damontecres.wholphin.ui.cards.GridCard], with the same parameters. Size it by
 * width through [modifier], as the grid does.
 */
@Composable
fun RawkoonGridCard(
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    imageAspectRatio: Float = AspectRatios.TALL,
    imageContentScale: ContentScale = ContentScale.Fit,
    imageType: ViewOptionImageType = ViewOptionImageType.PRIMARY,
    showTitle: Boolean = true,
    fillWidth: Int? = null,
    fillHeight: Int? = null,
) {
    val dto = item?.data
    val ratio = imageAspectRatio.coerceAtLeast(AspectRatios.MIN)
    val imageUrl =
        rememberCardImageUrl(
            item = item,
            imageType = imageType.imageType,
            fillWidth = fillWidth,
            fillHeight = fillHeight,
            useSeriesForPrimary = null,
            requireSize = true,
        )
    val played = dto?.userData?.played ?: false
    val percent = dto?.userData?.playedPercentage
    if (RawkoonCardLogic.isPoster(ratio) && showTitle) {
        RawkoonPosterWithProgress(
            title = item?.title ?: "",
            imageUrl = imageUrl,
            year = RawkoonCardLogic.yearText(dto?.productionYear),
            played = played,
            playPercent = percent ?: 0.0,
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interactionSource,
            modifier = modifier,
        )
    } else {
        Column(modifier = modifier) {
            RawkoonImageCard(
                imageUrl = imageUrl,
                name = item?.name,
                onClick = onClick,
                onLongClick = onLongClick,
                shape = if (RawkoonCardLogic.isPoster(ratio)) PosterShape else LandscapeShape,
                contentScale = imageContentScale,
                progress = RawkoonCardLogic.progress(percent),
                status = RawkoonCardLogic.status(played, percent),
                favorite = dto?.userData?.isFavorite ?: false,
                cornerText = dto?.userData?.unplayedItemCount?.takeIf { it > 0 }?.toString(),
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
            )
            if (showTitle) {
                CardTexts(title = item?.title, subtitle = item?.subtitle)
            }
        }
    }
}

/**
 * Rawkoon replacement for [com.github.damontecres.wholphin.ui.cards.DiscoverItemCard], with the same parameters: a
 * [RawkoonPosterCard] whose status pill follows the request availability, and the media type as a chip when
 * [showOverlay] is set.
 */
@Composable
fun RawkoonDiscoverItemCard(
    item: DiscoverItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showOverlay: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    width: Dp = Cards.height2x3 * AspectRatios.TALL,
) {
    val badge = RawkoonCardLogic.discoverBadge(item?.availability)
    val typeText =
        when (item?.type) {
            SeerrItemType.MOVIE -> pluralStringResource(R.plurals.movies, 1)
            SeerrItemType.TV -> pluralStringResource(R.plurals.tv_shows, 1)
            SeerrItemType.PERSON -> pluralStringResource(R.plurals.people, 1)
            SeerrItemType.UNKNOWN, null -> null
        }
    Box(modifier = modifier.width(width)) {
        RawkoonPosterCard(
            title = item?.title ?: "",
            imageUrl = item?.posterUrl,
            onClick = onClick,
            onLongClick = onLongClick,
            year = RawkoonCardLogic.yearText(item?.releaseDate?.year),
            status = badge?.status,
            statusLabel = if (badge?.partial == true) stringResource(R.string.rawkoon_status_partial) else null,
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth(),
        )
        if (showOverlay && typeText != null) {
            TypeChip(typeText, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
    }
}

/** A poster card with the in-progress track over its bottom edge */
@Composable
private fun RawkoonPosterWithProgress(
    title: String,
    imageUrl: String?,
    year: String?,
    played: Boolean,
    playPercent: Double,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val progress = RawkoonCardLogic.progress(playPercent)
    Box(modifier = modifier) {
        RawkoonPosterCard(
            title = title,
            imageUrl = imageUrl,
            onClick = onClick,
            onLongClick = onLongClick,
            year = year,
            status = RawkoonCardLogic.status(played, playPercent),
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth(),
        )
        if (progress != null) {
            ProgressTrack(
                fraction = progress,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .height(3.dp),
            )
        }
    }
}

/** The title (14sp semibold) and subtitle (12sp) under a landscape or square card */
@Composable
private fun CardTexts(
    title: String?,
    subtitle: String?,
) {
    Text(
        text = title ?: "",
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelLarge,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = RawkoonTokens.Neutral50,
        modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
    )
    if (!subtitle.isNullOrBlank()) {
        Text(
            text = subtitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 12.sp,
            color = RawkoonTokens.Neutral400,
            modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
        )
    }
}

/** An image-only card: border, ring on focus, a missing image shows [name], with the optional marks over it */
@Composable
private fun RawkoonImageCard(
    imageUrl: String?,
    name: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    shape: RoundedCornerShape,
    contentScale: ContentScale,
    progress: Float?,
    status: RawkoonStatus?,
    favorite: Boolean,
    cornerText: String?,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    var imageError by remember(imageUrl) { mutableStateOf(false) }
    // Stabilize callbacks so the image does not recompose
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    RawkoonSurface(
        onClick = { currentOnClick() },
        onLongClick = { currentOnLongClick() },
        shape = shape,
        container = RawkoonTokens.Neutral800,
        focusedContainer = RawkoonTokens.Neutral800,
        contentColor = Color.White,
        focusedContentColor = Color.White,
        border = BorderStroke(1.dp, RawkoonTokens.white(0.10f)),
        focusedBorder = BorderStroke(1.dp, RawkoonTokens.white(0.20f)),
        interactionSource = interactionSource,
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize().background(RawkoonTokens.Neutral700)) {
            if (!imageError && imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = contentScale,
                    onError = remember { { imageError = true } },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = name ?: "",
                    color = RawkoonTokens.Neutral300,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp).align(Alignment.Center),
                )
            }
            if (status != null || cornerText != null) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                    if (status != null) {
                        StatusPill(status)
                    } else if (cornerText != null) {
                        Text(
                            text = cornerText,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier =
                                Modifier
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            if (favorite) {
                Text(
                    text = stringResource(R.string.fa_heart),
                    color = RawkoonTokens.Rose400,
                    fontSize = 14.sp,
                    fontFamily = FontAwesome,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            }
            if (progress != null) {
                ProgressTrack(
                    fraction = progress,
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp),
                )
            }
        }
    }
}

/** The image url of an item for a card of [cardHeight] (or [fillWidth] by [fillHeight]), as the Wholphin cards ask for it */
@Composable
private fun rememberCardImageUrl(
    item: BaseItem?,
    imageType: ImageType,
    fillWidth: Int?,
    cardHeight: Dp,
    useSeriesForPrimary: Boolean?,
): String? {
    val density = LocalDensity.current
    val fillHeight =
        remember(cardHeight, density) {
            if (cardHeight.isSpecified) with(density) { cardHeight.roundToPx() } else null
        }
    return rememberCardImageUrl(item, imageType, fillWidth, fillHeight, useSeriesForPrimary, requireSize = false)
}

@Composable
private fun rememberCardImageUrl(
    item: BaseItem?,
    imageType: ImageType,
    fillWidth: Int?,
    fillHeight: Int?,
    useSeriesForPrimary: Boolean?,
    requireSize: Boolean,
): String? {
    val imageUrlService = LocalImageUrlService.current
    return remember(item, imageType, fillWidth, fillHeight, useSeriesForPrimary, requireSize) {
        if (item == null || (requireSize && (fillWidth ?: 0) <= 0 && (fillHeight ?: 0) <= 0)) {
            null
        } else {
            item.imageUrlOverride
                ?: imageUrlService.getItemImageUrl(
                    item,
                    imageType,
                    fillWidth = fillWidth,
                    fillHeight = fillHeight,
                    useSeriesForPrimary = useSeriesForPrimary,
                )
        }
    }
}

@PreviewTvSpec
@Composable
private fun RawkoonItemCardsPreview() {
    Column(
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        modifier = Modifier.background(RawkoonTokens.Background).padding(24.dp),
    ) {
        RawkoonBannerCard(
            name = "Episode title",
            item = null,
            onClick = {},
            onLongClick = {},
            played = false,
            playPercent = 40.0,
        )
        RawkoonBannerCardWithTitle(
            title = "Episode title",
            subtitle = "S1 E4",
            item = null,
            onClick = {},
            onLongClick = {},
            playPercent = 40.0,
        )
        RawkoonBannerCardWithTitle(
            title = "A poster",
            subtitle = null,
            item = null,
            onClick = {},
            onLongClick = {},
            played = true,
            cardHeight = 172.dp,
            aspectRatio = AspectRatios.TALL,
        )
    }
}

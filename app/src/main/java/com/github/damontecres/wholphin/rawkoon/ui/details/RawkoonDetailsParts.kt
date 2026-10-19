package com.github.damontecres.wholphin.rawkoon.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.rawkoon.RawkoonDisplay
import com.github.damontecres.wholphin.rawkoon.ui.kit.GenreChip
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonMonoText
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTokens
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.TitleOrLogo
import com.github.damontecres.wholphin.ui.components.rememberLogoUrl
import com.github.damontecres.wholphin.ui.formatTime
import com.github.damontecres.wholphin.ui.playback.audioStreamCount
import com.github.damontecres.wholphin.ui.util.LocalClock
import com.github.damontecres.wholphin.ui.util.StreamFormatting
import com.github.damontecres.wholphin.ui.util.StreamFormatting.concatWithSpace
import com.github.damontecres.wholphin.util.languageName
import org.jellyfin.sdk.model.api.ImageType
import kotlin.time.Duration

/** The details poster size */
internal val DetailsPosterWidth = 150.dp
internal val DetailsPosterHeight = 225.dp

/** An image on a plain surface that stays plain while the image is missing or failed to load */
@Composable
internal fun DetailsImage(
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

/** The Jellyfin image URL of [item], or null while there is no item */
@Composable
internal fun rememberItemImageUrl(
    item: BaseItem?,
    imageType: ImageType,
    height: Dp,
): String? {
    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    val fillHeight = remember(height, density) { with(density) { height.roundToPx() } }
    return remember(item?.id, imageType, fillHeight) {
        item?.let { it.imageUrlOverride ?: imageUrlService.getItemImageUrl(it, imageType, fillHeight = fillHeight) }
    }
}

/** The poster of the details header: 150x225, 12dp radius, a 1dp white ring and a big shadow */
@Composable
internal fun DetailsPoster(
    item: BaseItem,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(RawkoonTokens.RadiusLarge)
    val url = rememberItemImageUrl(item, ImageType.PRIMARY, DetailsPosterHeight)
    DetailsImage(
        url = url,
        contentDescription = null,
        modifier =
            modifier
                .size(DetailsPosterWidth, DetailsPosterHeight)
                .shadow(24.dp, shape)
                .clip(shape)
                .border(1.dp, RawkoonTokens.white(0.15f), shape),
    )
}

/**
 * The title in Fraunces, or the logo when [showLogo] is on and the item has one. A logo that fails to load falls
 * back to Wholphin's own title text.
 */
@Composable
internal fun DetailsTitle(
    item: BaseItem,
    showLogo: Boolean,
    modifier: Modifier = Modifier,
    fontSize: Int = 36,
) {
    val logoUrl = if (showLogo) rememberLogoUrl(item) else null
    if (logoUrl != null) {
        TitleOrLogo(title = item.title, logoImageUrl = logoUrl, showLogo = true, modifier = modifier)
    } else {
        Text(
            text = item.title ?: "",
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontFamily = RawkoonDisplay,
            fontWeight = FontWeight.SemiBold,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.1f).sp,
            color = RawkoonTokens.Neutral50,
            modifier = modifier,
        )
    }
}

/** The community rating in amber semibold with a small muted `TMDB` label */
@Composable
internal fun DetailsRating(
    rating: Float,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.Bottom, modifier = modifier) {
        Text(
            text = formatRating(rating),
            style = MaterialTheme.typography.labelLarge,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = RawkoonTokens.Rating,
        )
        Text(
            text = "TMDB",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = RawkoonTokens.white(0.45f),
            modifier = Modifier.padding(start = 3.dp),
        )
    }
}

/** A muted line of meta text in the details header */
@Composable
internal fun DetailsMetaText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        maxLines = 1,
        style = MaterialTheme.typography.labelLarge,
        fontSize = 13.sp,
        color = RawkoonTokens.white(0.58f),
        modifier = modifier,
    )
}

/** `Ends at 9:41 PM` for something that takes [remaining] from now */
@Composable
internal fun rememberEndsAtText(remaining: Duration?): String? {
    val now by LocalClock.current.now
    val context = LocalContext.current
    val resources = LocalResources.current
    return remaining?.takeIf { it > Duration.ZERO }?.let {
        resources.getString(R.string.ends_at, formatTime(context, now.plusSeconds(it.inWholeSeconds)))
    }
}

/** A 4dp track with an apricot fill and the time left in Fira Code */
@Composable
internal fun DetailsProgress(
    fraction: Float,
    minutesLeft: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .width(220.dp)
                .height(4.dp)
                .background(RawkoonTokens.Neutral800, CircleShape),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(RawkoonTokens.Primary500, CircleShape),
            )
        }
        RawkoonMonoText(
            text = LocalResources.current.getString(R.string.rawkoon_min_left, minutesLeft),
            fontSize = 12.sp,
        )
    }
}

/**
 * The resolution + HDR, codec, audio and subtitle tags of the chosen streams, built from the same data and
 * formatters as Wholphin's `VideoStreamDetails`.
 */
@Composable
internal fun rememberStreamChips(
    chosenStreams: ChosenStreams?,
    numberOfVersions: Int,
): List<String> {
    val resources = LocalResources.current
    return remember(chosenStreams, numberOfVersions, resources) {
        val source = chosenStreams?.source
        val videoStream = chosenStreams?.videoStream
        val audioStream = chosenStreams?.audioStream
        val subtitleStream = chosenStreams?.subtitleStream

        val video =
            videoStream?.let {
                val width = it.width
                val height = it.height
                val resolution =
                    if (width != null && height != null) {
                        StreamFormatting.resolutionString(width, height, it.isInterlaced)
                    } else {
                        null
                    }
                val range = StreamFormatting.formatVideoRange(resources, it.videoRange, it.videoRangeType, it.videoDoViTitle)
                resolution.concatWithSpace(range)
            }
        val audio =
            if ((source?.audioStreamCount ?: 0) == 0 || audioStream == null) {
                null
            } else {
                listOfNotNull(
                    languageName(audioStream.language),
                    StreamFormatting.formatAudioCodec(resources, audioStream.codec, audioStream.profile),
                    audioStream.channelLayout,
                ).joinToString(" ")
            }
        val subtitle =
            subtitleStream?.let {
                listOfNotNull(
                    languageName(it.language),
                    "SDH".takeIf { _ -> it.isHearingImpaired },
                    StreamFormatting.formatSubtitleCodec(it.codec),
                ).joinToString(" ")
            }
        streamChipLabels(
            video = video,
            codec = videoStream?.codec?.uppercase(),
            audio = audio,
            subtitle = subtitle,
            extraVersions = (numberOfVersions - 1).coerceAtLeast(0),
        )
    }
}

/** [GenreChip]s for [labels], wrapping onto further lines when they do not fit */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsChips(
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier,
    ) {
        labels.forEach { GenreChip(it) }
    }
}

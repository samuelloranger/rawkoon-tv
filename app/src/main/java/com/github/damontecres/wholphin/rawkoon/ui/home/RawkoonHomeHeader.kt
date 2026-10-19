package com.github.damontecres.wholphin.rawkoon.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.QuickDetailsData
import com.github.damontecres.wholphin.rawkoon.RawkoonDisplay
import com.github.damontecres.wholphin.rawkoon.ui.kit.GenreChip
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonStatus
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTokens
import com.github.damontecres.wholphin.rawkoon.ui.kit.StatusPill
import com.github.damontecres.wholphin.rawkoon.ui.kit.TypeChip
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.EpisodeName
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.components.TitleOrLogo
import com.github.damontecres.wholphin.ui.components.rememberLogoUrl
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.BaseItemKind
import kotlin.time.Duration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonMotion

/**
 * Rawkoon replacement for [com.github.damontecres.wholphin.ui.main.HomePageHeader] (the item overload), with the same
 * parameters. The header follows the focused item and reads like the Rawkoon detail hero: type chip, the quick details,
 * status pill on one row, the title, genre chips and a short overview.
 */
@Composable
fun RawkoonHomeHeader(
    item: BaseItem?,
    showLogo: Boolean,
    modifier: Modifier = Modifier,
) {
    val isEpisode = item?.type == BaseItemKind.EPISODE
    val title =
        remember(item?.id) {
            when (item?.type) {
                BaseItemKind.EPISODE,
                BaseItemKind.SEASON,
                -> item.data.seriesName

                else -> item?.title
            }
        }
    val subtitle =
        remember(item?.id) {
            when (item?.type) {
                BaseItemKind.SEASON,
                BaseItemKind.EPISODE,
                -> item.name

                else -> null
            }
        }
    val typeLabel = RawkoonHeaderLogic.typeLabelRes(item?.type)?.let { stringResource(it) }
    RawkoonHomeHeader(
        title = title,
        subtitle = subtitle,
        overview = item?.data?.overview,
        overviewTwoLines = isEpisode,
        quickDetails = item?.ui?.quickDetails,
        timeRemaining = item?.timeRemainingOrRuntime,
        endsAt = item?.data?.endDate,
        showLogo = showLogo,
        logoImageUrl = rememberLogoUrl(item),
        modifier = modifier,
        typeLabel = typeLabel,
        status = RawkoonHeaderLogic.status(item?.type, item?.data?.status, item?.played ?: false),
        genres = RawkoonHeaderLogic.genres(item?.data?.genres),
    )
}

/**
 * Rawkoon replacement for [com.github.damontecres.wholphin.ui.main.HomePageHeader] (the detail overload), with the
 * same parameters plus the optional hero extras [typeLabel], [status] and [genres].
 */
@Composable
fun RawkoonHomeHeader(
    title: String?,
    subtitle: String?,
    overview: String?,
    overviewTwoLines: Boolean,
    quickDetails: QuickDetailsData?,
    timeRemaining: Duration?,
    endsAt: DateTime?,
    showLogo: Boolean,
    logoImageUrl: String?,
    modifier: Modifier = Modifier,
    typeLabel: String? = null,
    status: RawkoonStatus? = null,
    genres: List<String> = emptyList(),
) {
    val target =
        HeaderContent(
            title = title,
            subtitle = subtitle,
            overview = overview,
            quickDetails = quickDetails,
            timeRemaining = timeRemaining,
            endsAt = endsAt,
            showLogo = showLogo,
            logoImageUrl = logoImageUrl,
            typeLabel = typeLabel,
            status = status,
            genres = genres,
        )
    // Cross-fade between items: the old header leaves fast, the new one fades in with a short rise. Updates for the
    // same item (such as the end time ticking) keep the content key, so they don't animate.
    AnimatedContent(
        targetState = target,
        contentKey = { it.title to it.subtitle },
        contentAlignment = Alignment.TopStart,
        transitionSpec = { headerEnter() togetherWith headerExit() using SizeTransform(clip = false) },
        label = "rawkoon_home_header",
        modifier = modifier,
    ) { h ->
        HeaderBody(h)
    }
}

/** Everything the header shows, so [AnimatedContent] can keep the outgoing item on screen while it fades */
private data class HeaderContent(
    val title: String?,
    val subtitle: String?,
    val overview: String?,
    val quickDetails: QuickDetailsData?,
    val timeRemaining: Duration?,
    val endsAt: DateTime?,
    val showLogo: Boolean,
    val logoImageUrl: String?,
    val typeLabel: String?,
    val status: RawkoonStatus?,
    val genres: List<String>,
)

private fun headerEnter(): EnterTransition {
    val spec = tween<Float>(RawkoonMotion.HEADER_IN_MILLIS, RawkoonMotion.HEADER_IN_DELAY_MILLIS, RawkoonMotion.EnterEasing)
    val slide =
        tween<IntOffset>(RawkoonMotion.HEADER_IN_MILLIS, RawkoonMotion.HEADER_IN_DELAY_MILLIS, RawkoonMotion.EnterEasing)
    return fadeIn(spec) + slideInVertically(slide) { it / 30 }
}

private fun headerExit(): ExitTransition = fadeOut(tween(RawkoonMotion.HEADER_OUT_MILLIS, easing = RawkoonMotion.ExitEasing))

@Composable
private fun HeaderBody(h: HeaderContent) {
    val title = h.title
    val subtitle = h.subtitle
    val overview = h.overview
    val quickDetails = h.quickDetails
    val timeRemaining = h.timeRemaining
    val endsAt = h.endsAt
    val showLogo = h.showLogo
    val logoImageUrl = h.logoImageUrl
    val typeLabel = h.typeLabel
    val status = h.status
    val genres = h.genres
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(.7f),
        ) {
            if (typeLabel != null) TypeChip(typeLabel)
            QuickDetails(
                details = quickDetails,
                timeRemaining = timeRemaining,
                endsAt = endsAt,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            if (status != null) StatusPill(status)
        }
        if (showLogo && logoImageUrl != null) {
            TitleOrLogo(
                title = title,
                logoImageUrl = logoImageUrl,
                showLogo = true,
                modifier = Modifier.fillMaxWidth(.75f),
            )
        } else {
            Text(
                text = title ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = RawkoonDisplay,
                fontWeight = FontWeight.SemiBold,
                fontSize = 34.sp,
                lineHeight = 38.sp,
                color = Color.White,
                modifier = Modifier.fillMaxWidth(.75f),
            )
        }
        if (subtitle != null) {
            EpisodeName(subtitle, Modifier.fillMaxWidth(.6f))
        }
        if (genres.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                genres.forEach { GenreChip(it) }
            }
        }
        val overviewModifier = Modifier.width(420.dp).height(40.dp)
        if (overview.isNotNullOrBlank()) {
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp,
                color = RawkoonTokens.white(0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = overviewModifier,
            )
        } else {
            Spacer(overviewModifier)
        }
    }
}

@PreviewTvSpec
@Composable
private fun RawkoonHomeHeaderPreview() {
    RawkoonHomeHeader(
        title = "North of Nine",
        subtitle = null,
        overview = "A radio operator on a weather ship hears a voice on a frequency no one has used in forty years.",
        overviewTwoLines = false,
        quickDetails = QuickDetailsData(AnnotatedString("2025 · 2h 04m")),
        timeRemaining = null,
        endsAt = null,
        showLogo = false,
        logoImageUrl = null,
        modifier = Modifier.padding(24.dp),
        typeLabel = "Movie",
        status = RawkoonStatus.Downloaded,
        genres = listOf("Drama", "Mystery"),
    )
}

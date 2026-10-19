package com.github.damontecres.wholphin.rawkoon.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.studioNames
import com.github.damontecres.wholphin.preferences.DisplayToggle
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.rawkoon.RawkoonDisplay
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonMonoText
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonStatus
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTokens
import com.github.damontecres.wholphin.rawkoon.ui.kit.StatusPill
import com.github.damontecres.wholphin.rawkoon.ui.kit.TypeChip
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.seasonEpisode
import com.github.damontecres.wholphin.ui.seriesProductionYears
import com.github.damontecres.wholphin.ui.util.LocalInterfaceCustomization
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration

private val DetailsTextWidth = 640.dp

/**
 * The movie details header: a poster, then the title (or logo), tagline, a meta row (type, year, runtime, end time,
 * library status, rating), genre and technical chips, the overview and the resume progress.
 *
 * Replaces Wholphin's `ui.detail.movie.MovieDetailsHeader` (same parameters).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RawkoonMovieDetailsHeader(
    preferences: UserPreferences,
    movie: BaseItem,
    chosenStreams: ChosenStreams?,
    bringIntoViewRequester: BringIntoViewRequester,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dto = movie.data
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val toggles = LocalInterfaceCustomization.current.enabledDisplayToggles
    val runtime = dto.runTimeTicks?.ticks
    val endsAt = rememberEndsAtText(movie.timeRemainingOrRuntime)
    val chips = rememberStreamChips(chosenStreams, dto.mediaSourceCount ?: 0)
    val genres = dto.genres.orEmpty()

    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier.padding(start = HeaderUtils.startPadding),
    ) {
        DetailsPoster(movie)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.widthIn(max = DetailsTextWidth),
        ) {
            DetailsTitle(
                item = movie,
                showLogo = preferences.appPreferences.interfacePreferences.showLogos,
            )
            dto.taglines?.firstOrNull()?.takeIf { it.isNotNullOrBlank() }?.let { tagline ->
                Text(
                    text = tagline,
                    maxLines = 2,
                    fontStyle = FontStyle.Italic,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 14.sp,
                    color = RawkoonTokens.white(0.65f),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                TypeChip(stringResource(R.string.rawkoon_type_movie))
                dto.productionYear?.let { DetailsMetaText(it.toString()) }
                runtime?.roundMinutes?.takeIf { it > Duration.ZERO }?.let {
                    DetailsMetaText(resources.formatDuration(it))
                }
                endsAt?.let { DetailsMetaText(it) }
                if (DisplayToggle.OFFICIAL_RATING in toggles) {
                    dto.officialRating?.takeIf { it.isNotNullOrBlank() }?.let { DetailsMetaText(it) }
                }
                StatusPill(RawkoonStatus.Downloaded)
                if (DisplayToggle.COMMUNITY_RATING in toggles) {
                    dto.communityRating?.let { DetailsRating(it) }
                }
            }
            DetailsChips(genres + chips)
            dto.overview?.takeIf { it.isNotNullOrBlank() }?.let { overview ->
                OverviewText(
                    overview = overview,
                    maxLines = 3,
                    onClick = overviewOnClick,
                    textBoxHeight = Dp.Unspecified,
                    modifier =
                        Modifier.onFocusChanged {
                            if (it.isFocused) {
                                scope.launch(ExceptionHandler()) {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                )
            }
            val fraction = watchedFraction(runtime, movie.playbackPosition)
            val left = minutesLeft(runtime, movie.playbackPosition)
            if (fraction != null && left != null) {
                DetailsProgress(fraction, left)
            }
            val directorName =
                remember(dto.people) {
                    dto.people
                        ?.filter { it.type == PersonKind.DIRECTOR && it.name.isNotNullOrBlank() }
                        ?.joinToString(", ") { it.name!! }
                        ?.takeIf { it.isNotNullOrBlank() }
                }
            directorName?.let {
                Text(
                    text = stringResource(R.string.directed_by, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RawkoonTokens.Neutral300,
                )
            }
        }
    }
}

/**
 * The show details header: a poster, the title (or logo), a meta row (type, years, season and episode counts,
 * status, rating), genre chips and the overview.
 *
 * Replaces Wholphin's `ui.detail.series.SeriesDetailsHeader` (same parameters).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RawkoonSeriesDetailsHeader(
    series: BaseItem,
    showLogo: Boolean,
    overviewOnClick: () -> Unit,
    bringIntoViewRequester: BringIntoViewRequester,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val dto = series.data
    val toggles = LocalInterfaceCustomization.current.enabledDisplayToggles
    val studios = remember(series) { if (dto.studios != null) series.studioNames else emptyList() }
    val genres = dto.genres.orEmpty()
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier.padding(start = HeaderUtils.startPadding),
    ) {
        DetailsPoster(series)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.widthIn(max = DetailsTextWidth),
        ) {
            DetailsTitle(item = series, showLogo = showLogo)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                TypeChip(stringResource(R.string.rawkoon_type_show))
                dto.seriesProductionYears?.let { DetailsMetaText(it) }
                seriesCountsLabel(dto.childCount, dto.recursiveItemCount)?.let { DetailsMetaText(it) }
                if (DisplayToggle.OFFICIAL_RATING in toggles) {
                    dto.officialRating?.takeIf { it.isNotNullOrBlank() }?.let { DetailsMetaText(it) }
                }
                seriesStatus(dto.status)?.let { StatusPill(it) }
                if (DisplayToggle.COMMUNITY_RATING in toggles) {
                    dto.communityRating?.let { DetailsRating(it) }
                }
            }
            if (studios.isNotEmpty()) {
                DetailsMetaText(studios.joinToString(" · "))
            }
            DetailsChips(genres)
            dto.overview?.takeIf { it.isNotNullOrBlank() }?.let { overview ->
                OverviewText(
                    overview = overview,
                    maxLines = 3,
                    onClick = overviewOnClick,
                    textBoxHeight = Dp.Unspecified,
                    modifier =
                        Modifier.onFocusChanged {
                            if (it.isFocused) {
                                scope.launch(ExceptionHandler()) {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                )
            }
        }
    }
}

/**
 * The header of the focused episode on the season overview: a mono episode number and the title, a meta row, the
 * technical chips and the overview. Draws nothing taller than Wholphin's header so the episode row below stays put.
 *
 * Replaces Wholphin's `ui.detail.series.FocusedEpisodeHeader` (same parameters).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RawkoonFocusedEpisodeHeader(
    preferences: UserPreferences,
    ep: BaseItem?,
    chosenStreams: ChosenStreams?,
    overviewOnClick: () -> Unit,
    overviewOnFocus: (FocusState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val dto = ep?.data
    val runtime = dto?.runTimeTicks?.ticks
    val chips = rememberStreamChips(chosenStreams, dto?.mediaSourceCount ?: 0)
    val endsAt = rememberEndsAtText(ep?.timeRemainingOrRuntime)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = HeaderUtils.startPadding),
        ) {
            dto?.indexNumber?.let {
                RawkoonMonoText(text = "E$it", fontSize = 13.sp, color = RawkoonTokens.Primary400, fontWeight = FontWeight.Medium)
            }
            Text(
                text = dto?.episodeTitle ?: dto?.name ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = RawkoonDisplay,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                color = RawkoonTokens.Neutral50,
            )
        }
        if (dto != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = HeaderUtils.startPadding),
            ) {
                TypeChip(stringResource(R.string.rawkoon_type_episode))
                dto.seasonEpisode?.let { DetailsMetaText(it) }
                runtime?.roundMinutes?.takeIf { it > Duration.ZERO }?.let {
                    DetailsMetaText(resources.formatDuration(it))
                }
                endsAt?.let { DetailsMetaText(it) }
                dto.communityRating?.let { DetailsRating(it) }
            }
            val position = ep.playbackPosition
            val fraction = watchedFraction(runtime, position)
            val left = minutesLeft(runtime, position)
            if (fraction != null && left != null) {
                DetailsProgress(fraction, left, Modifier.padding(start = HeaderUtils.startPadding))
            }
            DetailsChips(chips, Modifier.padding(start = HeaderUtils.startPadding))
        }
        OverviewText(
            overview = dto?.overview ?: "",
            maxLines = 3,
            onClick = overviewOnClick,
            modifier = Modifier.onFocusChanged(overviewOnFocus),
        )
    }
}

@PreviewTvSpec
@Composable
private fun DetailsMetaPreview() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
    ) {
        TypeChip("Movie")
        DetailsMetaText("2024")
        StatusPill(RawkoonStatus.Downloaded)
        DetailsRating(7.8f)
        DetailsProgress(0.4f, 42)
    }
}

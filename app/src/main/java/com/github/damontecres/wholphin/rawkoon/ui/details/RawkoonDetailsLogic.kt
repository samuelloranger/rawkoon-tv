package com.github.damontecres.wholphin.rawkoon.ui.details

import androidx.compose.ui.graphics.Color
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonStatus
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTokens
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Pure helpers behind the Rawkoon details screens: formatting and the decisions that are worth a unit test.
 */

/** A playback position as `1:12:04`, or `12:04` below one hour */
fun formatResumeTime(position: Duration): String {
    val total = position.inWholeSeconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/**
 * Whole minutes left of [runtime] at [position], rounded to the nearest minute but never below one.
 * Null when nothing was started, the runtime is unknown, or nothing is left.
 */
fun minutesLeft(
    runtime: Duration?,
    position: Duration,
): Int? {
    if (runtime == null || runtime <= Duration.ZERO || position <= Duration.ZERO) return null
    val remaining = runtime - position
    if (remaining <= Duration.ZERO) return null
    return (remaining + 30.seconds).inWholeMinutes.toInt().coerceAtLeast(1)
}

/** The watched fraction, 0 to 1, or null when nothing was started or the runtime is unknown */
fun watchedFraction(
    runtime: Duration?,
    position: Duration,
): Float? {
    if (runtime == null || runtime <= Duration.ZERO || position <= Duration.ZERO) return null
    return (position / runtime).toFloat().coerceIn(0f, 1f)
}

/** The community rating with one decimal, like `7.8` */
fun formatRating(
    rating: Float,
    locale: Locale = Locale.getDefault(),
): String = String.format(locale, "%.1f", rating)

/** `3S · 28E` for a show, leaving out whichever count is unknown or zero. Null when neither is known. */
fun seriesCountsLabel(
    seasons: Int?,
    episodes: Int?,
): String? {
    val parts =
        listOfNotNull(
            seasons?.takeIf { it > 0 }?.let { "${it}S" },
            episodes?.takeIf { it > 0 }?.let { "${it}E" },
        )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** The Rawkoon status of a show from Jellyfin's series status. Only a continuing show gets a pill. */
fun seriesStatus(status: String?): RawkoonStatus? =
    when (status?.lowercase(Locale.ROOT)) {
        "continuing" -> RawkoonStatus.Returning
        else -> null
    }

/** The technical tags of a stream, in display order. Missing parts are left out. */
fun streamChipLabels(
    video: String?,
    codec: String?,
    audio: String?,
    subtitle: String?,
    extraVersions: Int = 0,
): List<String> =
    buildList {
        if (!video.isNullOrBlank()) {
            add(if (extraVersions > 0) "$video (+$extraVersions)" else video)
        }
        if (!codec.isNullOrBlank()) add(codec)
        if (!audio.isNullOrBlank()) add(audio)
        if (!subtitle.isNullOrBlank()) add(subtitle)
    }

/** The buttons of the details action row, in display order */
enum class RawkoonPlayAction {
    Play,
    Restart,
    Version,
    Watched,
    Favorite,
    Trailer,
    Delete,
    More,
}

/**
 * Which action buttons to show, mirroring what Wholphin's expandable play buttons offer.
 *
 * @param hasTrailerButton the page supports trailers at all (the button is then shown, disabled when there are none)
 */
fun playActions(
    resumePosition: Duration,
    mediaSourceCount: Int,
    hasTrailerButton: Boolean,
    canDelete: Boolean,
): List<RawkoonPlayAction> =
    buildList {
        add(RawkoonPlayAction.Play)
        if (resumePosition > Duration.ZERO) add(RawkoonPlayAction.Restart)
        if (mediaSourceCount > 1) add(RawkoonPlayAction.Version)
        add(RawkoonPlayAction.Watched)
        add(RawkoonPlayAction.Favorite)
        if (hasTrailerButton) add(RawkoonPlayAction.Trailer)
        if (canDelete) add(RawkoonPlayAction.Delete)
        add(RawkoonPlayAction.More)
    }

/** The left status edge of an episode card */
enum class EpisodeEdge(
    val color: Color,
) {
    Watched(RawkoonTokens.Emerald400.copy(alpha = 0.45f)),
    InProgress(RawkoonTokens.Primary400.copy(alpha = 0.70f)),
    Unwatched(RawkoonTokens.Neutral600.copy(alpha = 0.60f)),
}

/**
 * The edge of an episode: watched emerald, in progress apricot, unwatched neutral.
 * [playedPercentage] is Jellyfin's 0 to 100 resume percentage.
 */
fun episodeEdge(
    played: Boolean,
    playedPercentage: Double,
): EpisodeEdge =
    when {
        playedPercentage > 0 && playedPercentage < 100 -> EpisodeEdge.InProgress
        played -> EpisodeEdge.Watched
        else -> EpisodeEdge.Unwatched
    }

/** Whether to show the watched check: played and not partly watched again */
fun showWatchedCheck(
    played: Boolean,
    playedPercentage: Double,
): Boolean = played && (playedPercentage <= 0 || playedPercentage >= 100)

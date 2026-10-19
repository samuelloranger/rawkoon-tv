package com.github.damontecres.wholphin.rawkoon.ui.player

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

/** How long the player has to stay paused, with the controls hidden, before the pause screen fades in */
const val PAUSE_SCREEN_DELAY_MILLIS = 8_000L

/** How long the D-pad skip indicator stays after the last press */
const val SKIP_INDICATOR_MILLIS = 1_500L

/** How long a skip-segment button asks before it dismisses itself */
const val SKIP_SEGMENT_WINDOW_MILLIS = 10_000L

/** Formats a position as `m:ss`, or `h:mm:ss` from one hour on. Negative values count as zero. */
fun formatClock(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L)) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/** The signed distance of a seek, such as `+1:30` or `-0:30`, shown under the trickplay thumbnail */
fun formatSeekDelta(deltaMillis: Long): String {
    val sign = if (deltaMillis < 0) "-" else "+"
    return sign + formatClock(abs(deltaMillis))
}

/** The seek distance label, or null when the seek is back where it started so no `+0:00` shows */
fun seekDeltaLabel(deltaMillis: Long): String? = if (deltaMillis == 0L) null else formatSeekDelta(deltaMillis)

/** The accumulated D-pad skip, such as `+30s` or `-1:30`: whole seconds below a minute, a clock above */
fun formatSkipAmount(deltaMillis: Long): String {
    val sign = if (deltaMillis < 0) "-" else "+"
    val seconds = abs(deltaMillis) / 1000
    return if (seconds < 60) "$sign${seconds}s" else sign + formatClock(abs(deltaMillis))
}

/** A part of the seek bar as fractions of its width, both between 0 and 1 */
data class FractionSpan(
    val start: Float,
    val end: Float,
)

/** The span of a media segment on the seek bar, or null when it is empty or the duration is unknown */
fun segmentSpan(
    startMillis: Long,
    endMillis: Long,
    durationMillis: Long,
): FractionSpan? {
    if (durationMillis <= 0) return null
    val start = (startMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
    val end = (endMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
    return if (end > start) FractionSpan(start, end) else null
}

/** A fraction of [durationMillis], 0 while the duration is unknown */
fun fractionOf(
    positionMillis: Long,
    durationMillis: Long,
): Float = if (durationMillis > 0) (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f) else 0f

/** The controls of the control row */
enum class RawkoonPlayerControl {
    Audio,
    Subtitles,
    Previous,
    SeekBack,
    PlayPause,
    SeekForward,
    Next,
    Settings,
}

/**
 * The control row as three columns, `[left | center | right]`. The center column is the transport group and is laid
 * out centered on the screen whatever the side columns hold. Each list is in focus order, left to right.
 */
fun controlOrder(
    previousEnabled: Boolean,
    nextEnabled: Boolean,
): List<List<RawkoonPlayerControl>> =
    listOf(
        listOf(RawkoonPlayerControl.Audio, RawkoonPlayerControl.Subtitles),
        buildList {
            if (previousEnabled) add(RawkoonPlayerControl.Previous)
            add(RawkoonPlayerControl.SeekBack)
            add(RawkoonPlayerControl.PlayPause)
            add(RawkoonPlayerControl.SeekForward)
        },
        buildList {
            if (nextEnabled) add(RawkoonPlayerControl.Next)
            add(RawkoonPlayerControl.Settings)
        },
    )

/** Wall-clock milliseconds until the media ends, given the playback [speed]. Zero while the duration is unknown. */
fun remainingWallMillis(
    durationMillis: Long,
    positionMillis: Long,
    speed: Float,
): Long {
    if (durationMillis <= 0) return 0L
    val safeSpeed = if (speed > 0f) speed else 1f
    return ((durationMillis - positionMillis).coerceAtLeast(0L) / safeSpeed).toLong()
}

/** Whole minutes left, rounded up so the last seconds read as `1 min left` */
fun minutesLeft(
    durationMillis: Long,
    positionMillis: Long,
): Int = if (durationMillis <= 0) 0 else ceil((durationMillis - positionMillis).coerceAtLeast(0L) / 60_000.0).toInt()

/** `S1 · E4`, or just the part that is known */
fun formatSeasonEpisode(
    season: Int?,
    episode: Int?,
): String? =
    listOfNotNull(season?.let { "S$it" }, episode?.let { "E$it" })
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")

package cloud.rawkoon.tv.playback

import kotlin.math.abs
import kotlin.math.roundToInt

/** A display mode of the TV, reduced to what choosing one needs. */
data class DisplayModeInfo(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

/**
 * Picks the display mode a video of [frameRate] frames per second plays best in, or null when
 * none of [modes] suits it (or the rate is unknown).
 *
 * A mode suits when its refresh rate is the frame rate or a whole multiple of it, which shows
 * every frame for the same time. Frame rate only ([matchResolution] false): keeps the resolution
 * of [current] and takes the lowest suitable multiple, the closest rate first. With
 * [matchResolution], any mode of a suitable rate qualifies and the smallest one at least as large
 * as the [videoWidth] by [videoHeight] picture wins, or the largest when none is that big; the
 * lowest multiple, then the closest rate, breaks ties.
 */
fun pickDisplayMode(
    modes: List<DisplayModeInfo>,
    current: DisplayModeInfo,
    frameRate: Float,
    matchResolution: Boolean,
    videoWidth: Int = 0,
    videoHeight: Int = 0,
): DisplayModeInfo? {
    if (frameRate <= 0f) return null
    val candidates = modes.mapNotNull { mode ->
        if (!matchResolution && (mode.width != current.width || mode.height != current.height)) return@mapNotNull null
        rateMultiple(mode.refreshRate, frameRate)?.let { mode to it }
    }
    if (candidates.isEmpty()) return null
    // Between two modes at the same multiple (23.976 and 24 Hz for 24 fps), the closer rate
    val fit = compareBy<Pair<DisplayModeInfo, Int>> { it.second }.thenBy { (mode, multiple) -> abs(mode.refreshRate / multiple - frameRate) }
    if (!matchResolution) return candidates.minWith(fit).first
    val fits = candidates.filter { (mode, _) -> mode.width >= videoWidth && mode.height >= videoHeight }
    val pool = fits.ifEmpty { candidates }
    val area = { mode: DisplayModeInfo -> mode.width.toLong() * mode.height }
    // Smallest picture that is big enough; when none is, the biggest there is
    val best = if (fits.isNotEmpty()) pool.minOf { area(it.first) } else pool.maxOf { area(it.first) }
    return pool.filter { area(it.first) == best }.minWith(fit).first
}

/** How many times [frameRate] fits in [refreshRate], when it does so exactly enough; otherwise null. */
internal fun rateMultiple(refreshRate: Float, frameRate: Float): Int? {
    val multiple = (refreshRate / frameRate).roundToInt()
    if (multiple < 1) return null
    return if (abs(refreshRate / multiple - frameRate) <= RATE_TOLERANCE_HZ) multiple else null
}

/**
 * How far a mode's rate may be from a whole multiple of the frame rate: 24 fps may play at
 * 23.976 Hz (a rounded container rate, or a TV with only one of the two), but 29.97 is not 30.
 */
private const val RATE_TOLERANCE_HZ = 0.025f

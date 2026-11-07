package cloud.rawkoon.tv.playback

import android.view.Display
import cloud.rawkoon.tv.data.HdrOverride
import cloud.rawkoon.tv.playback.profile.resolve
import java.util.Locale
import kotlin.math.roundToInt

/** What a format's support comes to: what the TV [detected], and what the player [effective]ly acts on after the user's override. */
data class HdrStatus(val detected: Boolean, val effective: Boolean) {
    /** True when the override changed the answer, so the page says so. */
    val forced: Boolean get() = detected != effective

    companion object {
        fun of(detected: Boolean, override: HdrOverride) = HdrStatus(detected, override.resolve(detected))
    }
}

/** The names of the HDR types a display reports, in the order of [types] with duplicates dropped. Unknown types are left out. */
fun hdrTypeNames(types: Collection<Int>): List<String> = types.distinct().mapNotNull {
    when (it) {
        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
        Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
        Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
        else -> null
    }
}

/** A refresh rate without trailing zeros: 60, 59.94, 23.976. */
fun formatRate(hertz: Float): String {
    val rounded = (hertz * 1000).roundToInt() / 1000.0
    return if (rounded == rounded.toInt().toDouble()) rounded.toInt().toString() else String.format(Locale.ROOT, "%.3f", rounded).trimEnd('0')
}

/**
 * The refresh rates each resolution is offered at, as "3840x2160" to its rates: the largest
 * resolution first, rates ascending. Modes that differ only in id (or in a rate rounding away) show once.
 */
fun groupModes(modes: List<DisplayModeInfo>): List<Pair<String, List<String>>> =
    modes.groupBy { it.width to it.height }
        .toList()
        .sortedWith(compareByDescending<Pair<Pair<Int, Int>, List<DisplayModeInfo>>> { it.first.first * it.first.second }.thenByDescending { it.first.first })
        .map { (size, group) ->
            val rates = group.map { (it.refreshRate * 1000).roundToInt() }.distinct().sorted()
            "${size.first}x${size.second}" to rates.map { formatRate(it / 1000f) }
        }

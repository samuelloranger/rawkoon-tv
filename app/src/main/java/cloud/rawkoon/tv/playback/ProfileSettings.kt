package cloud.rawkoon.tv.playback

import android.content.Context
import cloud.rawkoon.tv.data.HdrOverride
import cloud.rawkoon.tv.data.Prefs

/**
 * Every setting the device profile depends on, and so what a cached profile was built for: a
 * change to any of them builds another on the next play. [maxBitrate] is in bits per second and
 * [maxHeight] is 0 for no limit.
 */
data class ProfileSettings(
    val maxBitrate: Int,
    val maxHeight: Int,
    val ac3Passthrough: Boolean,
    val downmixStereo: Boolean,
    val pgsDirectPlay: Boolean,
    val hdr10: HdrOverride,
    val hdr10Plus: HdrOverride,
    val dolbyVision: HdrOverride,
) {
    companion object {
        private const val BITS_PER_MBPS = 1_000_000

        fun read(context: Context) = ProfileSettings(
            maxBitrate = Prefs.maxBitrateMbps(context) * BITS_PER_MBPS,
            maxHeight = Prefs.maxResolution(context),
            ac3Passthrough = Prefs.ac3Passthrough(context),
            downmixStereo = Prefs.downmixStereo(context),
            pgsDirectPlay = Prefs.pgsDirectPlay(context),
            hdr10 = Prefs.hdrOverride(context, Prefs.HDR10),
            hdr10Plus = Prefs.hdrOverride(context, Prefs.HDR10_PLUS),
            dolbyVision = Prefs.hdrOverride(context, Prefs.DOLBY_VISION),
        )
    }
}

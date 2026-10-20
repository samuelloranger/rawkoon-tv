package cloud.rawkoon.tv.data

import android.content.Context
import android.content.SharedPreferences

/** What the player does when playback reaches an intro, recap, preview or the credits. */
enum class SkipMode(val key: String) {
    /** Offers a skip button (the default). */
    BUTTON("button"),

    /** Seeks to the end of the segment as soon as it starts. */
    AUTO("auto"),

    /** Ignores the segment. */
    NONE("none"),
    ;

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: BUTTON
    }
}

/** How far "Match display" goes in asking the TV to follow the video. */
enum class MatchDisplay(val key: String) {
    OFF("off"),

    /** Switches the refresh rate only, at the TV's current resolution. */
    FRAME_RATE("fps"),

    /** Switches the refresh rate and the resolution. */
    FRAME_RATE_AND_RESOLUTION("fps_res"),
    ;

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: OFF
    }
}

/** The one preferences file: app-wide settings that don't belong to a profile. */
object Prefs {
    private const val NAME = "rawkoon"
    private const val RAWKOON_URL = "rawkoon:url"
    const val LANGUAGE = "language"

    // Playback
    const val SKIP_BACK_S = "playback:skip_back_s"
    const val SKIP_FORWARD_S = "playback:skip_forward_s"
    const val RESUME_REWIND_S = "playback:resume_rewind_s"
    const val MAX_BITRATE_MBPS = "playback:max_bitrate_mbps"
    const val MAX_RESOLUTION = "playback:max_resolution"

    // Skipping
    const val SKIP_INTRO = "playback:skip_intro"
    const val SKIP_RECAP = "playback:skip_recap"
    const val SKIP_PREVIEW = "playback:skip_preview"
    const val SKIP_CREDITS = "playback:skip_credits"
    const val AUTOPLAY_NEXT = "playback:autoplay_next"
    const val AUTOPLAY_COUNTDOWN_S = "playback:autoplay_countdown_s"
    const val STILL_WATCHING_EPISODES = "playback:still_watching_episodes"

    // Display
    const val MATCH_DISPLAY = "playback:match_display"

    const val DEFAULT_SKIP_BACK_S = 10
    const val DEFAULT_SKIP_FORWARD_S = 30
    const val DEFAULT_COUNTDOWN_S = 8

    /** The official client's default, in Mbps; what "Auto" means. */
    const val AUTO_BITRATE_MBPS = 100

    fun of(context: Context): SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** The Rawkoon server new profiles sign in to; asked for once, on the first sign-in. */
    fun rawkoonUrl(context: Context): String? = of(context).getString(RAWKOON_URL, null)

    fun setRawkoonUrl(context: Context, url: String) = of(context).edit().putString(RAWKOON_URL, url.trim().trimEnd('/')).apply()

    /** "system", "fr" or "en". */
    fun language(context: Context): String = of(context).getString(LANGUAGE, "system") ?: "system"

    /** Seconds the rewind button and key go back. */
    fun skipBackSeconds(context: Context): Int = of(context).getInt(SKIP_BACK_S, DEFAULT_SKIP_BACK_S)

    /** Seconds the fast-forward button and key go ahead. */
    fun skipForwardSeconds(context: Context): Int = of(context).getInt(SKIP_FORWARD_S, DEFAULT_SKIP_FORWARD_S)

    /** Seconds to step back when playback starts from a saved resume point; 0 is off. */
    fun resumeRewindSeconds(context: Context): Int = of(context).getInt(RESUME_REWIND_S, 0)

    /** The skip behaviour for a segment, by the key of its setting ([SKIP_INTRO] and the like). */
    fun skipMode(context: Context, key: String): SkipMode = SkipMode.of(of(context).getString(key, null))

    /** Whether the next episode starts by itself once the countdown ends. */
    fun autoplayNext(context: Context): Boolean = of(context).getBoolean(AUTOPLAY_NEXT, true)

    fun autoplayCountdownSeconds(context: Context): Int = of(context).getInt(AUTOPLAY_COUNTDOWN_S, DEFAULT_COUNTDOWN_S)

    /** After how many episodes in a row that started by themselves to ask "Still watching?"; 0 is never. */
    fun stillWatchingEpisodes(context: Context): Int = of(context).getInt(STILL_WATCHING_EPISODES, 0)

    fun matchDisplay(context: Context): MatchDisplay = MatchDisplay.of(of(context).getString(MATCH_DISPLAY, null))

    /** The streaming bitrate cap in Mbps; "Auto" is [AUTO_BITRATE_MBPS]. */
    fun maxBitrateMbps(context: Context): Int = of(context).getInt(MAX_BITRATE_MBPS, AUTO_BITRATE_MBPS)

    /** The tallest picture to direct play, in pixels of height; 0 is no limit. */
    fun maxResolution(context: Context): Int = of(context).getInt(MAX_RESOLUTION, 0)
}

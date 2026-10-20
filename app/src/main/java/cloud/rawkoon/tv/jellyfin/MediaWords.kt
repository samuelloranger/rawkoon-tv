package cloud.rawkoon.tv.jellyfin

import android.content.res.Resources
import cloud.rawkoon.tv.R

/**
 * The few words media text carries: durations, channel layouts, "SD". The app points [source] at
 * its string resources, so they follow the app's language; without it (unit tests) they are
 * English, which keeps [MediaItem] and [MediaInfo] pure.
 */
object MediaWords {
    @Volatile
    var source: Resources? = null

    fun duration(hours: Int, minutes: Int): String =
        source?.getString(R.string.media_duration_hours_minutes, hours, minutes) ?: "$hours h $minutes min"

    fun minutes(minutes: Int): String = source?.getString(R.string.media_duration_minutes, minutes) ?: "$minutes min"

    fun mono(): String = source?.getString(R.string.media_audio_mono) ?: "Mono"

    fun stereo(): String = source?.getString(R.string.media_audio_stereo) ?: "Stereo"

    fun channels(count: Int): String = source?.getString(R.string.media_audio_channels, count) ?: "$count ch"

    fun standardDefinition(): String = source?.getString(R.string.media_resolution_sd) ?: "SD"
}

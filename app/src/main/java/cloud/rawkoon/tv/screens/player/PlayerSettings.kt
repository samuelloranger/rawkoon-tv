package cloud.rawkoon.tv.screens.player

import android.content.Context
import cloud.rawkoon.tv.data.MatchDisplay
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.data.SkipMode
import org.jellyfin.sdk.model.api.MediaSegmentType

/**
 * What the player reads from the settings when it opens. A change in Settings applies to the
 * next time the player opens, never to one already playing.
 */
class PlayerSettings private constructor(
    val skipBackMs: Long,
    val skipForwardMs: Long,
    private val rewindOnResumeMs: Long,
    private val segmentModes: Map<MediaSegmentType, SkipMode>,
    val autoplayNext: Boolean,
    val countdownSeconds: Int,
    val stillWatchingAfter: Int,
    val matchDisplay: MatchDisplay,
) {
    /** What to do with a segment of [type]; types the server doesn't list as skippable are left alone. */
    fun modeFor(type: MediaSegmentType): SkipMode = segmentModes[type] ?: SkipMode.NONE

    /** Where playback of an item with a saved [resumeMs] should begin: a little before it, if so set. */
    fun startPosition(resumeMs: Long): Long = rewoundStart(resumeMs, rewindOnResumeMs)

    companion object {
        fun read(context: Context) = PlayerSettings(
            skipBackMs = Prefs.skipBackSeconds(context) * 1_000L,
            skipForwardMs = Prefs.skipForwardSeconds(context) * 1_000L,
            rewindOnResumeMs = Prefs.resumeRewindSeconds(context) * 1_000L,
            segmentModes = mapOf(
                MediaSegmentType.INTRO to Prefs.skipMode(context, Prefs.SKIP_INTRO),
                MediaSegmentType.RECAP to Prefs.skipMode(context, Prefs.SKIP_RECAP),
                MediaSegmentType.PREVIEW to Prefs.skipMode(context, Prefs.SKIP_PREVIEW),
                MediaSegmentType.OUTRO to Prefs.skipMode(context, Prefs.SKIP_CREDITS),
            ),
            autoplayNext = Prefs.autoplayNext(context),
            countdownSeconds = Prefs.autoplayCountdownSeconds(context),
            stillWatchingAfter = Prefs.stillWatchingEpisodes(context),
            matchDisplay = Prefs.matchDisplay(context),
        )
    }
}

/** [resumeMs] moved back by [rewindMs], never before the start. A zero [resumeMs] (not resuming) is left at zero. */
fun rewoundStart(resumeMs: Long, rewindMs: Long): Long = if (resumeMs > 0) (resumeMs - rewindMs).coerceAtLeast(0) else 0

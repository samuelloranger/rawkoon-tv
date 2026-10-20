package cloud.rawkoon.tv.screens.player

import cloud.rawkoon.tv.data.SkipMode
import org.jellyfin.sdk.model.api.MediaSegmentType

/** What the player does as the playhead sits inside a segment. */
enum class SkipAction {
    /** Show the skip button. */
    SHOW_BUTTON,

    /** Seek to the end of the segment, once. */
    SKIP,

    /** The credits of an episode with a next one: carry on as at the end of the episode. */
    GO_TO_NEXT_EPISODE,

    /** Ignore the segment. */
    NOTHING,
}

/**
 * The decision for a segment of [type] under [mode]. [alreadySkipped] says the player already
 * skipped this segment by itself: someone who seeks back into it gets the button rather than
 * being thrown out again. [hasNext] says an episode follows.
 */
fun skipAction(type: MediaSegmentType, mode: SkipMode, alreadySkipped: Boolean, hasNext: Boolean): SkipAction = when (mode) {
    SkipMode.NONE -> SkipAction.NOTHING
    SkipMode.BUTTON -> SkipAction.SHOW_BUTTON
    SkipMode.AUTO -> when {
        alreadySkipped -> SkipAction.SHOW_BUTTON
        type == MediaSegmentType.OUTRO && hasNext -> SkipAction.GO_TO_NEXT_EPISODE
        else -> SkipAction.SKIP
    }
}

/** How the up-next button shows: counting down to the next episode, or waiting for OK. */
data class UpNextPlan(
    /** Seconds until the next episode starts by itself, or null to wait for OK. */
    val countdownSeconds: Int?,
    /** Whether the button asks "Still watching?". */
    val stillWatching: Boolean,
)

/**
 * Counts the episodes that started by themselves in a row, so the player can stop and ask
 * whether anyone is still there. A press of any key means someone is, and starts the count over.
 * [limit] is the number of such episodes after which to ask; 0 never asks.
 */
class AutoplayTracker(private val limit: Int) {
    var count = 0
        private set

    /** An episode began without anyone pressing a key. */
    fun onAutoStarted() {
        count++
    }

    /** Any key pressed in the player. */
    fun onKey() {
        count = 0
    }

    /** True when the next end should ask instead of playing on. */
    val shouldAsk: Boolean get() = limit > 0 && count >= limit

    /** How the up-next button should read right now, given the "play next automatically" setting and its countdown. */
    fun plan(autoplay: Boolean, countdownSeconds: Int): UpNextPlan = when {
        shouldAsk -> UpNextPlan(countdownSeconds = null, stillWatching = true)
        autoplay -> UpNextPlan(countdownSeconds, stillWatching = false)
        else -> UpNextPlan(countdownSeconds = null, stillWatching = false)
    }
}

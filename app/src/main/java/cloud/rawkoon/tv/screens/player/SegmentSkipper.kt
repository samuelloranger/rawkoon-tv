package cloud.rawkoon.tv.screens.player

import android.content.Context
import android.os.Handler
import android.view.View
import androidx.media3.common.Player
import android.widget.Toast
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.data.SkipMode
import cloud.rawkoon.tv.playback.Segment
import cloud.rawkoon.tv.ui.showRising
import org.jellyfin.sdk.model.api.MediaSegmentType

/**
 * The skip button over an intro, recap, preview or the credits, and the up-next button after the
 * end. What each kind of segment gets (the button, an automatic skip, nothing) comes from
 * [settings]. [hasNext] says whether an episode follows (the credits button then reads "Next
 * episode" and plays it); [playNext] starts it, with `auto` true when nobody asked for it.
 * [plan] says how the up-next button should read (counting down, or waiting for OK).
 */
class SegmentSkipper(
    private val context: Context,
    private val chrome: PlayerChrome,
    private val exo: Player,
    private val main: Handler,
    private val settings: PlayerSettings,
    private val hasNext: () -> Boolean,
    private val playNext: (auto: Boolean) -> Unit,
    private val plan: () -> UpNextPlan,
) {
    var segments: List<Segment> = emptyList()

    private val skip = chrome.skip
    private val upNext = chrome.upNext
    private var activeSegment: Segment? = null

    /** The segments the player already skipped by itself, so seeking back into one shows the button. */
    private val autoSkipped = mutableSetOf<Segment>()

    /** The credits that brought the up-next button up before the end, if they did. */
    private var upNextCredits: Segment? = null
    private var countdown = 0
    private val countdownStep = object : Runnable {
        override fun run() {
            countdown--
            if (countdown <= 0) playNext(true) else {
                upNext.label.text = context.getString(R.string.up_next_in, countdown)
                main.postDelayed(this, 1_000)
            }
        }
    }

    /** True while the up-next button is up. */
    val upNextShown: Boolean get() = upNext.visibility == View.VISIBLE

    init {
        skip.setOnClickListener { skipSegment() }
        upNext.setOnClickListener { playNext(false) }
    }

    /** Acts on the segment [position] is inside, if any: shows the skip button, skips it, or moves on to the next episode. */
    fun update(position: Long) {
        // Seeking back from the credits that brought up the next episode: this one isn't over after all
        upNextCredits?.let { credits ->
            if (position < credits.startMs) {
                upNextCredits = null
                stopCountdown()
                upNext.visibility = View.GONE
            }
        }
        val segment = segments.firstOrNull { position >= it.startMs && position < it.endMs - END_MARGIN_MS && settings.modeFor(it.type) != SkipMode.NONE }
        if (segment == activeSegment) return
        activeSegment = segment
        if (segment == null || upNextShown) {
            hideButton()
            return
        }
        when (skipAction(segment.type, settings.modeFor(segment.type), segment in autoSkipped, hasNext())) {
            SkipAction.NOTHING -> hideButton()
            SkipAction.SKIP -> {
                autoSkipped += segment
                hideButton()
                exo.seekTo(segment.endMs)
                Toast.makeText(context, skippedLabel(segment.type), Toast.LENGTH_SHORT).show()
            }
            SkipAction.GO_TO_NEXT_EPISODE -> {
                autoSkipped += segment
                hideButton()
                showUpNext()
                upNextCredits = segment
            }
            SkipAction.SHOW_BUTTON -> {
                skip.label.text = context.getString(
                    when {
                        segment.type == MediaSegmentType.OUTRO && hasNext() -> R.string.next_episode
                        segment.type == MediaSegmentType.INTRO -> R.string.skip_intro
                        segment.type == MediaSegmentType.RECAP -> R.string.skip_recap
                        segment.type == MediaSegmentType.PREVIEW -> R.string.skip_preview
                        else -> R.string.skip_credits
                    },
                )
                skip.showRising()
                if (!chrome.shown) skip.requestFocus()
            }
        }
    }

    private fun hideButton() {
        if (skip.hasFocus()) chrome.setChrome(chrome.shown)
        skip.visibility = View.GONE
    }

    private fun skippedLabel(type: MediaSegmentType) = context.getString(
        when (type) {
            MediaSegmentType.INTRO -> R.string.skipped_intro
            MediaSegmentType.RECAP -> R.string.skipped_recap
            MediaSegmentType.PREVIEW -> R.string.skipped_preview
            else -> R.string.skipped_credits
        },
    )

    /** Milliseconds until the skip button should next appear or go, or null when no segment is ahead. Segments set to do nothing don't count. */
    fun nextEdge(position: Long, min: Long, max: Long): Long? =
        segments.filter { settings.modeFor(it.type) != SkipMode.NONE }
            .flatMap { listOf(it.startMs, it.endMs - END_MARGIN_MS) }.filter { it > position }.minOrNull()
            ?.let { (it - position).coerceIn(min, max) }

    /** Skips the segment the button is up for; the credits of an episode with a next one play it instead. */
    fun skipSegment() {
        val segment = activeSegment ?: return
        if (segment.type == MediaSegmentType.OUTRO && hasNext()) {
            playNext(false)
            return
        }
        exo.seekTo(segment.endMs)
        skip.visibility = View.GONE
        activeSegment = null
        chrome.setChrome(chrome.shown)
    }

    /**
     * Up next: a countdown to the next episode (OK plays at once, Back leaves), or just the
     * button, waiting for OK, when the countdown is off or it is time to ask "Still watching?".
     */
    fun showUpNext() {
        skip.visibility = View.GONE
        val plan = plan()
        stopCountdown()
        val seconds = plan.countdownSeconds
        when {
            plan.stillWatching -> upNext.label.text = context.getString(R.string.still_watching)
            seconds == null -> upNext.label.text = context.getString(R.string.next_episode)
            else -> {
                countdown = seconds
                upNext.label.text = context.getString(R.string.up_next_in, countdown)
                main.postDelayed(countdownStep, 1_000)
            }
        }
        upNext.showRising()
        upNext.requestFocus()
    }

    fun stopCountdown() {
        main.removeCallbacks(countdownStep)
    }

    /** Hides both buttons, for a new item. */
    fun hide() {
        stopCountdown()
        upNextCredits = null
        activeSegment = null
        skip.visibility = View.GONE
        upNext.visibility = View.GONE
    }

    /** Forgets the segments of the item that was playing. */
    fun reset() {
        segments = emptyList()
        activeSegment = null
        upNextCredits = null
        autoSkipped.clear()
    }

    private companion object {
        const val END_MARGIN_MS = 1_000L
    }
}

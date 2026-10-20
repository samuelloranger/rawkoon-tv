package cloud.rawkoon.tv.screens.player

import android.content.Context
import android.os.Handler
import android.view.View
import androidx.media3.common.Player
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.playback.Segment
import org.jellyfin.sdk.model.api.MediaSegmentType

/**
 * The skip button over an intro, recap, preview or the credits, and the "next episode in n
 * seconds" button after the end. [hasNext] says whether an episode follows (the credits button
 * then reads "Next episode" and plays it); [playNext] starts it.
 */
class SegmentSkipper(
    private val context: Context,
    private val chrome: PlayerChrome,
    private val exo: Player,
    private val main: Handler,
    private val hasNext: () -> Boolean,
    private val playNext: () -> Unit,
) {
    var segments: List<Segment> = emptyList()

    private val skip = chrome.skip
    private val upNext = chrome.upNext
    private var activeSegment: Segment? = null
    private var countdown = 0
    private val countdownStep = object : Runnable {
        override fun run() {
            countdown--
            if (countdown <= 0) playNext() else {
                upNext.label.text = context.getString(R.string.up_next_in, countdown)
                main.postDelayed(this, 1_000)
            }
        }
    }

    /** True while the up-next button is up. */
    val upNextShown: Boolean get() = upNext.visibility == View.VISIBLE

    init {
        skip.setOnClickListener { skipSegment() }
        upNext.setOnClickListener { playNext() }
    }

    /** Shows the skip button while [position] is inside a segment. */
    fun update(position: Long) {
        val segment = segments.firstOrNull { position >= it.startMs && position < it.endMs - END_MARGIN_MS }
        if (segment == activeSegment) return
        activeSegment = segment
        if (segment == null || upNextShown) {
            if (skip.hasFocus()) chrome.setChrome(chrome.shown)
            skip.visibility = View.GONE
            return
        }
        skip.label.text = context.getString(
            when {
                segment.type == MediaSegmentType.OUTRO && hasNext() -> R.string.next_episode
                segment.type == MediaSegmentType.INTRO -> R.string.skip_intro
                segment.type == MediaSegmentType.RECAP -> R.string.skip_recap
                segment.type == MediaSegmentType.PREVIEW -> R.string.skip_preview
                else -> R.string.skip_credits
            },
        )
        skip.visibility = View.VISIBLE
        if (!chrome.shown) skip.requestFocus()
    }

    /** Milliseconds until the skip button should next appear or go, or null when no segment is ahead. */
    fun nextEdge(position: Long, min: Long, max: Long): Long? =
        segments.flatMap { listOf(it.startMs, it.endMs - END_MARGIN_MS) }.filter { it > position }.minOrNull()
            ?.let { (it - position).coerceIn(min, max) }

    /** Skips the segment the button is up for; the credits of an episode with a next one play it instead. */
    fun skipSegment() {
        val segment = activeSegment ?: return
        if (segment.type == MediaSegmentType.OUTRO && hasNext()) {
            playNext()
            return
        }
        exo.seekTo(segment.endMs)
        skip.visibility = View.GONE
        activeSegment = null
        chrome.setChrome(chrome.shown)
    }

    /** Up next: a short countdown, OK plays at once, Back leaves. */
    fun showUpNext() {
        skip.visibility = View.GONE
        countdown = UP_NEXT_SECONDS
        upNext.label.text = context.getString(R.string.up_next_in, countdown)
        upNext.visibility = View.VISIBLE
        upNext.requestFocus()
        main.postDelayed(countdownStep, 1_000)
    }

    fun stopCountdown() {
        main.removeCallbacks(countdownStep)
    }

    /** Hides both buttons, for a new item. */
    fun hide() {
        activeSegment = null
        skip.visibility = View.GONE
        upNext.visibility = View.GONE
    }

    /** Forgets the segments of the item that was playing. */
    fun reset() {
        segments = emptyList()
        activeSegment = null
    }

    private companion object {
        const val END_MARGIN_MS = 1_000L
        const val UP_NEXT_SECONDS = 8
    }
}

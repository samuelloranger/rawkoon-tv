package cloud.rawkoon.tv.screens.player

import android.content.Context
import android.os.Handler
import android.view.KeyEvent
import android.view.View
import androidx.media3.common.Player
import cloud.rawkoon.tv.playback.Trickplay
import cloud.rawkoon.tv.playback.TrickplayLoader
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.dpf

/**
 * Scrubbing on the seek bar: Left and Right move a target (in bigger steps the longer the key is
 * held), a thumbnail or time bubble follows it, and the seek happens when OK is pressed, focus
 * leaves the bar, or the keys have been still for a moment. [refresh] redraws the progress.
 */
class ScrubController(
    private val context: Context,
    private val chrome: PlayerChrome,
    private val exo: Player,
    private val loader: TrickplayLoader,
    private val main: Handler,
    private val refresh: () -> Unit,
) {
    /** The thumbnails for the current item, once Jellyfin has said whether it has any. */
    var trickplay: Trickplay? = null

    private val seekBar = chrome.seekBar
    private val commit = Runnable { commitScrub() }

    /** True while a scrub target is set and not yet applied. */
    val scrubbing: Boolean get() = seekBar.scrubMs != null

    init {
        seekBar.setOnKeyListener { _, keyCode, event -> onSeekBarKey(keyCode, event) }
        seekBar.setOnFocusChangeListener { _, focused ->
            if (!focused) main.post { if (seekBar.scrubMs != null && !seekBar.isFocused) commitScrub() }
        }
    }

    private fun onSeekBarKey(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                scrub(if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1, event.repeatCount)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                commitScrub()
                true
            }
            else -> false
        }
    }

    /** Moves the scrub target; the longer the key is held, the bigger the steps. */
    fun scrub(direction: Int, repeat: Int) {
        val from = seekBar.scrubMs ?: exo.currentPosition
        val to = (from + direction * stepFor(repeat)).coerceIn(0, seekBar.durationMs.coerceAtLeast(0))
        seekBar.scrubMs = to
        refresh()
        showThumb(to)
        chrome.cancelHide()
        main.removeCallbacks(commit)
        main.postDelayed(commit, SCRUB_COMMIT_MS)
    }

    private fun showThumb(positionMs: Long) {
        val tp = trickplay
        chrome.thumbTime.text = formatTime(positionMs)
        // Without trickplay thumbnails on the server, a small time bubble follows the knob instead
        val w = context.dp(if (tp == null) PlayerChrome.BUBBLE_W else PlayerChrome.THUMB_W)
        val h = context.dp(if (tp == null) PlayerChrome.BUBBLE_H else PlayerChrome.THUMB_H)
        val frame = chrome.thumbFrame
        val params = frame.layoutParams
        if (params.width != w) {
            params.width = w
            params.height = h
            frame.layoutParams = params
            chrome.thumb.visibility = if (tp == null) View.GONE else View.VISIBLE
        }
        val x = seekBar.left + seekBar.xFor(positionMs) - w / 2f
        frame.translationX = x.coerceIn(context.dpf(24f), ((frame.parent as View).width - w - context.dp(24)).toFloat())
        frame.visibility = View.VISIBLE
        if (tp != null) loader.load(tp, positionMs) { chrome.thumb.setImageBitmap(it) }
    }

    /** Seeks to the scrub target, if there is one. */
    fun commitScrub() {
        main.removeCallbacks(commit)
        val target = seekBar.scrubMs ?: return
        seekBar.scrubMs = null
        clearThumb()
        exo.seekTo(target)
        refresh()
        chrome.scheduleHide()
    }

    /** Drops the scrub target without seeking. */
    fun cancel() {
        seekBar.scrubMs = null
        clearThumb()
        refresh()
    }

    /** Forgets the thumbnails of the item that was playing. */
    fun reset() {
        trickplay = null
        loader.clear()
        chrome.thumb.setImageDrawable(null)
    }

    private fun clearThumb() {
        loader.clear()
        chrome.thumb.setImageDrawable(null)
        chrome.thumbFrame.visibility = View.INVISIBLE
    }

    companion object {
        private const val SCRUB_COMMIT_MS = 900L

        /** The scrub step for a key held down for [repeat] repeats. */
        fun stepFor(repeat: Int): Long = when {
            repeat > 20 -> 120_000L
            repeat > 8 -> 60_000L
            repeat > 2 -> 30_000L
            else -> 10_000L
        }
    }
}

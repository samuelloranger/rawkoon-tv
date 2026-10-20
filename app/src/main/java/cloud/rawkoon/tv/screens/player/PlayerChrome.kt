package cloud.rawkoon.tv.screens.player

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import cloud.rawkoon.tv.App
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.ui.CenteredRow
import cloud.rawkoon.tv.ui.CircleButton
import cloud.rawkoon.tv.ui.FlatView
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.Icon
import cloud.rawkoon.tv.ui.LogoView
import cloud.rawkoon.tv.ui.PillButton
import cloud.rawkoon.tv.ui.SeekBar
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.clock
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.dpf
import cloud.rawkoon.tv.ui.fill
import cloud.rawkoon.tv.ui.label
import cloud.rawkoon.tv.ui.noClip
import cloud.rawkoon.tv.ui.rounded

/**
 * The player's views and what shows them: the title and logo, the seek bar, the times, the
 * controls and the skip and up-next buttons, built over [root] (which already holds the video).
 * Shows and hides the controls with a fade, moves the subtitles clear of them, and hides them
 * again after a while of playing. The rest of the player talks to the views through here.
 *
 * [onShow] runs as the controls come up (to refresh what they show) and [onSettled] after every
 * show or hide, whether or not it changed anything.
 */
@OptIn(UnstableApi::class)
class PlayerChrome(
    private val context: Context,
    private val app: App,
    private val root: FrameLayout,
    private val surface: PlayerView,
    private val main: Handler,
    private val isPlaying: () -> Boolean,
    private val dialogOpen: () -> Boolean,
    private val onShow: () -> Unit,
    private val onSettled: () -> Unit,
) {
    /** The controls layer: gradients, title, seek bar, times, buttons. */
    private val overlay = FrameLayout(context).noClip()
    private val logo = LogoView(context)
    private val title = label(context, 24f, Tokens.textPrimary, Fonts.title(context)).apply { gravity = Gravity.BOTTOM }
    private val subtitle = label(context, 14f, Tokens.textSoft, Fonts.medium(context))
    private val elapsed = label(context, 13f, Tokens.textSoft, Fonts.bold(context))
    private val remaining = label(context, 13f, Tokens.textSoft, Fonts.bold(context)).apply { gravity = Gravity.END }
    private val spinner = ProgressBar(context)
    private var shownElapsed = -1L
    private var shownRemaining = -1L

    val seekBar = SeekBar(context)
    val thumb = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    val thumbFrame = FrameLayout(context)
    val thumbTime = label(context, 12f, Tokens.textPrimary, Fonts.bold(context)).apply { gravity = Gravity.CENTER }
    val subtitlesButton = CircleButton(context, Icon.SUBTITLES, context.getString(R.string.subtitles))
    val audioButton = CircleButton(context, Icon.AUDIO, context.getString(R.string.audio))
    val rewind = CircleButton(context, Icon.REWIND, context.getString(R.string.rewind))
    val playPause = CircleButton(context, Icon.PAUSE, context.getString(R.string.play_pause), filled = true)
    val forward = CircleButton(context, Icon.FORWARD, context.getString(R.string.forward))
    val nextButton = CircleButton(context, Icon.NEXT_TRACK, context.getString(R.string.next_episode))
    val skip = PillButton(context, context.getString(R.string.skip_intro), Icon.SKIP, height = 40)
    val upNext = PillButton(context, context.getString(R.string.play_now), Icon.PLAY, primary = true, height = 40)

    /** Whether the controls are up. */
    var shown = true
        private set

    private val hide = Runnable { setChrome(false) }

    init {
        // The controls start up: the subtitles sit above them from the first frame
        surface.subtitleView?.setBottomPaddingFraction(0.27f)
        build()
        root.addView(overlay, fill())
        root.addView(spinner, FrameLayout.LayoutParams(context.dp(48), context.dp(48), Gravity.CENTER))
    }

    private fun build() {
        overlay.addView(FlatView(context).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xB3000000.toInt(), 0)) },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(140)))
        overlay.addView(FlatView(context).apply { background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xE6000000.toInt(), 0x99000000.toInt(), 0)) },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(240), Gravity.BOTTOM))
        val box = FrameLayout(context)
        box.addView(logo, fill())
        box.addView(title, fill())
        overlay.addView(box, at(48, 28, context.dp(300), context.dp(48)))
        overlay.addView(subtitle, at(48, 82, context.dp(700), ViewGroup.LayoutParams.WRAP_CONTENT))
        overlay.addView(clock(context), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END).apply {
            topMargin = context.dp(22)
            marginEnd = context.dp(32)
        })

        thumbFrame.background = rounded(Color.BLACK, context.dpf(8f))
        thumbFrame.clipToOutline = true
        thumbFrame.addView(thumb, fill())
        thumbFrame.addView(thumbTime, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(22), Gravity.BOTTOM).apply {
            thumbTime.background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xCC000000.toInt(), 0))
        })
        thumbFrame.visibility = View.INVISIBLE
        overlay.addView(thumbFrame, FrameLayout.LayoutParams(context.dp(THUMB_W), context.dp(THUMB_H), Gravity.BOTTOM).apply { bottomMargin = context.dp(120) })

        overlay.addView(seekBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(24), Gravity.BOTTOM).apply {
            leftMargin = context.dp(38)
            rightMargin = context.dp(38)
            bottomMargin = context.dp(92)
        })
        val times = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        times.addView(elapsed, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        times.addView(remaining, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        overlay.addView(times, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            leftMargin = context.dp(48)
            rightMargin = context.dp(48)
            bottomMargin = context.dp(74)
        })

        // Play/pause sits in the exact middle of the screen, whatever is on either side of it
        fun group(vararg views: View, gapBefore: Int = -1) = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            views.forEachIndexed { i, v ->
                if (i == gapBefore) addView(View(context), LinearLayout.LayoutParams(context.dp(36), 1))
                addView(v)
            }
        }.noClip()
        val controls = CenteredRow(
            context,
            start = group(subtitlesButton, audioButton, rewind, gapBefore = 2),
            middle = playPause,
            end = group(forward, nextButton, gapBefore = 1),
            gap = 0,
        )
        overlay.addView(controls, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            bottomMargin = context.dp(16)
        })

        // The skip and up-next buttons sit under the controls layer, so they stay up while it fades
        root.addView(skip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = context.dp(44)
            bottomMargin = context.dp(126)
        })
        root.addView(upNext, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = context.dp(44)
            bottomMargin = context.dp(126)
        })
        skip.visibility = View.GONE
        upNext.visibility = View.GONE
    }

    private fun at(left: Int, top: Int, w: Int, h: Int) = FrameLayout.LayoutParams(w, h).apply {
        leftMargin = context.dp(left)
        topMargin = context.dp(top)
    }

    /** Puts a new item's title (or its logo, when it has one) and subtitle line up. [isCurrent] says whether the logo is still wanted when it arrives. */
    fun showTitle(target: MediaItem, isCurrent: () -> Boolean) {
        title.text = ""
        logo.bitmap = null
        val url = target.logoUrl
        if (url == null) {
            title.text = target.headerTitle
        } else {
            val w = context.dp(300)
            val h = context.dp(48)
            app.images.load("$url&maxWidth=$w", w, h, 0f, contain = true, isCurrent = isCurrent) { logo.bitmap = it }
        }
        subtitle.text = if (target.isEpisode) listOfNotNull(target.episodeLabel, target.title).joinToString(" — ") else target.metaLine()
    }

    fun showSpinner(visible: Boolean) {
        spinner.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** Forgets which times are on screen, so the next [showProgress] writes them. */
    fun resetProgress() {
        shownElapsed = -1
        shownRemaining = -1
    }

    /** Updates the bar and the times; each text only changes when the second it shows does. */
    fun showProgress(durationMs: Long, positionMs: Long, bufferedMs: Long) {
        seekBar.update(durationMs, positionMs, bufferedMs)
        val at = seekBar.scrubMs ?: positionMs
        if (at / 1000 != shownElapsed) {
            shownElapsed = at / 1000
            elapsed.text = formatTime(at)
        }
        val left = (durationMs - at).coerceAtLeast(0)
        if (left / 1000 != shownRemaining) {
            shownRemaining = left / 1000
            remaining.text = context.getString(R.string.time_remaining, formatTime(left))
        }
    }

    fun setChrome(show: Boolean, autoHide: Boolean = true) {
        val changed = show != shown
        shown = show
        if (changed) {
            // Subtitles rise above the controls while they show
            surface.subtitleView?.setBottomPaddingFraction(if (show) 0.27f else 0.08f)
            overlay.animate().cancel()
            // A layer during the fade: the alpha then applies to one texture, not to a buffer drawn every frame
            overlay.animate().alpha(if (show) 1f else 0f).setDuration(if (show) 160 else 260).withLayer().start()
            overlay.descendantFocusability = if (show) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        if (show) {
            onShow()
            // An open track dialog keeps the focus
            if (!dialogOpen() && !overlay.hasFocus() && !skip.hasFocus() && !upNext.hasFocus()) playPause.requestFocus()
            if (autoHide) scheduleHide() else main.removeCallbacks(hide)
        } else {
            main.removeCallbacks(hide)
            thumbFrame.visibility = View.INVISIBLE
            // With the controls hidden, the skip button keeps focus when it is up; otherwise nothing has it
            if (skip.visibility == View.VISIBLE) skip.requestFocus() else root.requestFocus()
        }
        onSettled()
    }

    /** Hides the controls in a few seconds, if the film is playing and no dialog is open. */
    fun scheduleHide() {
        main.removeCallbacks(hide)
        if (isPlaying() && !dialogOpen()) main.postDelayed(hide, HIDE_MS)
    }

    fun cancelHide() {
        main.removeCallbacks(hide)
    }

    companion object {
        const val THUMB_W = 192
        const val THUMB_H = 108
        const val BUBBLE_W = 72
        const val BUBBLE_H = 26
        private const val HIDE_MS = 4_000L
    }
}

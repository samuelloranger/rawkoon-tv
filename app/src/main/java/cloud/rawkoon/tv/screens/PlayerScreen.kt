package cloud.rawkoon.tv.screens

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.Screen
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.playback.PlaybackService
import cloud.rawkoon.tv.playback.Segment
import cloud.rawkoon.tv.playback.Stream
import cloud.rawkoon.tv.playback.Trickplay
import cloud.rawkoon.tv.playback.TrickplayLoader
import cloud.rawkoon.tv.playback.VideoPlayer
import cloud.rawkoon.tv.ui.CenteredRow
import cloud.rawkoon.tv.ui.ChoiceDialog
import cloud.rawkoon.tv.ui.CircleButton
import cloud.rawkoon.tv.ui.FlatView
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.Icon
import cloud.rawkoon.tv.ui.LogoView
import cloud.rawkoon.tv.ui.PillButton
import cloud.rawkoon.tv.ui.SeekBar
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.dpf
import cloud.rawkoon.tv.ui.fill
import cloud.rawkoon.tv.ui.label
import cloud.rawkoon.tv.ui.noClip
import cloud.rawkoon.tv.ui.rounded
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.PlayMethod
import java.util.Locale

/**
 * The player: the video full screen, and over it, while shown, the title, the seek bar with
 * trickplay thumbnails and the controls. With the controls hidden, Left and Right seek, OK pauses
 * (or skips the intro when the skip button is up), and Back stops. Progress goes back to Jellyfin
 * as it plays, so "Continue watching" picks up where you left.
 */
@OptIn(UnstableApi::class)
class PlayerScreen(activity: MainActivity, private val session: JellyfinSession, private var item: MediaItem) : Screen(activity) {
    override val view = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
    private val service = PlaybackService(context, session)
    private val player = VideoPlayer(context, service.authorization)
    private val trickplayLoader = TrickplayLoader(context, service.authorization)
    private val main = Handler(Looper.getMainLooper())

    private val surface = PlayerView(context).apply {
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        keepScreenOn = true
        setShutterBackgroundColor(Color.BLACK)
        subtitleView?.apply {
            setStyle(CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, Fonts.medium(context)))
            setFractionalTextSize(0.058f)
            setBottomPaddingFraction(0.08f)
        }
    }
    private val chrome = FrameLayout(context).noClip()
    private val logo = LogoView(context)
    private val title = label(context, 24f, Tokens.textPrimary, Fonts.title(context)).apply { gravity = Gravity.BOTTOM }
    private val subtitle = label(context, 14f, Tokens.textSoft, Fonts.medium(context))
    private val seekBar = SeekBar(context)
    private val elapsed = label(context, 13f, Tokens.textSoft, Fonts.bold(context))
    private val remaining = label(context, 13f, Tokens.textSoft, Fonts.bold(context)).apply { gravity = Gravity.END }
    private val thumb = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val thumbFrame = FrameLayout(context)
    private val thumbTime = label(context, 12f, Tokens.textPrimary, Fonts.bold(context)).apply { gravity = Gravity.CENTER }
    private val subtitlesButton = CircleButton(context, Icon.SUBTITLES, string(R.string.subtitles))
    private val audioButton = CircleButton(context, Icon.AUDIO, string(R.string.audio))
    private val rewind = CircleButton(context, Icon.REWIND, string(R.string.rewind))
    private val playPause = CircleButton(context, Icon.PAUSE, string(R.string.play_pause), filled = true)
    private val forward = CircleButton(context, Icon.FORWARD, string(R.string.forward))
    private val nextButton = CircleButton(context, Icon.NEXT_TRACK, string(R.string.next_episode))
    private val skip = PillButton(context, string(R.string.skip_intro), Icon.SKIP, height = 40)
    private val upNext = PillButton(context, string(R.string.play_now), Icon.PLAY, primary = true, height = 40)
    private val spinner = ProgressBar(context)
    private var dialog: ChoiceDialog? = null

    private var stream: Stream? = null
    private var segments: List<Segment> = emptyList()
    private var trickplay: Trickplay? = null
    private var next: MediaItem? = null
    private var started = false
    private var triedTranscode = false
    private var loadJob: Job? = null
    private var chromeShown = true
    private var activeSegment: Segment? = null
    private var countdown = 0

    private val hideChrome = Runnable { setChrome(false) }
    private val tick = object : Runnable {
        override fun run() {
            updateProgress()
            main.postDelayed(this, TICK_MS)
        }
    }
    private val report = object : Runnable {
        override fun run() {
            reportProgress()
            main.postDelayed(this, REPORT_MS)
        }
    }
    private val commitScrub = Runnable { commitScrub() }
    private val countdownStep = object : Runnable {
        override fun run() {
            countdown--
            if (countdown <= 0) playNext() else {
                upNext.label.text = string(R.string.up_next_in, countdown)
                main.postDelayed(this, 1_000)
            }
        }
    }

    init {
        surface.player = player.exo
        view.addView(surface, fill())
        buildChrome()
        view.addView(chrome, fill())
        view.addView(spinner, FrameLayout.LayoutParams(context.dp(48), context.dp(48), Gravity.CENTER))
    }

    private fun buildChrome() {
        chrome.addView(FlatView(context).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xB3000000.toInt(), 0)) },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(140)))
        chrome.addView(FlatView(context).apply { background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xE6000000.toInt(), 0x99000000.toInt(), 0)) },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(240), Gravity.BOTTOM))
        val box = FrameLayout(context)
        box.addView(logo, fill())
        box.addView(title, fill())
        chrome.addView(box, at(48, 28, context.dp(300), context.dp(48)))
        chrome.addView(subtitle, at(48, 82, context.dp(700), ViewGroup.LayoutParams.WRAP_CONTENT))
        chrome.addView(clock(context), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END).apply {
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
        chrome.addView(thumbFrame, FrameLayout.LayoutParams(context.dp(THUMB_W), context.dp(THUMB_H), Gravity.BOTTOM).apply { bottomMargin = context.dp(120) })

        chrome.addView(seekBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(24), Gravity.BOTTOM).apply {
            leftMargin = context.dp(38)
            rightMargin = context.dp(38)
            bottomMargin = context.dp(92)
        })
        val times = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        times.addView(elapsed, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        times.addView(remaining, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        chrome.addView(times, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
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
        chrome.addView(controls, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            bottomMargin = context.dp(16)
        })

        view.addView(skip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = context.dp(44)
            bottomMargin = context.dp(126)
        })
        view.addView(upNext, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = context.dp(44)
            bottomMargin = context.dp(126)
        })
        skip.visibility = View.GONE
        upNext.visibility = View.GONE

        subtitlesButton.setOnClickListener { chooseSubtitles() }
        audioButton.setOnClickListener { chooseAudio() }
        rewind.setOnClickListener { seekBy(-10_000) }
        forward.setOnClickListener { seekBy(30_000) }
        playPause.setOnClickListener { togglePause() }
        nextButton.setOnClickListener { playNext() }
        skip.setOnClickListener { skipSegment() }
        upNext.setOnClickListener { playNext() }
        seekBar.setOnKeyListener { _, keyCode, event -> onSeekBarKey(keyCode, event) }
        seekBar.setOnFocusChangeListener { _, focused ->
            seekBar.invalidate()
            if (!focused) main.post { if (seekBar.scrubMs != null && !seekBar.isFocused) commitScrub() }
        }
    }

    private fun at(left: Int, top: Int, w: Int, h: Int) = FrameLayout.LayoutParams(w, h).apply {
        leftMargin = context.dp(left)
        topMargin = context.dp(top)
    }

    // --- Loading -------------------------------------------------------------------------------

    private fun start(target: MediaItem, startMs: Long, audio: Int? = null, subtitleIndex: Int? = null, direct: Boolean = true) {
        item = target
        app.lastPlayed = target
        started = false
        showTitle(target)
        skip.visibility = View.GONE
        upNext.visibility = View.GONE
        spinner.visibility = View.VISIBLE
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val s = service.stream(target, startMs, audio, subtitleIndex, direct = direct)
                stream = s
                player.load(s, startMs)
                nextButton.visibility = if (target.kind == MediaItem.Kind.EPISODE) View.VISIBLE else View.GONE
                launch { segments = service.segments(target.id) }
                launch { trickplay = service.trickplay(target.id, s.mediaSourceId, context.dp(THUMB_W)) }
                launch { next = if (target.kind == MediaItem.Kind.EPISODE) service.nextEpisode(target) else null }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e.message ?: e.javaClass.simpleName)
            }
        }
        setChrome(true)
    }

    private fun showTitle(target: MediaItem) {
        title.text = ""
        logo.bitmap = null
        val url = target.logoUrl
        if (url == null) {
            title.text = target.headerTitle
        } else {
            val w = context.dp(300)
            val h = context.dp(48)
            app.images.load("$url&maxWidth=$w", w, h, 0f, contain = true, isCurrent = { item === target }) { logo.bitmap = it }
        }
        subtitle.text = if (target.kind == MediaItem.Kind.EPISODE) listOfNotNull(target.episodeLabel, target.title).joinToString(" — ") else target.metaLine()
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            spinner.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            val s = stream ?: return
            if (state == Player.STATE_READY && !started) {
                started = true
                scope.launch { service.reportStart(s, player.exo.currentPosition, !player.exo.playWhenReady, s.audioIndex, s.subtitleIndex) }
                main.removeCallbacks(report)
                main.postDelayed(report, REPORT_MS)
                scheduleHide()
            }
            if (state == Player.STATE_ENDED) ended()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playPause.setGlyph(if (isPlaying) Icon.PAUSE else Icon.PLAY)
            if (started) reportProgress()
            if (!isPlaying && player.exo.playbackState == Player.STATE_READY) setChrome(true, autoHide = false) else if (isPlaying) scheduleHide()
        }

        override fun onPlayerError(error: PlaybackException) {
            val s = stream
            // A file the TV can't play after all: ask Jellyfin for a transcode, once
            if (s != null && s.method == PlayMethod.DIRECT_PLAY && !triedTranscode) {
                triedTranscode = true
                start(item, player.exo.currentPosition, s.audioIndex, s.subtitleIndex, direct = false)
                return
            }
            fail(error.errorCodeName)
        }
    }

    private fun fail(reason: String) {
        spinner.visibility = View.GONE
        Toast.makeText(context, string(R.string.playback_error, reason), Toast.LENGTH_LONG).show()
        close()
    }

    // --- Progress, segments, end ---------------------------------------------------------------

    private fun updateProgress() {
        val exo = player.exo
        val duration = exo.duration.takeIf { it > 0 } ?: stream?.durationMs ?: 0L
        val position = exo.currentPosition
        seekBar.durationMs = duration
        seekBar.positionMs = position
        seekBar.bufferedMs = exo.bufferedPosition
        seekBar.invalidate()
        elapsed.text = time(seekBar.scrubMs ?: position)
        remaining.text = "−" + time((duration - (seekBar.scrubMs ?: position)).coerceAtLeast(0))
        updateSegment(position)
    }

    /** Shows the skip button while inside an intro, recap, preview or the credits. */
    private fun updateSegment(position: Long) {
        val segment = segments.firstOrNull { position >= it.startMs && position < it.endMs - 1_000 }
        if (segment == activeSegment) return
        activeSegment = segment
        if (segment == null || upNext.visibility == View.VISIBLE) {
            if (skip.hasFocus()) setChrome(chromeShown)
            skip.visibility = View.GONE
            return
        }
        skip.label.text = string(
            when {
                segment.type == MediaSegmentType.OUTRO && next != null -> R.string.next_episode
                segment.type == MediaSegmentType.INTRO -> R.string.skip_intro
                segment.type == MediaSegmentType.RECAP -> R.string.skip_recap
                segment.type == MediaSegmentType.PREVIEW -> R.string.skip_preview
                else -> R.string.skip_credits
            },
        )
        skip.visibility = View.VISIBLE
        if (!chromeShown) skip.requestFocus()
    }

    private fun skipSegment() {
        val segment = activeSegment ?: return
        if (segment.type == MediaSegmentType.OUTRO && next != null) {
            playNext()
            return
        }
        player.exo.seekTo(segment.endMs)
        skip.visibility = View.GONE
        activeSegment = null
        setChrome(chromeShown)
    }

    private fun ended() {
        val s = stream ?: return close()
        scope.launch { service.reportStop(s, player.exo.duration.coerceAtLeast(0)) }
        stream = null
        if (next == null) return close()
        // Up next: a short countdown, OK plays at once, Back leaves
        skip.visibility = View.GONE
        countdown = UP_NEXT_SECONDS
        upNext.label.text = string(R.string.up_next_in, countdown)
        upNext.visibility = View.VISIBLE
        upNext.requestFocus()
        main.postDelayed(countdownStep, 1_000)
    }

    private fun playNext() {
        val target = next ?: return
        main.removeCallbacks(countdownStep)
        stream?.let { s -> scope.launch { service.reportStop(s, player.exo.currentPosition) } }
        stream = null
        next = null
        segments = emptyList()
        trickplay = null
        triedTranscode = false
        start(target, target.resumeMs)
    }

    private fun reportProgress() {
        val s = stream ?: return
        if (!started) return
        scope.launch { service.reportProgress(s, player.exo.currentPosition, !player.exo.isPlaying, s.audioIndex, s.subtitleIndex) }
    }

    // --- Chrome --------------------------------------------------------------------------------

    private fun setChrome(show: Boolean, autoHide: Boolean = true) {
        chromeShown = show
        // Subtitles rise above the controls while they show
        surface.subtitleView?.setBottomPaddingFraction(if (show) 0.27f else 0.08f)
        chrome.animate().cancel()
        chrome.animate().alpha(if (show) 1f else 0f).setDuration(if (show) 160 else 260).start()
        chrome.descendantFocusability = if (show) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
        main.removeCallbacks(tick)
        if (show) {
            updateProgress()
            main.post(tick)
            if (!chrome.hasFocus() && !skip.hasFocus() && !upNext.hasFocus()) playPause.requestFocus()
            if (autoHide) scheduleHide() else main.removeCallbacks(hideChrome)
        } else {
            main.removeCallbacks(hideChrome)
            thumbFrame.visibility = View.INVISIBLE
            // With the controls hidden, the skip button keeps focus when it is up; otherwise nothing has it
            if (skip.visibility == View.VISIBLE) skip.requestFocus() else view.requestFocus()
            main.post(tick)
        }
    }

    private fun scheduleHide() {
        main.removeCallbacks(hideChrome)
        if (player.exo.isPlaying && dialog?.isAttachedToWindow != true) main.postDelayed(hideChrome, HIDE_MS)
    }

    private fun togglePause() {
        val exo = player.exo
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    private fun seekBy(deltaMs: Long) {
        val exo = player.exo
        exo.seekTo((exo.currentPosition + deltaMs).coerceIn(0, exo.duration.coerceAtLeast(0)))
        updateProgress()
        scheduleHide()
    }

    // --- Scrubbing -----------------------------------------------------------------------------

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
    private fun scrub(direction: Int, repeat: Int) {
        val step = when {
            repeat > 20 -> 120_000L
            repeat > 8 -> 60_000L
            repeat > 2 -> 30_000L
            else -> 10_000L
        }
        val from = seekBar.scrubMs ?: player.exo.currentPosition
        val to = (from + direction * step).coerceIn(0, seekBar.durationMs.coerceAtLeast(0))
        seekBar.scrubMs = to
        updateProgress()
        showThumb(to)
        main.removeCallbacks(hideChrome)
        main.removeCallbacks(commitScrub)
        main.postDelayed(commitScrub, SCRUB_COMMIT_MS)
    }

    private fun showThumb(positionMs: Long) {
        val tp = trickplay
        thumbTime.text = time(positionMs)
        // Without trickplay thumbnails on the server, a small time bubble follows the knob instead
        val w = context.dp(if (tp == null) BUBBLE_W else THUMB_W)
        val h = context.dp(if (tp == null) BUBBLE_H else THUMB_H)
        val params = thumbFrame.layoutParams
        if (params.width != w) {
            params.width = w
            params.height = h
            thumbFrame.layoutParams = params
            thumb.visibility = if (tp == null) View.GONE else View.VISIBLE
        }
        val x = seekBar.left + seekBar.xFor(positionMs) - w / 2f
        thumbFrame.translationX = x.coerceIn(context.dpf(24f), (chrome.width - w - context.dp(24)).toFloat())
        thumbFrame.visibility = View.VISIBLE
        if (tp != null) trickplayLoader.load(tp, positionMs) { thumb.setImageBitmap(it) }
    }

    private fun commitScrub() {
        main.removeCallbacks(commitScrub)
        val target = seekBar.scrubMs ?: return
        seekBar.scrubMs = null
        player.exo.seekTo(target)
        thumbFrame.visibility = View.INVISIBLE
        updateProgress()
        scheduleHide()
    }

    // --- Tracks --------------------------------------------------------------------------------

    private fun chooseSubtitles() {
        val s = stream ?: return
        val options = listOf(-1 to string(R.string.subtitles_off)) + s.subtitles.map { it.index to it.title }
        choose(string(R.string.subtitles), options, s.subtitleIndex) { index -> switchTracks(s.audioIndex, index) }
    }

    private fun chooseAudio() {
        val s = stream ?: return
        choose(string(R.string.audio), s.audio.map { it.index to it.title }, s.audioIndex ?: -2) { index -> switchTracks(index, s.subtitleIndex) }
    }

    private fun choose(heading: String, options: List<Pair<Int, String>>, current: Int, pick: (Int) -> Unit) {
        main.removeCallbacks(hideChrome)
        dialog = ChoiceDialog(context, heading, options.map { (index, label) -> (if (index == current) "✓  $label" else label) to { pick(index); scheduleHide() } })
            .also { it.open(view) }
    }

    /** Switches here when the player has the track; otherwise asks Jellyfin for a new stream from the same spot. */
    private fun switchTracks(audio: Int?, subtitleIndex: Int) {
        val s = stream ?: return
        if (player.canSwitchLocally(audio, subtitleIndex)) {
            stream = s.copy(audioIndex = audio, subtitleIndex = subtitleIndex)
            player.select(audio, subtitleIndex)
            reportProgress()
        } else {
            val position = player.exo.currentPosition
            scope.launch { service.reportStop(s, position) }
            stream = null
            start(item, position, audio, subtitleIndex)
        }
    }

    // --- Keys and lifecycle --------------------------------------------------------------------

    override fun onKey(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (down && event.repeatCount == 0) {
                    togglePause()
                    setChrome(true)
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (down) seekBy(30_000)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (down) seekBy(-10_000)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (down && next != null) playNext()
                return true
            }
        }
        if (dialog?.isAttachedToWindow == true || upNext.visibility == View.VISIBLE) return false
        if (chromeShown) {
            if (down) scheduleHide()
            return false
        }
        // Controls hidden: the keys act on the video directly
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return false
        if (!down) return true
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (event.repeatCount == 0) {
                if (skip.visibility == View.VISIBLE) skipSegment() else {
                    togglePause()
                    setChrome(true)
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                setChrome(true, autoHide = false)
                seekBar.requestFocus()
                scrub(if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1, event.repeatCount)
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> setChrome(true)
        }
        return true
    }

    override fun onBack(): Boolean {
        dialog?.takeIf { it.isAttachedToWindow }?.let {
            it.close()
            scheduleHide()
            return true
        }
        if (upNext.visibility == View.VISIBLE) {
            close()
            return true
        }
        if (seekBar.scrubMs != null) {
            seekBar.scrubMs = null
            thumbFrame.visibility = View.INVISIBLE
            updateProgress()
            return true
        }
        if (chromeShown && player.exo.isPlaying) {
            setChrome(false)
            return true
        }
        close()
        return true
    }

    override fun focusDefault() {
        playPause.requestFocus()
    }

    override fun onPause() {
        player.exo.pause()
        reportProgress()
    }

    /** Stops, reports where it stopped, and goes back. */
    private fun close() {
        if (activity.isFinishing) return
        activity.pop()
    }

    override fun destroy() {
        main.removeCallbacksAndMessages(null)
        val s = stream
        val position = player.exo.currentPosition
        if (s != null && started) {
            // Reporting the stop must outlive this screen's scope
            app.reportScope.launch { withContext(NonCancellable) { service.reportStop(s, position) } }
        }
        player.release()
        trickplayLoader.release()
        super.destroy()
    }

    private fun time(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    // Last, once every property above is set: the player starts calling back right away
    init {
        player.exo.addListener(listener)
        start(item, item.resumeMs)
    }

    private companion object {
        const val TICK_MS = 250L
        const val REPORT_MS = 10_000L
        const val HIDE_MS = 4_000L
        const val SCRUB_COMMIT_MS = 900L
        const val UP_NEXT_SECONDS = 8
        const val THUMB_W = 192
        const val THUMB_H = 108
        const val BUBBLE_W = 72
        const val BUBBLE_H = 26
    }
}

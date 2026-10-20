package cloud.rawkoon.tv.screens

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
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
import cloud.rawkoon.tv.playback.SEEK_BACK_MS
import cloud.rawkoon.tv.playback.SEEK_FORWARD_MS
import cloud.rawkoon.tv.playback.TrickplayLoader
import cloud.rawkoon.tv.playback.VideoPlayer
import cloud.rawkoon.tv.screens.player.PlayerChrome
import cloud.rawkoon.tv.screens.player.ProgressReporter
import cloud.rawkoon.tv.screens.player.ScrubController
import cloud.rawkoon.tv.screens.player.SegmentSkipper
import cloud.rawkoon.tv.screens.player.TrackMenus
import cloud.rawkoon.tv.screens.player.isEpisode
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.Icon
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.fill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.PlayMethod

/**
 * The player: the video full screen, and over it, while shown, the title, the seek bar with
 * trickplay thumbnails and the controls. With the controls hidden, Left and Right seek, OK pauses
 * (or skips the intro when the skip button is up), and Back stops. Progress goes back to Jellyfin
 * as it plays, so "Continue watching" picks up where you left. The pieces live in
 * [cloud.rawkoon.tv.screens.player]; this screen loads the stream, listens to ExoPlayer and
 * handles the keys.
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

    init {
        surface.player = player.exo
        view.addView(surface, fill())
    }

    private var next: MediaItem? = null
    private var triedTranscode = false
    private var loadJob: Job? = null

    private val tick = Runnable {
        updateProgress()
        scheduleTick()
    }
    private val chrome: PlayerChrome = PlayerChrome(
        context, app, view, surface, main,
        isPlaying = { player.exo.isPlaying },
        dialogOpen = { menus.isOpen },
        onShow = { updateProgress() },
        onSettled = { scheduleTick() },
    )
    private val reporter = ProgressReporter(service, player.exo, main, scope, app.reportScope)
    private val scrub = ScrubController(context, chrome, player.exo, trickplayLoader, main, refresh = { updateProgress() })
    private val skipper = SegmentSkipper(context, chrome, player.exo, main, hasNext = { next != null }, playNext = { playNext() })
    private val menus: TrackMenus = TrackMenus(context, view, chrome, player, reporter) { position, audio, subtitleIndex, previous ->
        start(item, position, audio, subtitleIndex, direct = !triedTranscode || previous.method == PlayMethod.DIRECT_PLAY)
    }

    init {
        chrome.rewind.setOnClickListener { seekBy(-SEEK_BACK_MS) }
        chrome.forward.setOnClickListener { seekBy(SEEK_FORWARD_MS) }
        chrome.playPause.setOnClickListener { togglePause() }
        chrome.nextButton.setOnClickListener { playNext() }
    }

    // --- Loading -------------------------------------------------------------------------------

    private fun start(target: MediaItem, startMs: Long, audio: Int? = null, subtitleIndex: Int? = null, direct: Boolean = true) {
        item = target
        app.lastPlayed = target
        reporter.reset()
        chrome.resetProgress()
        chrome.showTitle(target) { item === target }
        skipper.hide()
        chrome.showSpinner(true)
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val s = service.stream(target, startMs, audio, subtitleIndex, direct = direct)
                reporter.stream = s
                player.load(s, startMs)
                chrome.nextButton.visibility = if (target.isEpisode) View.VISIBLE else View.GONE
                launch {
                    skipper.segments = service.segments(target.id)
                    scheduleTick()
                }
                launch { scrub.trickplay = service.trickplay(target.id, s.mediaSourceId, context.dp(PlayerChrome.THUMB_W)) }
                launch { next = if (target.isEpisode) service.nextEpisode(target) else null }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e.message ?: e.javaClass.simpleName)
            }
        }
        chrome.setChrome(true)
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            chrome.showSpinner(state == Player.STATE_BUFFERING)
            if (reporter.stream == null) return
            if (state == Player.STATE_READY && reporter.onReady()) chrome.scheduleHide()
            if (state == Player.STATE_ENDED) ended()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reporter.started) reporter.progress()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            chrome.playPause.setGlyph(if (isPlaying) Icon.PAUSE else Icon.PLAY)
            scheduleTick()
            if (!isPlaying && player.exo.playbackState == Player.STATE_READY) chrome.setChrome(true, autoHide = false) else if (isPlaying) chrome.scheduleHide()
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            scheduleTick()
        }

        override fun onPlayerError(error: PlaybackException) {
            val s = reporter.stream
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
        chrome.showSpinner(false)
        Toast.makeText(context, string(R.string.playback_error, reason), Toast.LENGTH_LONG).show()
        close()
    }

    // --- Progress, end -------------------------------------------------------------------------

    /** Brings the bar and the times up to date while the controls show; the skip button follows the position either way. */
    private fun updateProgress() {
        val exo = player.exo
        val position = exo.currentPosition
        if (chrome.shown) {
            val duration = exo.duration.takeIf { it > 0 } ?: reporter.stream?.durationMs ?: 0L
            chrome.showProgress(duration, position, exo.bufferedPosition)
        }
        skipper.update(position)
    }

    /**
     * Schedules the next [updateProgress]: often while the controls show a playing film, and with
     * them hidden only when a segment starts or ends. Nothing runs while paused or buffering; the
     * player's callbacks start it again.
     */
    private fun scheduleTick() {
        main.removeCallbacks(tick)
        val exo = player.exo
        if (!exo.isPlaying) return
        val delay = if (chrome.shown) TICK_MS else skipper.nextEdge(exo.currentPosition, TICK_MS, MAX_IDLE_TICK_MS) ?: return
        main.postDelayed(tick, delay)
    }

    private fun ended() {
        if (reporter.stream == null) return close()
        reporter.stop(player.exo.duration.coerceAtLeast(0))
        if (next == null) return close()
        skipper.showUpNext()
    }

    private fun playNext() {
        val target = next ?: return
        skipper.stopCountdown()
        reporter.stop(player.exo.currentPosition)
        scrub.reset()
        next = null
        skipper.reset()
        triedTranscode = false
        start(target, target.resumeMs)
    }

    private fun togglePause() {
        val exo = player.exo
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    private fun seekBy(deltaMs: Long) {
        val exo = player.exo
        exo.seekTo((exo.currentPosition + deltaMs).coerceIn(0, exo.duration.coerceAtLeast(0)))
        updateProgress()
        chrome.scheduleHide()
    }

    // --- Keys and lifecycle --------------------------------------------------------------------

    override fun onKey(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (down && event.repeatCount == 0) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> player.exo.play()
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> player.exo.pause()
                        else -> togglePause()
                    }
                    chrome.setChrome(true)
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (down) seekBy(SEEK_FORWARD_MS)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (down) seekBy(-SEEK_BACK_MS)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (down && next != null) playNext()
                return true
            }
        }
        if (menus.isOpen || skipper.upNextShown) return false
        if (chrome.shown) {
            if (down) chrome.scheduleHide()
            return false
        }
        // Controls hidden: the keys act on the video directly
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return false
        if (!down) return true
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (event.repeatCount == 0) {
                if (chrome.skip.visibility == View.VISIBLE) skipper.skipSegment() else {
                    togglePause()
                    chrome.setChrome(true)
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                chrome.setChrome(true, autoHide = false)
                chrome.seekBar.requestFocus()
                scrub.scrub(if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1, event.repeatCount)
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> chrome.setChrome(true)
        }
        return true
    }

    override fun onBack(): Boolean {
        if (menus.close()) return true
        if (skipper.upNextShown) {
            close()
            return true
        }
        if (scrub.scrubbing) {
            scrub.cancel()
            return true
        }
        if (chrome.shown && player.exo.isPlaying) {
            chrome.setChrome(false)
            return true
        }
        close()
        return true
    }

    override fun focusDefault() {
        chrome.playPause.requestFocus()
    }

    /** Pausing reports itself, through the player's play-when-ready callback. */
    override fun onPause() {
        player.exo.pause()
    }

    /** Stops, reports where it stopped, and goes back. */
    private fun close() {
        if (activity.isFinishing) return
        activity.pop()
    }

    override fun destroy() {
        main.removeCallbacksAndMessages(null)
        reporter.stop(player.exo.currentPosition)
        player.release()
        trickplayLoader.release()
        super.destroy()
    }

    // Last, once every property above is set: the player starts calling back right away
    init {
        player.exo.addListener(listener)
        start(item, item.resumeMs)
    }

    private companion object {
        const val TICK_MS = 250L
        const val MAX_IDLE_TICK_MS = 5_000L
    }
}

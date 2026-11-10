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
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.Screen
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.jellyfin.unhideSeries
import cloud.rawkoon.tv.playback.PlaybackService
import cloud.rawkoon.tv.playback.TrackMemory
import cloud.rawkoon.tv.playback.TrickplayLoader
import cloud.rawkoon.tv.playback.VideoPlayer
import cloud.rawkoon.tv.screens.player.AutoplayTracker
import cloud.rawkoon.tv.screens.player.DisplayMatcher
import cloud.rawkoon.tv.screens.player.EpisodePanel
import cloud.rawkoon.tv.screens.player.PlayerChrome
import cloud.rawkoon.tv.screens.player.PlayerSettings
import cloud.rawkoon.tv.screens.player.ProgressReporter
import cloud.rawkoon.tv.screens.player.ScrubController
import cloud.rawkoon.tv.screens.player.SegmentSkipper
import cloud.rawkoon.tv.screens.player.TrackMenus
import cloud.rawkoon.tv.screens.player.MAX_NETWORK_RETRIES
import cloud.rawkoon.tv.screens.player.NETWORK_RETRY_DELAY_MS
import cloud.rawkoon.tv.screens.player.advanceStopPosition
import cloud.rawkoon.tv.screens.player.isNetworkError
import cloud.rawkoon.tv.screens.player.togglePlayback
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
    override val view = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        // Park focus here while the controls are hidden, rather than on an invisible button.
        isFocusable = true
    }
    override val slides get() = false
    private val service = PlaybackService(context, session)
    private val settings = PlayerSettings.read(context)
    private val player = VideoPlayer(context, service.authorization, service.serverUrl, settings.skipBackMs, settings.skipForwardMs)
    private val autoplay = AutoplayTracker(settings.stillWatchingAfter)
    private val displayMatcher = DisplayMatcher(activity, player.exo, mainHandler, settings.matchDisplay)
    private val trickplayLoader = TrickplayLoader(context, service.authorization)
    private val trackMemory = TrackMemory(context, session.profile.id)

    private val surface = PlayerView(context).apply {
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        keepScreenOn = true
        setShutterBackgroundColor(Color.BLACK)
        subtitleView?.apply {
            val style = settings.subtitleStyle
            setStyle(CaptionStyleCompat(style.textColor, style.backgroundColor, Color.TRANSPARENT, style.edgeType, style.edgeColor, Fonts.medium(context)))
            setFractionalTextSize(style.textSizeFraction)
            setBottomPaddingFraction(0.08f)
        }
    }

    init {
        // The preview clip's decoder would compete with the video's
        app.clips.release()
        surface.player = player.exo
        view.addView(surface, fill())
    }

    private var next: MediaItem? = null
    private var triedTranscode = false
    private var networkRetries = 0
    private val retryPrepare = Runnable { player.exo.prepare() }
    private var loadJob: Job? = null

    /** Where the stream loading now was asked to start. */
    private var startMs = 0L

    private val tick = Runnable {
        updateProgress()
        scheduleTick()
    }
    private val chrome: PlayerChrome = PlayerChrome(
        context, app, view, surface, mainHandler,
        isPlaying = { player.exo.isPlaying },
        dialogOpen = { menus.isOpen },
        onShow = { updateProgress() },
        onSettled = { scheduleTick() },
        skipBackSeconds = (settings.skipBackMs / 1000).toInt(),
        skipForwardSeconds = (settings.skipForwardMs / 1000).toInt(),
    )
    private val reporter = ProgressReporter(service, player.exo, mainHandler, scope, app)
    private val scrub = ScrubController(context, chrome, player.exo, trickplayLoader, mainHandler, refresh = { updateProgress() })
    private val skipper = SegmentSkipper(
        context, chrome, player.exo, mainHandler, settings,
        hasNext = { next != null },
        playNext = { auto -> playNext(auto) },
        plan = { autoplay.plan(settings.autoplayNext, settings.countdownSeconds) },
    )
    private val menus: TrackMenus = TrackMenus(context, view, chrome, player, reporter, trackMemory, series = { item.seriesId.takeIf { item.isEpisode } }) { position, audio, subtitleIndex, previous ->
        // A file that already failed to play directly stays transcoded, whatever the new tracks or quality
        start(item, position, audio, subtitleIndex, previous.mediaSourceId, direct = !triedTranscode || previous.method == PlayMethod.DIRECT_PLAY)
    }

    private val episodePanel = EpisodePanel(
        context, app, session, scope, mainHandler, view,
        play = { target -> playEpisode(target) },
        onClosed = {
            chrome.held = false
            chrome.setChrome(true)
        },
    )

    init {
        chrome.episodesButton.setOnClickListener { openEpisodes() }
        chrome.rewind.setOnClickListener { seekBy(-settings.skipBackMs) }
        chrome.forward.setOnClickListener { seekBy(settings.skipForwardMs) }
        chrome.playPause.setOnClickListener { togglePause() }
        chrome.nextButton.setOnClickListener { playNext(auto = false) }
    }

    // --- Loading -------------------------------------------------------------------------------

    private fun start(
        target: MediaItem,
        startMs: Long,
        audio: Int? = null,
        subtitleIndex: Int? = null,
        mediaSourceId: String? = null,
        direct: Boolean = true,
    ) {
        item = target
        networkRetries = 0
        mainHandler.removeCallbacks(retryPrepare)
        this.startMs = startMs
        app.lastPlayed = target
        reporter.reset()
        chrome.resetProgress()
        chrome.showTitle(target) { item === target }
        skipper.hide()
        displayMatcher.reset()
        chrome.showSpinner(true)
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                // A new episode, rather than the same one again with other tracks, gets the show's last picks
                val remembered = if (audio == null && subtitleIndex == null && target.isEpisode) target.seriesId?.let(trackMemory::read) else null
                val s = service.stream(target, startMs, audio, subtitleIndex, mediaSourceId, direct = direct, remembered = remembered, maxBitrateMbps = menus.maxBitrateMbps)
                reporter.stream = s
                player.load(s, startMs)
                chrome.nextButton.visibility = if (target.isEpisode) View.VISIBLE else View.GONE
                chrome.episodesButton.visibility = if (target.isEpisode && target.seriesId != null) View.VISIBLE else View.GONE
                val nextLoad = launch { next = if (target.isEpisode) service.nextEpisode(target) else null }
                launch {
                    val segments = service.segments(target.id)
                    // Credits set to skip by themselves need to know whether an episode follows
                    nextLoad.join()
                    skipper.segments = segments
                    // The playhead may already be inside a segment (resuming, a slow server)
                    skipper.update(player.exo.currentPosition)
                    scheduleTick()
                }
                launch { scrub.trickplay = service.trickplay(target.id, s.mediaSourceId, context.dp(PlayerChrome.THUMB_W)) }
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
            // A file ExoPlayer can't seek in (its index out of reach) would start at the beginning,
            // ignore every seek, and report that as the resume point: the server's stream can seek
            if (state == Player.STATE_READY && !player.exo.isCurrentMediaItemSeekable && !player.exo.isCurrentMediaItemLive && transcodeInstead(startMs)) return
            if (state == Player.STATE_READY) networkRetries = 0
            if (state == Player.STATE_READY && reporter.onReady()) {
                chrome.scheduleHide()
                val hidden = Prefs.nextUpHidden(context, session.profile.id)
                unhideSeries(hidden, item.seriesId).takeIf { it != hidden }?.let { Prefs.setNextUpHidden(context, session.profile.id, it) }
            }
            if (state == Player.STATE_ENDED) ended()
        }

        override fun onTracksChanged(tracks: Tracks) {
            displayMatcher.onTracks(tracks)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // Buffering changes isPlaying without changing the user's Play/Pause choice.
            chrome.playPause.setGlyph(if (playWhenReady) Icon.PAUSE else Icon.PLAY)
            if (!playWhenReady) chrome.setChrome(true, autoHide = false) else chrome.scheduleHide()
            if (reporter.started) reporter.progress()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            scheduleTick()
            if (!isPlaying && player.exo.playbackState == Player.STATE_READY) chrome.setChrome(true, autoHide = false) else if (isPlaying) chrome.scheduleHide()
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            scheduleTick()
        }

        override fun onPlayerError(error: PlaybackException) {
            // A network blip: prepare again from the same position before blaming the file
            if (isNetworkError(error.errorCode) && networkRetries < MAX_NETWORK_RETRIES) {
                networkRetries++
                chrome.showSpinner(true)
                mainHandler.postDelayed(retryPrepare, NETWORK_RETRY_DELAY_MS)
                return
            }
            // A file the TV can't play after all
            if (transcodeInstead(player.exo.currentPosition)) return
            fail(error.errorCodeName)
        }
    }

    /** Asks Jellyfin for a transcode of the file playing directly, from [positionMs], once per item. False when there is nothing to fall back from. */
    private fun transcodeInstead(positionMs: Long): Boolean {
        val s = reporter.stream ?: return false
        if (s.method != PlayMethod.DIRECT_PLAY || triedTranscode) return false
        triedTranscode = true
        reporter.stop(positionMs)
        // Silent while the server prepares the stream, instead of playing on from the start
        player.exo.stop()
        start(item, positionMs, s.audioIndex, s.subtitleIndex, s.mediaSourceId, direct = false)
        return true
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
            chrome.showProgress(durationMs(), position, exo.bufferedPosition)
        }
        skipper.update(position)
    }

    private fun durationMs() = player.exo.duration.takeIf { it > 0 } ?: reporter.stream?.durationMs ?: 0L

    /**
     * Schedules the next [updateProgress]: often while the controls show a playing film, and with
     * them hidden only when a segment starts or ends. Nothing runs while paused or buffering; the
     * player's callbacks start it again.
     */
    private fun scheduleTick() {
        mainHandler.removeCallbacks(tick)
        val exo = player.exo
        if (!exo.isPlaying) return
        val delay = if (chrome.shown) TICK_MS else skipper.nextEdge(exo.currentPosition, TICK_MS, MAX_IDLE_TICK_MS) ?: return
        mainHandler.postDelayed(tick, delay)
    }

    private fun ended() {
        episodePanel.close()
        if (reporter.stream == null) return close()
        reporter.stop(player.exo.duration.coerceAtLeast(0))
        if (next == null) return close()
        // The credits may already have brought the button up, and its countdown is under way
        if (!skipper.upNextShown) skipper.showUpNext()
    }

    /** Plays the next episode. [auto] is true when nobody asked for it (the countdown ran out), which counts toward "Still watching?". */
    private fun playNext(auto: Boolean) {
        val target = next ?: return
        if (auto) autoplay.onAutoStarted()
        skipper.stopCountdown()
        val position = player.exo.currentPosition
        switchTo(target, advanceStopPosition(position, durationMs(), auto, skipper.upNextShown, skipper.segments))
    }

    /** Plays [target], an episode picked in the episodes panel, from its resume point. */
    private fun playEpisode(target: MediaItem) {
        skipper.stopCountdown()
        switchTo(target, player.exo.currentPosition)
    }

    /** Reports the stop of what plays at [stopAtMs] and starts [target] in its place. */
    private fun switchTo(target: MediaItem, stopAtMs: Long) {
        episodePanel.close()
        reporter.stop(stopAtMs)
        player.exo.pause()
        scrub.reset()
        next = null
        skipper.reset()
        triedTranscode = false
        start(target, settings.startPosition(target.resumeMs))
    }

    /** Puts the show's episodes over the video, which plays on behind them. */
    private fun openEpisodes() {
        if (!item.isEpisode || item.seriesId == null || episodePanel.isOpen) return
        menus.close()
        chrome.setChrome(false)
        chrome.held = true
        // An episode with no show to list leaves the controls as they were
        if (!episodePanel.open(item)) {
            chrome.held = false
            chrome.setChrome(true)
        }
    }

    private fun togglePause() {
        displayMatcher.dropAutoResume()
        player.exo.togglePlayback()
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
        // Someone is there: "Still watching?" starts over
        if (down) autoplay.onKey()
        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (down && event.repeatCount == 0) {
                    displayMatcher.dropAutoResume()
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
                if (down) seekBy(settings.skipForwardMs)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (down) seekBy(-settings.skipBackMs)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (down && next != null) playNext(auto = false)
                return true
            }
        }
        if (menus.isOpen || skipper.upNextShown || episodePanel.isOpen) return false
        if (chrome.shown) {
            // Down from the row of buttons opens the episodes, as their button does
            if (down && event.repeatCount == 0 && event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && chrome.controlsFocused && item.isEpisode && item.seriesId != null) {
                openEpisodes()
                return true
            }
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
        if (episodePanel.isOpen) {
            episodePanel.close()
            return true
        }
        if (skipper.upNextShown) {
            close()
            return true
        }
        if (scrub.scrubbing) {
            scrub.cancel()
            return true
        }
        if (chrome.shown && player.exo.playWhenReady) {
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
        displayMatcher.dropAutoResume()
        player.exo.pause()
    }

    /**
     * Leaving the app ends playback: the server saves the position only on a stop report, and a
     * TV going to sleep never comes back through Back to send one. The page below shows where it
     * stopped.
     */
    override fun onStop() {
        close()
    }

    /** Stops, reports where it stopped, and goes back. */
    private fun close() {
        if (activity.isFinishing) return
        activity.pop()
    }

    override fun destroy() {
        mainHandler.removeCallbacks(retryPrepare)
        reporter.stop(player.exo.currentPosition)
        displayMatcher.restore()
        player.release()
        trickplayLoader.release()
        super.destroy()
    }

    // Last, once every property above is set: the player starts calling back right away
    init {
        player.exo.addListener(listener)
        start(item, settings.startPosition(item.resumeMs))
    }

    private companion object {
        const val TICK_MS = 250L
        const val MAX_IDLE_TICK_MS = 5_000L
    }
}

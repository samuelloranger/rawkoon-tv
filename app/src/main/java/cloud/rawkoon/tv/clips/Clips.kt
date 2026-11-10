package cloud.rawkoon.tv.clips

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import cloud.rawkoon.tv.data.DiskCache
import cloud.rawkoon.tv.data.HttpStatusException
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.data.Profile
import cloud.rawkoon.tv.rawkoon.RawkoonClient
import cloud.rawkoon.tv.rawkoon.rawkoonEndpoint
import cloud.rawkoon.tv.ui.Backdrop
import cloud.rawkoon.tv.ui.Tokens
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Where to fetch a clip and the header it needs. */
data class ClipRequest(val url: String, val headers: Map<String, String>)

/**
 * Preview clips: whether each profile's server offers them, and the one shared player that plays a
 * focused title's clip in the backdrop frame. The player is built on first use and released when the
 * app leaves the screen. Everything here runs on the main thread except the downloads.
 */
class Clips(private val context: Context, private val disk: DiskCache) {
    private val main = Handler(Looper.getMainLooper())
    private val offered = ConcurrentHashMap<String, Boolean>()
    private val intent = ClipIntent()
    private val downloads = Executors.newSingleThreadExecutor()

    /** Clips the server answered no for (no clip, or clips off): not asked for again until the app restarts. */
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private var player: ExoPlayer? = null
    private var target: Backdrop? = null
    private var playing = false

    /** Whether the app is in front: nothing starts while it is not. */
    var foreground = false

    /** Bumped by every [stop]: a lookup that began before one must not start a clip after it. */
    var epoch = 0
        private set
    
    /** The downloaded file of the clip wanted now. */
    private var ready: java.io.File? = null
    private val dwell = Runnable { if (intent.dwellElapsed()) ready?.let(::play) }

    /**
     * Asks every linked profile's server whether it offers clips. A failed call keeps the last answer;
     * before any answer, clips are off.
     */
    suspend fun refresh(profiles: List<Profile>) {
        // Clips the server lacked may exist now (it makes them in the background)
        missing.clear()
        for (p in profiles) {
            val endpoint = p.rawkoonEndpoint(context)
            if (endpoint == null) {
                offered.remove(p.id)
                continue
            }
            try {
                offered[p.id] = RawkoonClient(endpoint.baseUrl).clipsEnabled(endpoint.token)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep what was known
            }
        }
    }

    /** Whether the server of [profile] offers clips (the setting is not looked at): decides if the setting is shown. */
    fun serverOffers(profile: Profile) = offered[profile.id] == true

    /** Whether a focused title of [profile] may play a clip: the server offers them and the setting is on. */
    fun wanted(profile: Profile) = serverOffers(profile) && Prefs.previewClips(context)

    fun request(profile: Profile, key: ClipKey): ClipRequest? =
        profile.rawkoonEndpoint(context)?.let { ClipRequest("${it.baseUrl}/${key.path()}", mapOf("Authorization" to "Bearer ${it.token}")) }

    /**
     * [request] is the clip of the title that just took focus in [backdrop] (null: none): downloads it
     * now and plays it once focus has rested. Whatever was playing or pending stops first.
     */
    fun focus(request: ClipRequest?, backdrop: Backdrop) {
        stop()
        if (!foreground || request == null || request.url in missing) return
        target = backdrop
        intent.focus(request.url)
        main.postDelayed(dwell, Tokens.CLIP_DWELL_MS)
        downloads.execute {
            if (intent.url != request.url) return@execute
            var notFound = false
            val file = try {
                disk.file(request.url, request.headers, Tokens.CLIP_TTL_MS)
            } catch (e: HttpStatusException) {
                notFound = e.code == 404
                null
            } catch (e: Exception) {
                null
            }
            main.post {
                if (file == null) {
                    // Only "no clip" is remembered: a timeout or a server error says nothing about the clip
                    if (notFound) missing.add(request.url)
                } else if (intent.url == request.url) {
                    ready = file
                    if (intent.downloaded(request.url)) play(file)
                }
            }
        }
    }

    private fun play(file: java.io.File) {
        val backdrop = target ?: return
        if (playing) return
        val p = player ?: build().also { player = it }
        playing = true
        p.volume = 0f
        p.repeatMode = Player.REPEAT_MODE_OFF
        p.setVideoTextureView(backdrop.clipView)
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        p.prepare()
        p.playWhenReady = true
    }

    private fun build(): ExoPlayer = ExoPlayer.Builder(context).build().apply {
        addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (playing) target?.setClipVisible(true)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                target?.fitClip(videoSize.width, videoSize.height)
            }

            // Qualified: inside apply, a bare stop() is the player's own, which leaves the last frame showing
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) this@Clips.stop()
            }

            override fun onPlayerError(error: PlaybackException) = this@Clips.stop()
        })
    }

    /** Cancels what is pending and fades a playing clip back to the art. */
    fun stop() {
        epoch++
        main.removeCallbacks(dwell)
        intent.stop()
        ready = null
        if (!playing) return
        playing = false
        player?.stop()
        player?.clearMediaItems()
        target?.setClipVisible(false)
    }

    /** [backdrop] is going away or hidden: stops if it is the one playing. */
    fun stop(backdrop: Backdrop) {
        if (target === backdrop) {
            stop()
            // The app-wide player must not keep a view, and through it an activity, alive
            target = null
        }
    }

    /** Frees the player, and the decoder with it. */
    fun release() {
        stop()
        target = null
        player?.release()
        player = null
    }
}

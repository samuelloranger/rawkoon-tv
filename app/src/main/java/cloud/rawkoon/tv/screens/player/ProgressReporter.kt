package cloud.rawkoon.tv.screens.player

import android.os.Handler
import androidx.media3.common.Player
import cloud.rawkoon.tv.App
import cloud.rawkoon.tv.playback.PlaybackService
import cloud.rawkoon.tv.playback.Stream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.PlayMethod

/**
 * What the player tells Jellyfin: that a stream started, where it is every few seconds, and that
 * it stopped, so "Continue watching" picks up where you left. Owns the [stream] being reported.
 * [scope] is the screen's; stops go through [App.reportScope] instead, which outlives it.
 */
class ProgressReporter(
    private val service: PlaybackService,
    private val exo: Player,
    private val main: Handler,
    private val scope: CoroutineScope,
    private val app: App,
) {
    /** The stream playing now, or null between streams. */
    var stream: Stream? = null

    /** True once the stream has played, and so has been reported as started. */
    var started = false
        private set

    private val timer = object : Runnable {
        override fun run() {
            progress()
            main.postDelayed(this, REPORT_MS)
        }
    }

    /** A new item is loading: nothing has been reported for it yet. */
    fun reset() {
        started = false
    }

    /** Called when the player is ready; reports the start the first time, and returns true then. */
    fun onReady(): Boolean {
        val s = stream ?: return false
        if (started) return false
        started = true
        scope.launch { service.reportStart(s, exo.currentPosition, !exo.playWhenReady) }
        main.removeCallbacks(timer)
        main.postDelayed(timer, REPORT_MS)
        return true
    }

    fun progress() {
        val s = stream ?: return
        if (!started) return
        scope.launch { service.reportProgress(s, exo.currentPosition, !exo.playWhenReady) }
    }

    /**
     * Reports the stop of the current stream and forgets it. Every way a stream ends goes through
     * here: the report must outlive the screen's scope (leaving the screen cancels it), and a
     * transcode that never got to play still has to be ended on the server. The job is kept in
     * [App.pendingStop] for the page below to wait on.
     */
    fun stop(positionMs: Long) {
        val s = stream ?: return
        stream = null
        if (started || s.method != PlayMethod.DIRECT_PLAY) {
            app.pendingStop = app.reportScope.launch { withContext(NonCancellable) { service.reportStop(s, positionMs) } }
        }
    }

    private companion object {
        const val REPORT_MS = 10_000L
    }
}

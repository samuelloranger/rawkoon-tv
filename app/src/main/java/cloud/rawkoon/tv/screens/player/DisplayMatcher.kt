package cloud.rawkoon.tv.screens.player

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import cloud.rawkoon.tv.data.MatchDisplay
import cloud.rawkoon.tv.playback.DisplayModeInfo
import cloud.rawkoon.tv.playback.pickDisplayMode

/**
 * Asks the TV to switch to the display mode that suits the video (its frame rate, and with
 * [mode] set to match the resolution too), as the "Match display" setting says. The video is
 * paused while the TV switches, and resumes when the display reports the change or after a
 * moment. One switch per item; [restore] hands the display back to the system.
 */
@OptIn(UnstableApi::class)
class DisplayMatcher(
    private val activity: Activity,
    private val exo: Player,
    private val main: Handler,
    private val mode: MatchDisplay,
) {
    private val displayManager = activity.getSystemService(DisplayManager::class.java)
    private var handled = false
    private var pausedByUs = false
    private var listener: DisplayManager.DisplayListener? = null
    private var awaitedModeId = 0
    private val resumeAfterTimeout = Runnable { resume() }

    /** A new item is loading: the next video track it selects may need a switch of its own. */
    fun reset() {
        handled = false
        cancelWait()
        pausedByUs = false
    }

    /** Looks at the video track ExoPlayer selected, once its frame rate is known, and switches to a matching display mode. */
    fun onTracks(tracks: Tracks) {
        if (mode == MatchDisplay.OFF || handled) return
        val format = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
            ?.let { group -> (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it) } }
            ?: return
        if (format.frameRate == Format.NO_VALUE.toFloat() || format.frameRate <= 0f) return
        handled = true
        switchFor(format)
    }

    @Suppress("DEPRECATION")
    private fun display(): Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display else activity.windowManager.defaultDisplay

    private fun switchFor(format: Format) {
        val display = display() ?: return
        val current = display.mode.toInfo()
        val chosen = pickDisplayMode(
            modes = display.supportedModes.map { it.toInfo() },
            current = current,
            frameRate = format.frameRate,
            matchResolution = mode == MatchDisplay.FRAME_RATE_AND_RESOLUTION,
            videoWidth = format.width.coerceAtLeast(0),
            videoHeight = format.height.coerceAtLeast(0),
        ) ?: return
        // Already the right mode, or the system is on its way to it
        if (chosen.id == current.id) return
        val window = activity.window
        if (window.attributes.preferredDisplayModeId == chosen.id) return
        awaitedModeId = chosen.id
        pauseForSwitch(display.displayId)
        window.attributes = window.attributes.also { it.preferredDisplayModeId = chosen.id }
    }

    /** Pauses until the display reports a change, or until [SWITCH_TIMEOUT_MS] have passed. */
    private fun pauseForSwitch(displayId: Int) {
        if (exo.isPlaying || exo.playWhenReady) {
            exo.pause()
            pausedByUs = true
        }
        val changed = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            // Other changes (HDR, another display) can come first: only the awaited mode ends the wait
            override fun onDisplayChanged(changedId: Int) {
                if (changedId == displayId && display()?.mode?.modeId == awaitedModeId) resume()
            }
        }
        listener = changed
        displayManager.registerDisplayListener(changed, main)
        main.postDelayed(resumeAfterTimeout, SWITCH_TIMEOUT_MS)
    }

    private fun resume() {
        cancelWait()
        if (pausedByUs) {
            pausedByUs = false
            exo.play()
        }
    }

    private fun cancelWait() {
        main.removeCallbacks(resumeAfterTimeout)
        listener?.let { displayManager.unregisterDisplayListener(it) }
        listener = null
    }

    /**
     * Play or pause was pressed while the TV switched, or the app went to the background: the
     * player's state now stands, so the end of the wait doesn't resume it.
     */
    fun dropAutoResume() {
        pausedByUs = false
    }

    /** Gives the display back to the system's choice; for when the player is closed. */
    fun restore() {
        cancelWait()
        if (activity.window.attributes.preferredDisplayModeId != 0) {
            activity.window.attributes = activity.window.attributes.also { it.preferredDisplayModeId = 0 }
        }
    }

    private fun Display.Mode.toInfo() = DisplayModeInfo(modeId, physicalWidth, physicalHeight, refreshRate)

    private companion object {
        const val SWITCH_TIMEOUT_MS = 3_000L
    }
}

package cloud.rawkoon.tv.screens.player

import android.content.Context
import android.view.ViewGroup
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.playback.Stream
import cloud.rawkoon.tv.playback.VideoPlayer
import cloud.rawkoon.tv.ui.ChoiceDialog

/**
 * The subtitle, audio and quality menus, and the switch they ask for. A track the player has is
 * switched here; otherwise the stop of the current stream is reported and [restart] asks for a
 * new one from the same spot (it gets the stream that was playing). A new quality always restarts.
 */
class TrackMenus(
    private val context: Context,
    private val root: ViewGroup,
    private val chrome: PlayerChrome,
    private val player: VideoPlayer,
    private val reporter: ProgressReporter,
    private val restart: (positionMs: Long, audio: Int?, subtitle: Int, previous: Stream) -> Unit,
) {
    private var dialog: ChoiceDialog? = null

    /**
     * The bitrate cap streams are asked for with: the setting's, until the quality menu changes it
     * for the rest of this player, next episodes included.
     */
    var maxBitrateMbps = Prefs.maxBitrateMbps(context)
        private set

    /** True while a menu is up. */
    val isOpen: Boolean get() = dialog?.isAttachedToWindow == true

    init {
        chrome.subtitlesButton.setOnClickListener { chooseSubtitles() }
        chrome.audioButton.setOnClickListener { chooseAudio() }
        chrome.qualityButton.setOnClickListener { chooseQuality() }
    }

    /** Closes the menu if one is up, and says whether there was one. */
    fun close(): Boolean {
        val open = dialog?.takeIf { it.isAttachedToWindow } ?: return false
        open.close()
        chrome.scheduleHide()
        return true
    }

    private fun chooseSubtitles() {
        val s = reporter.stream ?: return
        val options = listOf(-1 to context.getString(R.string.subtitles_off)) + s.subtitles.map { it.index to it.title }
        choose(context.getString(R.string.subtitles), options, s.subtitleIndex) { index -> switchTracks(s.audioIndex, index) }
    }

    private fun chooseAudio() {
        val s = reporter.stream ?: return
        choose(context.getString(R.string.audio), s.audio.map { it.index to it.title }, s.audioIndex ?: -2) { index -> switchTracks(index, s.subtitleIndex) }
    }

    private fun chooseQuality() {
        val s = reporter.stream ?: return
        val options = Prefs.BITRATES_MBPS.map {
            it to if (it == Prefs.AUTO_BITRATE_MBPS) context.getString(R.string.setting_auto) else context.getString(R.string.mbps, it)
        }
        choose(context.getString(R.string.quality), options, maxBitrateMbps) { mbps ->
            if (mbps != maxBitrateMbps) {
                maxBitrateMbps = mbps
                val position = player.exo.currentPosition
                reporter.stop(position)
                restart(position, s.audioIndex, s.subtitleIndex, s)
            }
        }
    }

    private fun choose(heading: String, options: List<Pair<Int, String>>, current: Int, pick: (Int) -> Unit) {
        chrome.cancelHide()
        dialog = ChoiceDialog(
            context,
            heading,
            options.map { (index, label) -> (if (index == current) context.getString(R.string.choice_current, label) else label) to { pick(index); chrome.scheduleHide() } },
        ).also { it.open(root) }
    }

    /** Switches here when the player has the track; otherwise asks Jellyfin for a new stream from the same spot. */
    private fun switchTracks(audio: Int?, subtitleIndex: Int) {
        val s = reporter.stream ?: return
        if (player.canSwitchLocally(audio, subtitleIndex)) {
            reporter.stream = s.copy(audioIndex = audio, subtitleIndex = subtitleIndex)
            player.select(audio, subtitleIndex)
            reporter.progress()
        } else {
            val position = player.exo.currentPosition
            reporter.stop(position)
            restart(position, audio, subtitleIndex, s)
        }
    }
}

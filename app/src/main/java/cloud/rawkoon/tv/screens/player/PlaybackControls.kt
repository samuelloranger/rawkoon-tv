package cloud.rawkoon.tv.screens.player

import androidx.media3.common.Player

/** Pause the requested playback even while buffering or temporarily suppressed. */
internal fun Player.togglePlayback() {
    if (playWhenReady) pause() else play()
}

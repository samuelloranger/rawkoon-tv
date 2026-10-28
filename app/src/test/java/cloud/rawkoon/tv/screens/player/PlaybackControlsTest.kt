package cloud.rawkoon.tv.screens.player

import androidx.media3.common.Player
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackControlsTest {
    private class Playback(var requested: Boolean, var advancing: Boolean) {
        val commands = mutableListOf<String>()
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.name) {
                "getPlayWhenReady" -> requested
                "isPlaying" -> advancing
                "pause" -> { commands += "pause"; requested = false; null }
                "play" -> { commands += "play"; requested = true; null }
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player
    }

    @Test fun `pause during buffering records pause instead of play`() {
        val playback = Playback(requested = true, advancing = false)
        playback.player.togglePlayback()
        assertFalse(playback.requested)
        assertEquals(listOf("pause"), playback.commands)
        // A second press resumes, even if no frames have advanced yet.
        playback.player.togglePlayback()
        assertTrue(playback.requested)
        assertEquals(listOf("pause", "play"), playback.commands)
    }

    @Test fun `successive presses alternate while no frames advance`() {
        val playback = Playback(requested = true, advancing = false)
        repeat(4) { playback.player.togglePlayback() }
        assertEquals(listOf("pause", "play", "pause", "play"), playback.commands)
        assertTrue(playback.requested)
    }

    @Test fun `playing movie pauses and resumes`() {
        val playback = Playback(requested = true, advancing = true)
        playback.player.togglePlayback()
        assertFalse(playback.requested)
        playback.advancing = false
        playback.player.togglePlayback()
        assertTrue(playback.requested)
        assertEquals(listOf("pause", "play"), playback.commands)
    }
}

package cloud.rawkoon.tv.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayModeChoiceTest {
    private fun mode(id: Int, rate: Float, width: Int = 3840, height: Int = 2160) = DisplayModeInfo(id, width, height, rate)

    private val current = mode(1, 60f)

    @Test
    fun `film rate picks the film rate over 60 hertz`() {
        val modes = listOf(current, mode(2, 23.976f), mode(3, 24f))
        assertEquals(2, pickDisplayMode(modes, current, 23.976f, matchResolution = false)?.id)
    }

    @Test
    fun `a rounded film rate still finds the film mode`() {
        val modes = listOf(current, mode(2, 23.976f))
        assertEquals(2, pickDisplayMode(modes, current, 23.98f, matchResolution = false)?.id)
        assertEquals(2, pickDisplayMode(modes, current, 24f, matchResolution = false)?.id)
    }

    @Test
    fun `24 fps prefers 24 hertz over 23_976`() {
        val modes = listOf(current, mode(2, 23.976f), mode(3, 24f))
        assertEquals(3, pickDisplayMode(modes, current, 24f, matchResolution = false)?.id)
    }

    @Test
    fun `29_97 fps does not take 60 hertz`() {
        assertNull(pickDisplayMode(listOf(mode(1, 60f)), current, 29.97f, matchResolution = false))
    }

    @Test
    fun `25 fps picks 50 hertz`() {
        val modes = listOf(current, mode(2, 50f), mode(3, 24f))
        assertEquals(2, pickDisplayMode(modes, current, 25f, matchResolution = false)?.id)
    }

    @Test
    fun `29_97 fps picks 59_94 hertz and not 60`() {
        val modes = listOf(mode(1, 60f), mode(2, 59.94f))
        assertEquals(2, pickDisplayMode(modes, current, 29.97f, matchResolution = false)?.id)
    }

    @Test
    fun `the exact rate beats a multiple`() {
        val modes = listOf(mode(1, 48f), mode(2, 24f), mode(3, 120f))
        assertEquals(2, pickDisplayMode(modes, current, 24f, matchResolution = false)?.id)
    }

    @Test
    fun `no mode at a multiple of the rate means no switch`() {
        val modes = listOf(current, mode(2, 50f))
        assertNull(pickDisplayMode(modes, current, 23.976f, matchResolution = false))
        assertNull(pickDisplayMode(modes, current, 0f, matchResolution = false))
    }

    @Test
    fun `frame rate only keeps the current resolution`() {
        val hd = mode(2, 24f, 1920, 1080)
        val uhd = mode(3, 48f)
        assertEquals(3, pickDisplayMode(listOf(current, hd, uhd), current, 24f, matchResolution = false)?.id)
        assertNull(pickDisplayMode(listOf(current, hd), current, 24f, matchResolution = false))
    }

    @Test
    fun `resolution matching takes the smallest mode that holds the picture`() {
        val modes = listOf(current, mode(2, 24f, 3840, 2160), mode(3, 24f, 1920, 1080), mode(4, 24f, 1280, 720))
        assertEquals(3, pickDisplayMode(modes, current, 24f, true, videoWidth = 1920, videoHeight = 1080)?.id)
        assertEquals(3, pickDisplayMode(modes, current, 24f, true, videoWidth = 1280, videoHeight = 720 + 1)?.id)
        assertEquals(2, pickDisplayMode(modes, current, 24f, true, videoWidth = 3840, videoHeight = 2160)?.id)
    }

    @Test
    fun `resolution matching takes the largest mode when none is big enough`() {
        val modes = listOf(mode(1, 60f, 1920, 1080), mode(2, 24f, 1920, 1080), mode(3, 24f, 1280, 720))
        assertEquals(2, pickDisplayMode(modes, modes[0], 24f, true, videoWidth = 3840, videoHeight = 2160)?.id)
    }

    @Test
    fun `resolution matching only considers modes of a suitable rate`() {
        val modes = listOf(current, mode(2, 60f, 1920, 1080), mode(3, 24f))
        assertEquals(3, pickDisplayMode(modes, current, 24f, true, videoWidth = 1920, videoHeight = 1080)?.id)
    }

    @Test
    fun `at equal resolution the lowest multiple wins`() {
        val modes = listOf(mode(1, 120f), mode(2, 48f), mode(3, 24f))
        assertEquals(3, pickDisplayMode(modes, modes[0], 24f, true, videoWidth = 3840, videoHeight = 2160)?.id)
    }
}

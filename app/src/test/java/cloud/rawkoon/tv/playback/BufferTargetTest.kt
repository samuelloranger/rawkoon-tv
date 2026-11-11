package cloud.rawkoon.tv.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BufferTargetTest {
    private val mib = 1024 * 1024

    @Test
    fun `a small heap shares a third of it, a quarter of that for the preload`() {
        assertEquals(BufferTargets(player = 48 * mib, preload = 16 * mib), bufferTargets(192))
        val at256 = bufferTargets(256)!!
        assertEquals(85 * mib, at256.player + at256.preload)
        assertEquals(85 * mib / 4, at256.preload)
    }

    @Test
    fun `a large heap keeps media3's defaults`() {
        assertNull(bufferTargets(384))
        assertNull(bufferTargets(512))
    }

    @Test
    fun `the cap stops where a third of the heap reaches media3's default`() {
        assertNull(bufferTargets(375))
        assertNotNull(bufferTargets(374))
    }
}

package cloud.rawkoon.tv.screens.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerFormatTest {
    @Test
    fun `times under an hour show minutes and seconds`() {
        assertEquals("0:00", formatTime(0))
        assertEquals("9:05", formatTime(545_000))
    }

    @Test
    fun `times over an hour show hours`() {
        assertEquals("1:02:03", formatTime(3_723_000))
    }

    @Test
    fun `a held key scrubs in growing steps`() {
        assertEquals(10_000L, ScrubController.stepFor(0))
        assertEquals(30_000L, ScrubController.stepFor(3))
        assertEquals(60_000L, ScrubController.stepFor(9))
        assertEquals(120_000L, ScrubController.stepFor(21))
    }
}

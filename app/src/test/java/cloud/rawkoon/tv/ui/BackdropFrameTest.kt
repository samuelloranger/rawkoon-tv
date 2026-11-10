package cloud.rawkoon.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropFrameTest {
    @Test
    fun `home frame is three quarters of the width at 16 by 9`() {
        val f = BackdropFrame.of(1920, 1080, Tokens.FRAME_W_HOME)
        assertEquals(1440, f.w)
        assertEquals(810, f.h)
    }

    @Test
    fun `grid frame is three fifths of the width at 16 by 9`() {
        val f = BackdropFrame.of(1920, 1080, Tokens.FRAME_W_GRID)
        assertEquals(1152, f.w)
        assertEquals(648, f.h)
    }

    @Test
    fun `a full fraction keeps the whole screen`() {
        assertEquals(BackdropFrame(1920, 1080), BackdropFrame.of(1920, 1080, 1f))
        assertFalse(BackdropFrame.isFramed(1f))
        assertTrue(BackdropFrame.isFramed(0.6f))
    }

    @Test
    fun `the fade runs from clear to the page colour without overshoot`() {
        assertEquals(0f, BackdropFrame.fadeAlpha(0f), 0f)
        assertEquals(0.5f, BackdropFrame.fadeAlpha(0.5f), 1e-6f)
        assertEquals(1f, BackdropFrame.fadeAlpha(1f), 0f)
        assertEquals(1f, BackdropFrame.fadeAlpha(2f), 0f)
    }
}

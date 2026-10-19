package com.github.damontecres.wholphin.rawkoon.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class RawkoonLogoMotionTest {
    @Test
    fun `each opening turns the ring by 120 degrees`() {
        assertEquals(120f, RawkoonLogoMotion.nextRotation(0f), 0f)
        assertEquals(240f, RawkoonLogoMotion.nextRotation(120f), 0f)
    }

    @Test
    fun `wordmark clip grows with the reveal and stays within the width`() {
        assertEquals(0f, RawkoonLogoMotion.clipRight(80f, 0f), 0f)
        assertEquals(40f, RawkoonLogoMotion.clipRight(80f, 0.5f), 0f)
        assertEquals(80f, RawkoonLogoMotion.clipRight(80f, 1.4f), 0f)
        assertEquals(0f, RawkoonLogoMotion.clipRight(80f, -0.2f), 0f)
    }
}

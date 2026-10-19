package com.github.damontecres.wholphin.rawkoon.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class RawkoonLogoMotionTest {
    @Test
    fun `each opening turns the ring a quarter and ends on the axes`() {
        assertEquals(90f, RawkoonLogoMotion.nextRotation(0f), 0f)
        assertEquals(180f, RawkoonLogoMotion.nextRotation(90f), 0f)
        // An opening cut short at 37 degrees still ends at 90
        assertEquals(90f, RawkoonLogoMotion.nextRotation(37f), 0f)
    }

    @Test
    fun `wordmark clip grows with the reveal and stays within the width`() {
        assertEquals(0f, RawkoonLogoMotion.clipRight(80f, 0f), 0f)
        assertEquals(40f, RawkoonLogoMotion.clipRight(80f, 0.5f), 0f)
        assertEquals(80f, RawkoonLogoMotion.clipRight(80f, 1.4f), 0f)
        assertEquals(0f, RawkoonLogoMotion.clipRight(80f, -0.2f), 0f)
    }
}

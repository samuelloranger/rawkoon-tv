package cloud.rawkoon.tv.rawkoon

import org.junit.Assert.assertEquals
import org.junit.Test

class CleanServerUrlTest {
    @Test
    fun `zero-width, control and surrounding characters are dropped`() {
        assertEquals("https://rk.example", cleanServerUrl("https://rk.example​"))
        assertEquals("https://rk.example", cleanServerUrl("﻿ https://rk.exam⁠ple/ \n"))
    }

    @Test
    fun `a clean address is unchanged apart from the trailing slash`() {
        assertEquals("http://10.0.0.2:3000", cleanServerUrl("http://10.0.0.2:3000/"))
    }
}

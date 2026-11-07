package cloud.rawkoon.tv.playback

import android.view.Display
import cloud.rawkoon.tv.data.HdrOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceInfoFormatTest {
    private fun mode(id: Int, rate: Float, width: Int = 3840, height: Int = 2160) = DisplayModeInfo(id, width, height, rate)

    @Test
    fun `hdr types are named once and unknown ones are left out`() {
        val types = listOf(
            Display.HdrCapabilities.HDR_TYPE_HDR10,
            Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION,
            Display.HdrCapabilities.HDR_TYPE_HDR10,
            99,
        )
        assertEquals(listOf("HDR10", "Dolby Vision"), hdrTypeNames(types))
        assertEquals(emptyList<String>(), hdrTypeNames(emptyList()))
    }

    @Test
    fun `rates drop trailing zeros`() {
        assertEquals("60", formatRate(60f))
        assertEquals("59.94", formatRate(59.94f))
        assertEquals("23.976", formatRate(23.976f))
        assertEquals("24", formatRate(24.0001f))
    }

    @Test
    fun `modes group by resolution with the biggest first and rates ascending`() {
        val modes = listOf(
            mode(1, 60f, 1920, 1080),
            mode(2, 60f),
            mode(3, 23.976f),
            mode(4, 24f),
            mode(5, 60f),
        )
        assertEquals(
            listOf(
                "3840x2160" to listOf("23.976", "24", "60"),
                "1920x1080" to listOf("60"),
            ),
            groupModes(modes),
        )
    }

    @Test
    fun `an override that changes the answer is marked forced`() {
        assertEquals(HdrStatus(detected = false, effective = true), HdrStatus.of(false, HdrOverride.ALWAYS))
        assertTrue(HdrStatus.of(true, HdrOverride.NEVER).forced)
        assertFalse(HdrStatus.of(true, HdrOverride.ALWAYS).forced)
        assertFalse(HdrStatus.of(true, HdrOverride.AUTO).forced)
        assertFalse(HdrStatus.of(false, HdrOverride.AUTO).effective)
    }
}

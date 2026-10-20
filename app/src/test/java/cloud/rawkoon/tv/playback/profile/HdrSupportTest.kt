package cloud.rawkoon.tv.playback.profile

import cloud.rawkoon.tv.data.HdrOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HdrSupportTest {
    @Test
    fun `auto follows what the TV reports`() {
        assertTrue(HdrOverride.AUTO.resolve(true))
        assertFalse(HdrOverride.AUTO.resolve(false))
    }

    @Test
    fun `always allows and never forbids, whatever the TV reports`() {
        assertTrue(HdrOverride.ALWAYS.resolve(false))
        assertTrue(HdrOverride.ALWAYS.resolve(true))
        assertFalse(HdrOverride.NEVER.resolve(true))
        assertFalse(HdrOverride.NEVER.resolve(false))
    }

    @Test
    fun `an unknown stored key is auto`() {
        assertEquals(HdrOverride.AUTO, HdrOverride.of(null))
        assertEquals(HdrOverride.AUTO, HdrOverride.of("bogus"))
        assertEquals(HdrOverride.NEVER, HdrOverride.of("never"))
    }

    private fun hevc(dv: Boolean = true, el: Boolean = true, hdr10: Boolean = true, plus: Boolean = true, bug: Boolean = false) =
        unsupportedHevcRangeTypes(jellyfinTenEleven = true, dolbyVision = dv, dolbyVisionEL = el, hdr10 = hdr10, hdr10Plus = plus, doviHdr10PlusBug = bug)

    @Test
    fun `a TV with every format excludes only invalid Dolby Vision`() {
        assertEquals(setOf("DOVIInvalid"), hevc())
    }

    @Test
    fun `no HDR10 plus excludes HDR10 plus, and HDR10 too when HDR10 is out`() {
        assertTrue("HDR10Plus" in hevc(plus = false))
        assertFalse("HDR10" in hevc(plus = false))
        assertTrue("HDR10" in hevc(plus = false, hdr10 = false))
    }

    @Test
    fun `no Dolby Vision excludes every Dolby Vision range type`() {
        val out = hevc(dv = false, el = false, plus = false)
        assertTrue(listOf("DOVI", "DOVIWithEL", "DOVIWithELHDR10Plus", "DOVIWithHDR10Plus").all { it in out })
        assertFalse("DOVI" in hevc())
    }

    @Test
    fun `av1 follows the same overrides`() {
        val none = unsupportedAv1RangeTypes(true, dolbyVision = false, hdr10 = false, hdr10Plus = false)
        assertTrue(listOf("DOVI", "DOVIWithHDR10", "HDR10Plus", "HDR10").all { it in none })
        assertEquals(setOf("DOVIInvalid"), unsupportedAv1RangeTypes(true, dolbyVision = true, hdr10 = true, hdr10Plus = true))
    }

    @Test
    fun `an override flows through resolve into the excluded types`() {
        // A TV that reports no HDR10+ but is set to always allow it
        val plus = HdrOverride.ALWAYS.resolve(false)
        assertFalse("HDR10Plus" in hevc(plus = plus))
        // A TV that reports HDR10+ but is set to never allow it
        assertTrue("HDR10Plus" in hevc(plus = HdrOverride.NEVER.resolve(true)))
    }
}

package cloud.rawkoon.tv.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerVersionGateTest {
    private fun tooOld(raw: String?) = runCatching { ServerVersionGate.require(raw) }.exceptionOrNull() is JellyfinTooOldException

    @Test
    fun `versions below the minimum are too old`() {
        assertTrue(tooOld("10.11.0"))
        assertTrue(tooOld("11.9.9"))
        assertTrue(tooOld("10.10.7"))
    }

    @Test
    fun `the minimum and later are accepted`() {
        assertFalse(tooOld("12.0.0"))
        assertFalse(tooOld("12.0.1"))
        assertFalse(tooOld("12.3.0"))
        assertFalse(tooOld("13.0.0"))
    }

    @Test
    fun `a missing or unreadable version never blocks`() {
        assertFalse(tooOld(null))
        assertFalse(tooOld(""))
        assertFalse(tooOld("   "))
        assertFalse(tooOld("not a version"))
        assertNull(ServerVersionGate.parse("not a version"))
    }

    @Test
    fun `four-part versions compare like three-part ones`() {
        assertTrue(tooOld("10.11.0.0"))
        assertFalse(tooOld("12.0.0.0"))
    }

    @Test
    fun `a pre-release suffix is unreadable, so it never blocks`() {
        assertFalse(tooOld("12.0.0-rc1"))
    }

    @Test
    fun `an old version is reported with the version found`() {
        val e = runCatching { ServerVersionGate.require("10.11.5") }.exceptionOrNull()
        assertTrue(e is JellyfinTooOldException)
        assertEquals("10.11.5", (e as JellyfinTooOldException).version.toString())
    }
}

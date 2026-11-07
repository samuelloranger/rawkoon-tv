package cloud.rawkoon.tv.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerVersionGateTest {
    @Test
    fun `versions below the minimum are too old`() {
        assertTrue(ServerVersionGate.isTooOld("10.11.0"))
        assertTrue(ServerVersionGate.isTooOld("11.9.9"))
        assertTrue(ServerVersionGate.isTooOld("10.10.7"))
    }

    @Test
    fun `the minimum and later are accepted`() {
        assertFalse(ServerVersionGate.isTooOld("12.0.0"))
        assertFalse(ServerVersionGate.isTooOld("12.0.1"))
        assertFalse(ServerVersionGate.isTooOld("12.3.0"))
        assertFalse(ServerVersionGate.isTooOld("13.0.0"))
    }

    @Test
    fun `a missing or unreadable version never blocks`() {
        assertFalse(ServerVersionGate.isTooOld(null))
        assertFalse(ServerVersionGate.isTooOld(""))
        assertFalse(ServerVersionGate.isTooOld("   "))
        assertFalse(ServerVersionGate.isTooOld("not a version"))
        assertNull(ServerVersionGate.parse("not a version"))
    }

    @Test
    fun `four-part versions compare like three-part ones`() {
        assertTrue(ServerVersionGate.isTooOld("10.11.0.0"))
        assertFalse(ServerVersionGate.isTooOld("12.0.0.0"))
    }

    @Test
    fun `a pre-release suffix is unreadable, so it never blocks`() {
        assertFalse(ServerVersionGate.isTooOld("12.0.0-rc1"))
    }

    @Test
    fun `an old version is reported with the version found`() {
        val e = runCatching { ServerVersionGate.require("10.11.5") }.exceptionOrNull()
        assertTrue(e is JellyfinTooOldException)
        assertEquals("10.11.5", (e as JellyfinTooOldException).version.toString())
    }
}

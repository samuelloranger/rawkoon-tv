package cloud.rawkoon.tv

import org.junit.Assert.*
import org.junit.Test

class RemoteKeyGuardTest {
    @Test fun heldKeyCannotContinueOnAnotherPage() {
        val guard = RemoteKeyGuard()
        val home = Any()
        val details = Any()
        assertTrue(guard.accepts(23, true, 0, 100, home))
        assertFalse(guard.accepts(23, true, 1, 100, details))
        assertFalse(guard.accepts(23, false, 0, 100, details))
        assertTrue(guard.accepts(23, true, 0, 200, details))
        assertTrue(guard.accepts(23, false, 0, 200, details))
    }

    @Test fun heldDirectionsKeepRepeatingOnTheirPage() {
        val guard = RemoteKeyGuard()
        val page = Any()
        assertTrue(guard.accepts(20, true, 0, 100, page))
        for (repeat in 1..20) assertTrue(guard.accepts(20, true, repeat, 100, page))
        assertTrue(guard.accepts(20, false, 0, 100, page))
        assertFalse(guard.accepts(20, false, 0, 100, page))
    }

    @Test fun backBurstDoesNotDismissMultipleLayers() {
        val guard = RemoteKeyGuard()
        assertTrue(guard.backAllowed(1000))
        assertFalse(guard.backAllowed(1100))
        assertFalse(guard.backAllowed(1299))
        assertTrue(guard.backAllowed(1300))
        assertTrue(guard.backAllowed(2000))
    }

    @Test fun orphanedReleaseAndRepeatAreIgnored() {
        val guard = RemoteKeyGuard()
        val page = Any()
        assertFalse(guard.accepts(4, false, 0, 100, page))
        assertFalse(guard.accepts(4, true, 2, 100, page))
    }
}

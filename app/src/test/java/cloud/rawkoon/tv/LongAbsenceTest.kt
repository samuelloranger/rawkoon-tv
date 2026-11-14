package cloud.rawkoon.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LongAbsenceTest {
    @Test
    fun `the first start is not an absence`() {
        assertFalse(isLongAbsence(stoppedAt = 0, now = LONG_ABSENCE_MS * 10))
    }

    @Test
    fun `a short trip away keeps the page as it was`() {
        assertFalse(isLongAbsence(stoppedAt = 1_000, now = 1_000 + LONG_ABSENCE_MS - 1))
    }

    @Test
    fun `thirty minutes away or more starts over`() {
        assertTrue(isLongAbsence(stoppedAt = 1_000, now = 1_000 + LONG_ABSENCE_MS))
        assertTrue(isLongAbsence(stoppedAt = 1_000, now = 1_000 + LONG_ABSENCE_MS * 48))
    }
}

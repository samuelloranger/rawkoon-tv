package cloud.rawkoon.tv.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackoffTest {
    private fun top() = Backoff(random = { 1.0 })
    private fun bottom() = Backoff(random = { 0.0 })

    @Test
    fun `waits double from two seconds up to a minute`() {
        val backoff = top()
        assertEquals(listOf(2_000L, 4_000, 8_000, 16_000, 32_000, 60_000, 60_000), List(7) { backoff.afterConnection(0) })
    }

    @Test
    fun `jitter spreads each wait over its upper half`() {
        val backoff = bottom()
        assertEquals(listOf(1_000L, 2_000, 4_000), List(3) { backoff.afterConnection(0) })
        assertEquals(30_000L, List(6) { backoff.afterConnection(0) }.last())
    }

    @Test
    fun `real jitter stays inside the bounds`() {
        val backoff = Backoff()
        repeat(50) { assertTrue(backoff.afterConnection(0) in 1_000L..60_000L) }
    }

    @Test
    fun `a healthy connection starts the doubling over`() {
        val backoff = top()
        repeat(4) { backoff.afterConnection(0) }
        assertEquals(2_000L, backoff.afterConnection(Backoff.HEALTHY_MS))
        assertEquals(4_000L, backoff.afterConnection(1_000))
    }

    @Test
    fun `a connection that dropped at once does not reset it`() {
        val backoff = top()
        backoff.afterConnection(0)
        assertEquals(4_000L, backoff.afterConnection(Backoff.HEALTHY_MS - 1))
    }

    @Test
    fun `many failures never overflow`() {
        val backoff = top()
        repeat(200) { assertEquals(true, backoff.afterConnection(0) <= 60_000L) }
    }
}

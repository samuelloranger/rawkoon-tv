package cloud.rawkoon.tv.rawkoon

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class DeviceCodeFlowTest {
    private fun code(n: Int, interval: Int = 5) = DeviceCode("device-$n", "ABCD123$n", "https://example.test/link", null, interval)

    /** Plays [answers] one per poll, recording the codes shown and the pauses taken. */
    private class Script(answers: List<Any>, private val codes: List<DeviceCode>) {
        private val queue = answers.toMutableList()
        val shown = mutableListOf<DeviceCode>()
        val pauses = mutableListOf<Long>()
        val polled = mutableListOf<String>()
        private var issued = 0

        suspend fun run(): String? = DeviceCodeFlow.run(
            requestCode = { codes[issued++] },
            poll = { device ->
                polled += device
                when (val next = queue.removeAt(0)) {
                    is Exception -> throw next
                    else -> next as DevicePoll
                }
            },
            onCode = { shown += it },
            pause = { pauses += it },
        )
    }

    @Test
    fun `an approved code returns its token after waiting the server's interval`() = runBlocking {
        val script = Script(listOf(DevicePoll.Pending, DevicePoll.Granted("token")), listOf(code(1, interval = 3)))
        assertEquals("token", script.run())
        assertEquals(listOf(3_000L, 3_000L), script.pauses)
    }

    @Test
    fun `an interval below a second is raised to one`() = runBlocking {
        val script = Script(listOf(DevicePoll.Granted("t")), listOf(code(1, interval = 0)))
        script.run()
        assertEquals(listOf(1_000L), script.pauses)
    }

    @Test
    fun `slow down adds five seconds to every later wait`() = runBlocking {
        val script = Script(listOf(DevicePoll.SlowDown, DevicePoll.SlowDown, DevicePoll.Granted("t")), listOf(code(1)))
        script.run()
        assertEquals(listOf(5_000L, 10_000L, 15_000L), script.pauses)
    }

    @Test
    fun `an expired code is replaced, shown again and polled under its new id`() = runBlocking {
        val script = Script(listOf(DevicePoll.Expired, DevicePoll.Granted("t")), listOf(code(1), code(2, interval = 2)))
        assertEquals("t", script.run())
        assertEquals(listOf("device-1", "device-2"), script.polled)
        assertEquals(2, script.shown.size)
        assertEquals(listOf(5_000L, 2_000L), script.pauses)
    }

    @Test
    fun `a denied code ends the flow without a token`() = runBlocking {
        assertNull(Script(listOf(DevicePoll.Pending, DevicePoll.Denied), listOf(code(1))).run())
    }

    @Test
    fun `a dropped poll is retried and a good one resets the count`() = runBlocking {
        val blip = java.io.IOException("offline")
        val answers = listOf(blip, blip, DevicePoll.Pending, blip, blip, DevicePoll.Granted("t"))
        assertEquals("t", Script(answers, listOf(code(1))).run())
    }

    @Test
    fun `a run of dropped polls ends the sign-in with the last error`() = runBlocking {
        val answers = List(DeviceCodeFlow.MAX_POLL_FAILURES) { java.io.IOException("offline $it") }
        try {
            Script(answers, listOf(code(1))).run()
            fail("expected the failure to propagate")
        } catch (e: java.io.IOException) {
            assertEquals("offline ${DeviceCodeFlow.MAX_POLL_FAILURES - 1}", e.message)
        }
    }
}

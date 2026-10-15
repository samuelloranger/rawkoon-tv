package com.github.damontecres.wholphin.rawkoon

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceSignInTest {
    private val auth = mockk<RawkoonAuthClient>()
    private val base = "https://rawkoon.example.test"

    private fun code(
        n: Int,
        interval: Int = 5,
    ) = DeviceCode("dc$n", "ABCD123$n", "https://rawkoon.example.test/device", null, 600, interval)

    private fun polls(vararg answers: DevicePoll) {
        val queue = ArrayDeque(answers.toList())
        coEvery { auth.pollDeviceToken(any(), any()) } answers { queue.removeFirst() }
    }

    @Test
    fun pending_then_granted_signs_in_at_the_server_pace() =
        runTest {
            coEvery { auth.requestDeviceCode(base) } returns code(1)
            polls(DevicePoll.Pending, DevicePoll.Pending, DevicePoll.Granted("tok"))
            val states = DeviceSignIn(auth).run(base).toList()
            assertEquals(
                listOf(DeviceSignInState.RequestingCode, DeviceSignInState.Waiting(code(1)), DeviceSignInState.SignedIn("tok")),
                states,
            )
            assertEquals(15_000L, currentTime)
        }

    @Test
    fun slow_down_adds_five_seconds_to_every_later_poll() =
        runTest {
            coEvery { auth.requestDeviceCode(base) } returns code(1, interval = 2)
            polls(DevicePoll.SlowDown, DevicePoll.Pending, DevicePoll.Granted("tok"))
            DeviceSignIn(auth).run(base).toList()
            // 2s, then 7s, then 7s
            assertEquals(16_000L, currentTime)
        }

    @Test
    fun an_expired_code_is_replaced_and_shown() =
        runTest {
            coEvery { auth.requestDeviceCode(base) } returnsMany listOf(code(1), code(2))
            polls(DevicePoll.Expired, DevicePoll.Granted("tok"))
            val states = DeviceSignIn(auth).run(base).toList()
            assertEquals(
                listOf(
                    DeviceSignInState.RequestingCode,
                    DeviceSignInState.Waiting(code(1)),
                    DeviceSignInState.Waiting(code(2)),
                    DeviceSignInState.SignedIn("tok"),
                ),
                states,
            )
            coVerify(exactly = 1) { auth.pollDeviceToken(base, "dc2") }
        }

    @Test
    fun denied_ends_the_flow() =
        runTest {
            coEvery { auth.requestDeviceCode(base) } returns code(1)
            polls(DevicePoll.Denied)
            val states = DeviceSignIn(auth).run(base).toList()
            assertEquals(DeviceSignInState.Denied, states.last())
            coVerify(exactly = 1) { auth.pollDeviceToken(any(), any()) }
        }

    @Test
    fun a_failed_call_ends_the_flow_with_the_reason() =
        runTest {
            coEvery { auth.requestDeviceCode(base) } throws RawkoonException.Http(500)
            val last = DeviceSignIn(auth).run(base).toList().last() as DeviceSignInState.Failed
            assertEquals(500, (last.reason as RawkoonException.Http).code)

            coEvery { auth.requestDeviceCode(base) } returns code(1)
            coEvery { auth.pollDeviceToken(any(), any()) } throws RawkoonException.Http(503)
            assertEquals(true, DeviceSignIn(auth).run(base).toList().last() is DeviceSignInState.Failed)
        }
}

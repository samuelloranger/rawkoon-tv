package cloud.rawkoon.tv.rawkoon

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * The device-code sign-in, as the screens that show the code run it: ask for a code, poll at the
 * server's pace (a slow-down adds five seconds), ask for a new code when one expires. A dropped
 * request is retried a few times; only a run of them ends the sign-in.
 */
object DeviceCodeFlow {
    const val MAX_POLL_FAILURES = 3
    private const val SLOW_DOWN_MS = 5_000L

    /**
     * Runs the flow against [client], calling [onCode] with every code to show, and returns the
     * token once the code is approved, or null when it is denied. A failure that lasts is thrown.
     */
    suspend fun run(client: RawkoonClient, onCode: (DeviceCode) -> Unit): String? =
        run(client::requestDeviceCode, client::pollDeviceToken, onCode)

    internal suspend fun run(
        requestCode: suspend () -> DeviceCode,
        poll: suspend (deviceCode: String) -> DevicePoll,
        onCode: (DeviceCode) -> Unit,
        pause: suspend (Long) -> Unit = { delay(it) },
    ): String? {
        var device = requestCode()
        onCode(device)
        var interval = intervalOf(device)
        var failures = 0
        while (true) {
            pause(interval)
            val answer = try {
                poll(device.deviceCode).also { failures = 0 }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (++failures >= MAX_POLL_FAILURES) throw e
                continue
            }
            when (answer) {
                DevicePoll.Pending -> Unit
                DevicePoll.SlowDown -> interval += SLOW_DOWN_MS
                DevicePoll.Expired -> {
                    device = requestCode()
                    onCode(device)
                    interval = intervalOf(device)
                }
                DevicePoll.Denied -> return null
                is DevicePoll.Granted -> return answer.token
            }
        }
    }

    private fun intervalOf(device: DeviceCode) = device.interval.coerceAtLeast(1) * 1_000L
}

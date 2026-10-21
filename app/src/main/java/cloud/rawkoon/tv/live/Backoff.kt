package cloud.rawkoon.tv.live

/**
 * How long to wait before a stream reconnects: doubling from [baseMs] up to [maxMs], each wait
 * spread over its upper half so a server coming back is not met by every client at once. A
 * connection that held for [healthyMs] starts the doubling over.
 */
class Backoff(
    private val baseMs: Long = BASE_MS,
    private val maxMs: Long = MAX_MS,
    private val healthyMs: Long = HEALTHY_MS,
    private val random: () -> Double = Math::random,
) {
    private var failures = 0

    /** The wait after a connection that lasted [connectedMs] (0 when it never opened) has ended. */
    fun afterConnection(connectedMs: Long): Long {
        if (connectedMs >= healthyMs) failures = 0
        val ceiling = minOf(maxMs, baseMs shl minOf(failures, MAX_SHIFT))
        failures++
        return ceiling / 2 + (random().coerceIn(0.0, 1.0) * (ceiling - ceiling / 2)).toLong()
    }

    companion object {
        const val BASE_MS = 2_000L
        const val MAX_MS = 60_000L
        const val HEALTHY_MS = 30_000L
        private const val MAX_SHIFT = 20
    }
}

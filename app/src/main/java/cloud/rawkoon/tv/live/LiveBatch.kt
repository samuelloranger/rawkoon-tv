package cloud.rawkoon.tv.live

/**
 * Gathers the events of a burst into one delivery: a library scan or playback elsewhere sends many,
 * and each delivery reloads a page. The first event of a burst waits [windowMs] for the rest, and a
 * delivery never follows the previous one by less than [minGapMs], so a steady trickle (another
 * device's progress every few seconds) cannot keep a low-end TV reloading.
 */
class LiveBatch(private val windowMs: Long = WINDOW_MS, private val minGapMs: Long = MIN_GAP_MS) {
    private val pending = LinkedHashSet<LiveKind>()
    private var scheduled = false
    private var lastDeliveredAt: Long? = null

    /** Records [kind] at [now]; returns how long to wait before [drain] when this event starts a burst, else null (one is already due). */
    fun add(kind: LiveKind, now: Long): Long? {
        pending += kind
        if (scheduled) return null
        scheduled = true
        val earliest = lastDeliveredAt?.let { it + minGapMs - now } ?: 0L
        return maxOf(windowMs, earliest)
    }

    /** Hands over what gathered, at [now], and starts a new burst. */
    fun drain(now: Long): Set<LiveKind> {
        scheduled = false
        lastDeliveredAt = now
        return pending.toSet().also { pending.clear() }
    }

    /** Drops whatever gathered, e.g. when the connection closes: nothing may be delivered to the next screen's session. */
    fun clear() {
        pending.clear()
        scheduled = false
    }

    companion object {
        const val WINDOW_MS = 1_500L
        const val MIN_GAP_MS = 5_000L
    }
}

package cloud.rawkoon.tv.data

/** Visible work runs before background work; within each, first come, first served. */
internal class PriorityTask(val background: Boolean, val order: Long, val body: () -> Unit) : Runnable, Comparable<PriorityTask> {
    override fun run() = body()

    override fun compareTo(other: PriorityTask) = compareValuesBy(this, other, { it.background }, { it.order })
}

/** URLs that failed, remembered for [ttlMs] so a bad one is not retried on every bind. */
internal class FailureMemory(private val ttlMs: Long, private val max: Int, private val clock: () -> Long = System::currentTimeMillis) {
    private val failed = HashMap<String, Long>()

    @Synchronized
    fun recent(url: String): Boolean {
        val at = failed[url] ?: return false
        if (clock() - at < ttlMs) return true
        failed.remove(url)
        return false
    }

    @Synchronized
    fun clear() = failed.clear()

    @Synchronized
    fun add(url: String) {
        if (failed.size > max) failed.clear()
        failed[url] = clock()
    }
}

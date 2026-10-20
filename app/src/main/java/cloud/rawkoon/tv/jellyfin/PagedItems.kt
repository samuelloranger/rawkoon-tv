package cloud.rawkoon.tv.jellyfin

/**
 * A long list that is fetched a page at a time, in any order: a grid of thousands of titles knows
 * its [total] after the first page and fills in the rest as it is scrolled to. Pure bookkeeping, so
 * the paging rules are testable apart from views and network.
 *
 * Every [begin] starts a new [generation]; a response from an older one (a sort that changed while
 * it was on the way) is dropped.
 */
class PagedItems(val pageSize: Int = PAGE_SIZE) {
    var total = 0
        private set
    var generation = 0
        private set
    private val pages = HashMap<Int, List<MediaItem>>()
    private val requested = HashSet<Int>()
    private val failedAt = HashMap<Int, Long>()

    /** Forgets everything and returns the new generation; the first page is counted as asked for. */
    fun begin(): Int {
        generation++
        total = 0
        pages.clear()
        requested.clear()
        failedAt.clear()
        requested += 0
        return generation
    }

    operator fun get(position: Int): MediaItem? = pages[position / pageSize]?.getOrNull(position % pageSize)

    /**
     * The pages around [position] (within [margin] items) that are neither loaded nor on their way;
     * they count as asked for. A page that failed is left alone for [RETRY_MS], so a server that is
     * down is not asked again by every poster scrolled past.
     */
    fun claimAround(position: Int, margin: Int, nowMs: Long = System.currentTimeMillis()): List<Int> {
        if (total == 0) return emptyList()
        val first = (position - margin).coerceAtLeast(0) / pageSize
        val last = (position + margin).coerceAtMost(total - 1) / pageSize
        return (first..last).filter { page -> failedAt[page]?.let { nowMs - it < RETRY_MS } != true && requested.add(page) }
    }

    /**
     * Stores [items] as [page]; false when [gen] is no longer the current generation. The total
     * of the first page stands for the whole generation: a count that moved since would change the
     * grid's size without the adapter being told.
     */
    fun put(gen: Int, page: Int, items: List<MediaItem>, total: Int): Boolean {
        if (gen != generation) return false
        if (pages.isEmpty()) this.total = total
        pages[page] = items
        failedAt -= page
        return true
    }

    /** A page's request failed: it can be asked for again once [RETRY_MS] has passed. */
    fun release(gen: Int, page: Int, nowMs: Long = System.currentTimeMillis()) {
        if (gen != generation) return
        requested -= page
        failedAt[page] = nowMs
    }

    /** Forgets every loaded page not in [keep], so scrolling back to it fetches it afresh. */
    fun keepOnly(keep: Set<Int>) {
        pages.keys.retainAll(keep)
        requested.retainAll(keep)
        failedAt.clear()
    }

    companion object {
        const val PAGE_SIZE = 100
        const val RETRY_MS = 5_000L
    }
}

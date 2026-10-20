package cloud.rawkoon.tv.jellyfin

import kotlin.math.abs

/**
 * A long list that is fetched a page at a time, in any order: a grid of thousands of titles knows
 * its [total] after the first page and fills in the rest as it is scrolled to. Pure bookkeeping, so
 * the paging rules are testable apart from views and network.
 *
 * Every [begin] starts a new [generation]; a response from an older one (a sort that changed while
 * it was on the way) is dropped. Pages further than [keepRadius] pages from where the grid is
 * being looked at are evicted as new ones arrive, so a very large library stays bounded in memory.
 */
class PagedItems(val pageSize: Int = PAGE_SIZE, private val keepRadius: Int = KEEP_RADIUS) {
    var total = 0
        private set
    var generation = 0
        private set
    private val pages = HashMap<Int, List<MediaItem>>()
    private val requested = HashSet<Int>()
    private val failedAt = HashMap<Int, Long>()
    private var focusPage = 0

    /** Forgets everything and returns the new generation; the first page is counted as asked for. */
    fun begin(): Int {
        generation++
        total = 0
        pages.clear()
        requested.clear()
        failedAt.clear()
        focusPage = 0
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
        focusPage = position / pageSize
        val first = (position - margin).coerceAtLeast(0) / pageSize
        val last = (position + margin).coerceAtMost(total - 1) / pageSize
        var claimed: ArrayList<Int>? = null
        for (page in first..last) {
            if (page in requested || failedAt[page]?.let { nowMs - it < RETRY_MS } == true) continue
            requested += page
            (claimed ?: ArrayList<Int>(2).also { claimed = it }) += page
        }
        return claimed ?: emptyList()
    }

    /**
     * Stores [items] as [page]; false when [gen] is no longer the current generation. The total
     * of the first page stands for the whole generation: a count that moved since would change the
     * grid's size without the adapter being told. Loaded pages far from the focus are evicted, and
     * fetched again if scrolled back to.
     */
    fun put(gen: Int, page: Int, items: List<MediaItem>, total: Int): Boolean {
        if (gen != generation) return false
        if (pages.isEmpty()) this.total = total
        pages[page] = items
        failedAt -= page
        evictFarPages()
        return true
    }

    /** A page's request failed: it can be asked for again once [RETRY_MS] has passed. */
    fun release(gen: Int, page: Int, nowMs: Long = System.currentTimeMillis()) {
        if (gen != generation) return
        requested -= page
        failedAt[page] = nowMs
    }

    /**
     * Forgets every loaded page not in [keep], so scrolling back to it fetches it afresh. A page
     * still on its way is not loaded, so it stays claimed: asking for it again would fetch it twice.
     */
    fun keepOnly(keep: Set<Int>) {
        val dropped = pages.keys.filter { it !in keep }
        pages.keys.removeAll(dropped.toSet())
        requested.removeAll(dropped.toSet())
        failedAt.clear()
    }

    private fun evictFarPages() {
        if (pages.size <= 2 * keepRadius + 1) return
        val far = pages.keys.filter { abs(it - focusPage) > keepRadius }
        pages.keys.removeAll(far.toSet())
        requested.removeAll(far.toSet())
    }

    companion object {
        const val PAGE_SIZE = 100
        const val RETRY_MS = 5_000L

        /** Pages kept loaded on each side of the one in view. */
        const val KEEP_RADIUS = 7
    }
}

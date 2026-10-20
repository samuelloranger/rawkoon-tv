package cloud.rawkoon.tv.ui

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cloud.rawkoon.tv.data.Images
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.jellyfin.PagedItems

/** True when [this] sits somewhere under [ancestor]. */
fun View.isWithin(ancestor: View): Boolean {
    var parent = parent
    while (parent != null) {
        if (parent === ancestor) return true
        parent = parent.parent
    }
    return false
}

/**
 * The poster grid of the library pages: [SPAN] posters per row, backed by a [PagedItems] so only
 * the pages near the focus are ever fetched, and recycling [PosterCell]s.
 *
 * Android's own focus search and scroll-into-view are replaced: arrows move one poster by
 * position, and the focused row glides to a fixed spot under the page bar (a smooth scroll on the
 * same curve as the home rows), instead of scrolling the least it can. Rows that scroll past the
 * top are clipped by the container the screen puts this in.
 */
class PosterGrid(context: Context, private val images: Images, private val paged: PagedItems) : RecyclerView(context) {
    /** Focus moved to the poster at this position. */
    var onFocused: (Int) -> Unit = {}
    var onOpen: (MediaItem) -> Unit = {}

    /** Pages that need fetching: the focus got near them, or a poster of theirs was shown empty. */
    var onPages: (List<Int>) -> Unit = {}

    /** Where the arrows go past the grid: to the rail, the page bar and the letter column. */
    var onLeftEdge: () -> View? = { null }
    var onTopEdge: () -> View? = { null }
    var onRightEdge: () -> View? = { null }

    /** The position that last had focus, which is where focus returns to. */
    var lastPosition = 0
        private set

    private val manager = object : GridLayoutManager(context, SPAN) {
        // The glide below keeps the focused row in place; the stock scroll would fight it
        override fun requestChildRectangleOnScreen(parent: RecyclerView, child: View, rect: Rect, immediate: Boolean, focusedChildVisible: Boolean) = false
    }

    private val posters = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = paged.total

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val cell = PosterCell(context, images)
            cell.onFocused = ::focused
            cell.onOpen = { onOpen(it) }
            return object : RecyclerView.ViewHolder(cell) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = paged[position]
            (holder.itemView as PosterCell).bind(item, position)
            if (item == null) onPages(paged.claimAround(position, 0))
        }
    }

    init {
        layoutManager = manager
        adapter = posters
        itemAnimator = null
        setHasFixedSize(true)
        // Two rows kept bound beyond the screen: scrolling back costs no rebinding
        setItemViewCacheSize(2 * SPAN)
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFocusable = false
        noClip()
        // The top padding is the ring's room above the anchored row; the bottom lets the last row reach the anchor
        setPadding(context.dp(Tokens.CONTENT_START - Tokens.RING_INSET), context.dp(8), 0, context.dp(300))
    }

    /** Starts over at the first poster, e.g. after the sort changed. */
    fun reset() {
        stopScroll()
        lastPosition = 0
        posters.notifyDataSetChanged()
        manager.scrollToPositionWithOffset(0, 0)
    }

    /** Scrolls so the row of [position] is the anchored one, and makes it where focus goes back to. */
    fun jumpTo(position: Int) {
        stopScroll()
        lastPosition = position
        // The manager adds the start padding itself
        manager.scrollToPositionWithOffset(position, 0)
    }

    /** The posters of [page] have arrived: shows them. */
    fun pageLoaded(page: Int, firstLoad: Boolean) {
        if (firstLoad) {
            posters.notifyDataSetChanged()
            return
        }
        val start = page * paged.pageSize
        posters.notifyItemRangeChanged(start, minOf(paged.pageSize, paged.total - start))
    }

    /** The poster focus should return to: the last focused one, or the first on screen. */
    fun focusTarget(): View? {
        // Until the pending update is laid out the children are the old ones
        if (hasPendingAdapterUpdates()) return null
        return manager.findViewByPosition(lastPosition.coerceIn(0, (paged.total - 1).coerceAtLeast(0))) ?: getChildAt(0)
    }

    private fun focused(cell: PosterCell) {
        lastPosition = cell.position
        val dy = cell.top - paddingTop
        if (dy != 0) smoothScrollBy(0, dy, Tokens.ease, Tokens.GLIDE_MS.toInt())
        onPages(paged.claimAround(cell.position, PREFETCH))
        onFocused(cell.position)
    }

    /**
     * Arrows go to the poster at the next position instead of the nearest one on screen, so a row
     * never skips a poster and Down keeps its column. Beyond the grid they go where the screen says.
     */
    override fun focusSearch(focused: View, direction: Int): View? {
        val position = findContainingViewHolder(focused)?.bindingAdapterPosition ?: return super.focusSearch(focused, direction)
        val last = paged.total - 1
        val column = position % SPAN
        val target = when (direction) {
            FOCUS_LEFT -> if (column == 0) return onLeftEdge() ?: focused else position - 1
            FOCUS_RIGHT -> if (column == SPAN - 1 || position == last) return onRightEdge() ?: focused else position + 1
            FOCUS_UP -> if (position < SPAN) return onTopEdge() ?: focused else position - SPAN
            FOCUS_DOWN -> if (position / SPAN == last / SPAN) return focused else minOf(position + SPAN, last)
            else -> return super.focusSearch(focused, direction)
        }
        // A row scrolled out of view is not bound: lay it out first, then let the stock search find it
        return manager.findViewByPosition(target) ?: super.focusSearch(focused, direction)
    }

    companion object {
        const val SPAN = 7

        /** Three rows each way are fetched ahead of focus. */
        const val PREFETCH = 3 * SPAN
    }
}

package cloud.rawkoon.tv.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cloud.rawkoon.tv.data.Images
import cloud.rawkoon.tv.rawkoon.ShelfSummary

/**
 * The Shelves page's grid of cover cards, [SPAN] across. Arrows move by position so a row is never
 * skipped, and the focused row glides to a fixed spot under the page title, on the same curve as
 * the rest of the app. A page of shelves is short enough to build every card: nothing pages here.
 */
class ShelfGrid(context: Context, private val images: Images) : RecyclerView(context) {
    /** A cover opened. */
    var onOpen: (ShelfSummary) -> Unit = {}

    /** A cover focused, for the page's backdrop. */
    var onFocused: (ShelfSummary) -> Unit = {}

    /** Where Left goes from the first column. */
    var onLeftEdge: () -> View? = { null }

    private var shelves = emptyList<ShelfSummary>()

    /** The position focus returns to; kept across a reload. */
    var lastPosition = 0
        private set

    private val covers = object : Adapter<Holder>() {
        override fun getItemCount() = shelves.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(FrameLayout(context).apply {
                // Room between cards: a grid of covers needs more air than a row of posters
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = context.dp(ROW_GAP)
                    marginEnd = context.dp(COLUMN_GAP)
                }
            })

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(shelves[position], position)
    }

    /** A cell's box; the cover itself is built on bind, as a shelf's name, cover and count come together. */
    inner class Holder(private val box: FrameLayout) : ViewHolder(box) {
        fun bind(shelf: ShelfSummary, position: Int) {
            box.removeAllViews()
            val card = ShelfCoverCard(context, shelf, images, { focused(it, position) }, { onOpen(it.shelf) })
            box.addView(card, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    init {
        layoutManager = GridLayoutManager(context, SPAN)
        adapter = covers
        itemAnimator = null
        setHasFixedSize(true)
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFocusable = false
        clipChildren = false
        clipToPadding = false
        setPadding(context.dp(Tokens.CONTENT_START - Tokens.RING_INSET), context.dp(PAD_TOP), 0, context.dp(PAD_BOTTOM))
    }

    /** Shows [list]; the cover focused last keeps focus, and the grid stays where it was. */
    fun setShelves(list: List<ShelfSummary>) {
        val old = shelves
        val same = list.map { it.id } == old.map { it.id }
        val hadFocus = hasFocus()
        val position = lastPosition
        shelves = list
        if (same) {
            // Only a changed cover is rebuilt: a refresh with the same shelves must not redraw them all
            val changed = list.indices.filter { list[it] != old[it] }
            if (changed.isEmpty()) return
            changed.forEach { adapter?.notifyItemChanged(it) }
        } else {
            adapter?.notifyDataSetChanged()
        }
        lastPosition = position.coerceIn(0, (shelves.size - 1).coerceAtLeast(0))
        // After the rebind, not before: a card removed from its cell takes the focus with it
        if (hadFocus) focusWhenLaidOut(lastPosition)
    }

    /** Focuses the cover of shelf [id], e.g. when coming back from its page; nothing when it is gone. */
    fun focusShelf(id: Int) {
        val position = shelves.indexOfFirst { it.id == id }
        if (position < 0) return
        lastPosition = position
        layoutManager?.scrollToPosition(position)
        focusWhenLaidOut(position)
    }

    /** Gives the cover at [position] focus on the next frame, once any pending rebind has laid it out. */
    private fun focusWhenLaidOut(position: Int) {
        val observer = viewTreeObserver
        observer.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(this)
                coverAt(position)?.requestFocus()
                return true
            }
        })
    }

    private fun coverAt(position: Int): View? {
        val manager = layoutManager as? GridLayoutManager ?: return null
        return manager.findViewByPosition(position)?.let(::coverIn)
    }

    /** The cover focus should go to: the one focused last, or the first on screen. */
    fun focusTarget(): View? {
        if (hasPendingAdapterUpdates()) return null
        val manager = layoutManager as? GridLayoutManager ?: return firstCover()
        val view = manager.findViewByPosition(lastPosition.coerceIn(0, (shelves.size - 1).coerceAtLeast(0)))
        return view?.let(::coverIn) ?: firstCover()
    }

    private fun firstCover(): View? = (0 until childCount).firstNotNullOfOrNull { coverIn(getChildAt(it)) }

    /** The card inside a cell's box. */
    private fun coverIn(view: View): View? = (view as? ViewGroup)?.getChildAt(0)

    private fun focused(card: ShelfCoverCard, position: Int) {
        lastPosition = position
        val cell = (card.parent as? View) ?: return
        val dy = cell.top - paddingTop
        if (dy != 0) smoothScrollBy(0, dy, Tokens.ease, Tokens.GLIDE_MS.toInt())
        onFocused(card.shelf)
    }

    /** Arrows go to the cover at the next position instead of the nearest one on screen. */
    override fun focusSearch(focused: View, direction: Int): View? {
        val position = findContainingViewHolder(focused)?.bindingAdapterPosition ?: return super.focusSearch(focused, direction)
        val last = shelves.size - 1
        if (last < 0) return focused
        val column = position % SPAN
        val target = when (direction) {
            FOCUS_LEFT -> if (column == 0) return onLeftEdge() ?: focused else position - 1
            FOCUS_RIGHT -> if (column == SPAN - 1 || position == last) return focused else position + 1
            FOCUS_UP -> if (position < SPAN) return focused else position - SPAN
            FOCUS_DOWN -> if (position / SPAN == last / SPAN) return focused else minOf(position + SPAN, last)
            else -> return super.focusSearch(focused, direction)
        }
        return findViewHolderForAdapterPosition(target)?.let { coverIn(it.itemView) } ?: super.focusSearch(focused, direction)
    }

    private companion object {
        /** Four covers across: 16:9 cards wide enough to read a shelf's name under its art. */
        const val SPAN = 4

        /** Room above the anchored row for the focus ring. */
        const val PAD_TOP = 8

        /** Between the covers: the grid reads as separate cards, not a tiled sheet. */
        const val ROW_GAP = 20
        const val COLUMN_GAP = 12

        /** Lets the last row glide up to the anchor. */
        const val PAD_BOTTOM = 200
    }
}

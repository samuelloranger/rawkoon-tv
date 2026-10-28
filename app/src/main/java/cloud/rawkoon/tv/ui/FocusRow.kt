package cloud.rawkoon.tv.ui

import android.content.Context
import android.graphics.Rect
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.HorizontalScrollView
import android.widget.LinearLayout

/**
 * A horizontal row that keeps the focused card at a fixed spot on the left edge and glides the
 * content under it, and that hands focus back to the card you left when you come back from
 * another row.
 *
 * It never actually scrolls: the strip is translated with a ViewPropertyAnimator. Each move
 * retargets the running animation from wherever it is, so holding the D-pad glides instead of
 * jump-cutting, and translation is a render property, so moving costs no redraw.
 */
class FocusRow(context: Context) : HorizontalScrollView(context) {
    private var lastKey: String? = null
    private var lastIndex = 0

    /** Where the row last drew a focused card, so drawing order needs no search. */
    private var focusedIndex = -1

    /** Where the strip is gliding to, to tell a repeat of the same move from a new one. */
    private var glideTarget = 0f

    /** Asked when Left is pressed on the first card; returns the view that takes focus, or null to stay. */
    var onLeftEdge: (() -> View?)? = null

    val strip: LinearLayout = object : LinearLayout(context) {
        init {
            isChildrenDrawingOrderEnabled = true
        }

        /** The focused card draws last, so its scaled-up ring covers its neighbours. */
        override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int {
            val focused = focusedIndex
            if (focused < 0 || focused >= childCount) return drawingPosition
            return when {
                drawingPosition == childCount - 1 -> focused
                drawingPosition >= focused -> drawingPosition + 1
                else -> drawingPosition
            }
        }

        override fun requestChildFocus(child: View?, focused: View?) {
            super.requestChildFocus(child, focused)
            (child as? RowItem)?.let { item ->
                lastKey = item.key
                lastIndex = indexOfChild(child)
                reveal(child)
            }
            // The drawing order only changes here: invalidate once per focus move, not per card
            if (child != null) {
                focusedIndex = indexOfChild(child)
                invalidate()
            }
        }
    }

    init {
        isHorizontalScrollBarEnabled = false
        isHorizontalFadingEdgeEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFocusable = false
        clipChildren = false
        clipToPadding = false
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
        val padV = context.dp(8)
        strip.setPadding(context.dp(Tokens.CONTENT_START) - context.dp(Tokens.RING_INSET), padV, context.dp(Tokens.OVERSCAN_H), padV)
        strip.clipChildren = false
        strip.clipToPadding = false
        addView(strip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun setCards(cards: List<View>) {
        dropPendingSnap()
        strip.removeAllViews()
        val gap = context.dp(Tokens.CARD_GAP) - context.dp(Tokens.RING_INSET) * 2
        cards.forEachIndexed { i, card ->
            (card.layoutParams as LinearLayout.LayoutParams).marginStart = if (i == 0) 0 else gap
            strip.addView(card)
        }
        strip.animate().cancel()
        strip.translationX = 0f
        glideTarget = 0f
        focusedIndex = -1
        lastIndex = 0
        lastKey = null
    }

    /**
     * Fades the first cards in one after another, each rising a little, for a row that just landed
     * on a page already on screen. Only alpha and translation move, each card through its own
     * layer, so the stagger redraws no card. The cards past the screen edge appear as they are.
     */
    fun arrive(delay: Long = 0) {
        cards().take(ARRIVING).forEachIndexed { i, card -> card.riseIn(delay + i * Tokens.ARRIVE_STAGGER_MS) }
    }

    /**
     * Shows what changed on the cards since [before] (the cards this row had): a progress bar grows
     * from where it was, a new watched check pops in. Cards are matched by key; new ones stay still.
     */
    fun carryOver(before: List<View>) {
        val old = before.mapNotNull { it as? Card }.associateBy { it.key }
        cards().forEach { card -> (card as? Card)?.let { new -> old[new.key]?.let(new::changedFrom) } }
    }

    fun cards(): List<View> = (0 until strip.childCount).map { strip.getChildAt(it) }

    fun restoreFocus(): Boolean = remembered()?.requestFocus() == true

    /** The card focused last time, or the one now at its position if it's gone. */
    fun remembered(): View? {
        if (strip.childCount == 0) return null
        return cards().firstOrNull { (it as? RowItem)?.key == lastKey } ?: strip.getChildAt(lastIndex.coerceIn(0, strip.childCount - 1))
    }

    /** Points the remembered card at [key], so the next focus into this row lands on it. */
    fun remember(key: String) {
        val index = cards().indexOfFirst { (it as? RowItem)?.key == key }
        if (index < 0) return
        lastKey = key
        lastIndex = index
    }

    /**
     * Carries on from [old], the row this one replaces: the same card is remembered (or, when it
     * is gone, the one now at its position) and shown where [old] showed it. A row sharing no card
     * with [old] (new search results) is a new list, and starts at its first card. With
     * [showChanges], cards that changed since (progress, watched) show it.
     */
    fun takeOver(old: FocusRow, showChanges: Boolean = false) {
        if (showChanges) carryOver(old.cards())
        val oldKeys = old.cards().mapNotNullTo(HashSet()) { (it as? RowItem)?.key }
        if (cards().none { (it as? RowItem)?.key in oldKeys }) return
        val card = old.remembered() ?: return
        val key = (card as? RowItem)?.key
        val index = cards().indexOfFirst { key != null && (it as? RowItem)?.key == key }
            .takeIf { it >= 0 } ?: old.strip.indexOfChild(card)
        if (strip.childCount == 0) return
        lastIndex = index.coerceIn(0, strip.childCount - 1)
        lastKey = (strip.getChildAt(lastIndex) as? RowItem)?.key
        snapTo(strip.getChildAt(lastIndex))
    }

    /** Glides so [card] sits at the row's start, or as far as the row allows. */
    fun reveal(card: View) {
        val target = targetFor(card)
        if (glideTarget == target) return
        glideTarget = target
        strip.animate().translationX(target).setDuration(Tokens.GLIDE_MS).setInterpolator(Tokens.ease).start()
    }

    /**
     * Puts [card] at the row's start at once, before the next frame: a row rebuilt in place of
     * another shows its focused card where the old one had it, and new cards open on the one
     * asked for, with no glide from the first card.
     */
    fun snapTo(card: View) {
        dropPendingSnap()
        val place = {
            val target = targetFor(card)
            strip.animate().cancel()
            strip.translationX = target
            glideTarget = target
        }
        if (strip.isLaidOut && card.isLaidOut) return place()
        pendingSnap = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                dropPendingSnap()
                // The cards may have been replaced before the first frame
                if (card.parent === strip) place()
                return true
            }
        }.also { strip.viewTreeObserver.addOnPreDrawListener(it) }
    }

    /** A placement waiting for the first frame; dropped when the row goes away first, so no listener outlives it. */
    private var pendingSnap: ViewTreeObserver.OnPreDrawListener? = null

    private fun dropPendingSnap(observer: ViewTreeObserver = strip.viewTreeObserver) {
        pendingSnap?.let { observer.removeOnPreDrawListener(it) }
        pendingSnap = null
    }

    override fun onDetachedFromWindow() {
        // The strip detaches before its row, so only the row still holds the window's observer, where the listener was merged
        dropPendingSnap(viewTreeObserver)
        super.onDetachedFromWindow()
    }

    private fun targetFor(card: View): Float {
        val maxShift = (strip.width - width).coerceAtLeast(0)
        return -(card.left - strip.paddingStart).coerceIn(0, maxShift).toFloat()
    }

    /** Left/right never leave the row, except Left on the first card, which goes where [onLeftEdge] says. */
    override fun focusSearch(focused: View?, direction: Int): View? {
        if (direction != FOCUS_LEFT && direction != FOCUS_RIGHT) return super.focusSearch(focused, direction)
        // Geometry changes throughout a glide. Walk the strip's order instead of its animated bounds.
        var card = focused ?: return null
        while (card.parent !== strip) card = card.parent as? View ?: return focused
        val index = strip.indexOfChild(card)
        val step = if (direction == FOCUS_LEFT) -1 else 1
        var next = index + step
        while (next in 0 until strip.childCount) {
            val candidate = strip.getChildAt(next)
            if (candidate.isShown && candidate.isFocusable && candidate.isEnabled) return candidate
            next += step
        }
        return if (direction == FOCUS_LEFT) onLeftEdge?.invoke() ?: focused else focused
    }

    /** Coming from another row, only the card last focused here is a candidate. */
    override fun addFocusables(views: ArrayList<View>, direction: Int, focusableMode: Int) {
        val last = remembered()
        if (!hasFocus() && last != null && (direction == FOCUS_UP || direction == FOCUS_DOWN)) {
            views.add(last)
            return
        }
        super.addFocusables(views, direction, focusableMode)
    }

    // The stock scrolling is replaced by reveal(): no key-driven paging, no scroll-to-child.
    override fun executeKeyEvent(event: KeyEvent) = false
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect) = 0

    private companion object {
        /** More posters than fit across the screen; landscape cards fit fewer. */
        const val ARRIVING = 9
    }
}

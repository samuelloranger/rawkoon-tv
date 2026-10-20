package cloud.rawkoon.tv.ui

import android.content.Context
import android.graphics.Rect
import android.view.KeyEvent
import android.view.View
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

    /** Asked when Left is pressed on the first card; returns the view that takes focus, or null to stay. */
    var onLeftEdge: (() -> View?)? = null

    val strip: LinearLayout = object : LinearLayout(context) {
        init {
            isChildrenDrawingOrderEnabled = true
        }

        /** The focused card draws last, so its scaled-up ring covers its neighbours. */
        override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int {
            val focused = indexOfChild(focusedChild)
            if (focused < 0) return drawingPosition
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
        strip.removeAllViews()
        val gap = context.dp(Tokens.CARD_GAP) - context.dp(Tokens.RING_INSET) * 2
        cards.forEachIndexed { i, card ->
            (card.layoutParams as LinearLayout.LayoutParams).marginStart = if (i == 0) 0 else gap
            strip.addView(card)
        }
        strip.translationX = 0f
        lastIndex = 0
        lastKey = null
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

    /** Glides so [card] sits at the row's start, or as far as the row allows. */
    fun reveal(card: View) {
        val maxShift = (strip.width - width).coerceAtLeast(0)
        val target = -(card.left - strip.paddingStart).coerceIn(0, maxShift).toFloat()
        if (strip.translationX == target) return
        strip.animate().translationX(target).setDuration(Tokens.GLIDE_MS).setInterpolator(Tokens.ease).start()
    }

    /** Left/right never leave the row, except Left on the first card, which goes where [onLeftEdge] says. */
    override fun focusSearch(focused: View?, direction: Int): View? {
        val next = super.focusSearch(focused, direction)
        if ((direction == FOCUS_LEFT || direction == FOCUS_RIGHT) && (next == null || !contains(next))) {
            if (direction == FOCUS_LEFT) onLeftEdge?.invoke()?.let { return it }
            return focused
        }
        return next
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

    private fun contains(view: View): Boolean {
        var parent = view.parent
        while (parent != null) {
            if (parent === this) return true
            parent = parent.parent
        }
        return false
    }
}

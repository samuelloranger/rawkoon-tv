package cloud.rawkoon.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A card's actions in a small panel beside the card, pointing at it. The page stays as it is,
 * undimmed, so it reads as part of the card rather than a dialog over the page. Lives inside the
 * screen's view like [ChoiceDialog], so Back and focus stay with the screen: the screen calls
 * [close] on Back. Focus goes back to the card when it closes.
 */
@SuppressLint("ViewConstructor")
class CardMenu(context: Context, title: String, subtitle: String?, entries: List<Entry>) : FrameLayout(context) {
    /** One action: its Font Awesome glyph, its label, and what it does once the menu has closed. */
    class Entry(val glyph: String, val text: String, val run: () -> Unit)

    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val panel = object : LinearLayout(context) {
        /** A wrap-content LinearLayout leaves match-parent children out of its width: the rows' own width is added here, up to [MAX_W]. */
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            list.measure(unspecified, unspecified)
            val wanted = minOf(list.measuredWidth + paddingLeft + paddingRight, context.dp(MAX_W)).coerceAtLeast(measuredWidth)
            // Measured again at that width even when the title set it: the list was just measured at its own
            super.onMeasure(MeasureSpec.makeMeasureSpec(wanted, MeasureSpec.EXACTLY), heightMeasureSpec)
        }
    }.apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Tokens.surfaceHigh, context.dpf(RADIUS)).apply { setStroke(1, EDGE_LINE) }
        elevation = context.dpf(12f)
        minimumWidth = context.dp(MIN_W)
        setPadding(context.dp(6), context.dp(12), context.dp(6), context.dp(6))
    }
    private val caret = Caret(context)
    private val rows = mutableListOf<Row>()
    private var anchor: View? = null
    private val anchorRect = Rect()
    private val here = IntArray(2)
    private val there = IntArray(2)

    /** Whether the panel sits right of the card; it goes left when the right has no room. */
    private var onRight = true

    /** Set once [close] starts: the panel fades out with its rows already out of focus's reach. */
    var closing = false
        private set

    /** Runs once, after the menu has left: the page uses it to forget the menu. */
    var onClosed: () -> Unit = {}

    /** Where focus goes on close when the card is gone (the page rebuilt its rows meanwhile). */
    var fallbackFocus: () -> View? = { null }

    init {
        isFocusable = false
        val maxText = context.dp(MAX_W) - context.dp(12) * 2
        panel.addView(label(context, 15f, Tokens.textPrimary, Fonts.title(context)).apply {
            text = title
            maxWidth = maxText
        }, textParams())
        if (!subtitle.isNullOrBlank()) {
            panel.addView(label(context, 11.5f, Tokens.textMuted, Fonts.medium(context)).apply {
                text = subtitle
                maxWidth = maxText
            }, textParams().apply { topMargin = context.dp(3) })
        }
        panel.addView(FlatView(context).apply { setBackgroundColor(EDGE_LINE) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            topMargin = context.dp(10)
            bottomMargin = context.dp(4)
            marginStart = context.dp(6)
            marginEnd = context.dp(6)
        })
        panel.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        entries.forEach(::add)
        addView(panel, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // Level with the panel and drawn after it, so the panel's shadow doesn't cross the caret
        caret.elevation = panel.elevation
        addView(caret, LayoutParams(context.dp(CARET_W), context.dp(CARET_H)))
    }

    private fun textParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        marginStart = context.dp(12)
        marginEnd = context.dp(12)
    }

    /** Adds [entry] at the end; an action known late (whether the title is in "My list") comes in this way. */
    fun add(entry: Entry) {
        if (closing) return
        val row = Row(context, entry)
        row.setOnClickListener {
            close()
            entry.run()
        }
        rows += row
        list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(ROW_H)))
        if (isAttachedToWindow) row.fadeIn()
    }

    /** Opens over [parent] beside [anchor], the card it acts on, with focus on the first action. */
    fun open(parent: ViewGroup, anchor: View) {
        this.anchor = anchor
        parent.addView(this, fill())
        rows.firstOrNull()?.requestFocus()
        (anchor as? Focusable)?.holdFocusLook()
        // Placed on the first layout, so it grows from the side the card is on
        panel.alpha = 0f
        caret.alpha = 0f
        panel.scaleX = OPEN_SCALE
        panel.scaleY = OPEN_SCALE
        post {
            val shift = context.dpf(OPEN_SHIFT) * if (onRight) -1 else 1
            panel.translationX = shift
            caret.translationX = shift
            panel.pivotX = if (onRight) 0f else panel.width.toFloat()
            panel.pivotY = (caret.top + caret.height / 2f - panel.top).coerceIn(0f, panel.height.toFloat())
            panel.animate().alpha(1f).scaleX(1f).scaleY(1f).translationX(0f).setDuration(OPEN_MS).setInterpolator(Tokens.ease).start()
            caret.animate().alpha(1f).translationX(0f).setDuration(OPEN_MS).setInterpolator(Tokens.ease).start()
        }
    }

    fun close() {
        if (closing) return
        closing = true
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        // Focus goes back before the rows leave, so Android doesn't pick the page's first row meanwhile
        (anchor?.takeIf { it.isAttachedToWindow } ?: fallbackFocus())?.requestFocus()
        caret.animate().alpha(0f).setDuration(CLOSE_MS).start()
        panel.animate().alpha(0f).setDuration(CLOSE_MS).withEndAction {
            (parent as? ViewGroup)?.removeView(this)
            onClosed()
        }.start()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val card = anchor
        if (card == null || !card.isAttachedToWindow) return super.onLayout(changed, left, top, right, bottom)
        getLocationInWindow(here)
        card.getLocationInWindow(there)
        val x = there[0] - here[0]
        val y = there[1] - here[1]
        anchorRect.set(x, y, x + (card.width * card.scaleX).toInt(), y + (card.height * card.scaleY).toInt())
        val w = panel.measuredWidth
        val h = panel.measuredHeight
        val gap = context.dp(GAP)
        val edge = context.dp(Tokens.OVERSCAN_H)
        onRight = anchorRect.right + gap + w <= width - edge || anchorRect.left - gap - w < edge
        val panelLeft = if (onRight) anchorRect.right + gap else anchorRect.left - gap - w
        val panelTop = anchorRect.top.coerceIn(edge / 2, (height - edge / 2 - h).coerceAtLeast(edge / 2))
        panel.layout(panelLeft, panelTop, panelLeft + w, panelTop + h)
        // The caret points at the card's middle, kept on the panel's straight edge
        val cw = caret.measuredWidth
        val ch = caret.measuredHeight
        val inset = context.dp(RADIUS.toInt()) + ch / 2
        val cy = anchorRect.centerY().coerceIn(panelTop + inset, panelTop + h - inset)
        val cx = if (onRight) panelLeft - cw + 1 else panelLeft + w - 1
        caret.pointsLeft = onRight
        caret.layout(cx, cy - ch / 2, cx + cw, cy + ch / 2)
    }

    /** Keeps focus inside the panel while it is open. */
    override fun focusSearch(focused: View?, direction: Int): View? {
        val next = super.focusSearch(focused, direction)
        return if (next != null && next in rows) next else focused
    }

    /** Menu again closes it, as Back does. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_UP) close()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** One action: a light pill behind it when focused, like the page's main buttons. */
    private class Row(context: Context, entry: Entry) : FrameLayout(context) {
        private val fill = FlatView(context).apply {
            background = rounded(Tokens.textPrimary, context.dpf(8f))
            alpha = 0f
        }
        private val glyph: TextView = icon(context, entry.glyph, 12f, Tokens.textMuted)
        private val text: TextView = label(context, 14f, Tokens.textSoft, Fonts.medium(context)).apply { this.text = entry.text }

        init {
            isFocusable = true
            isClickable = true
            contentDescription = entry.text
            addView(fill, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            val line = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(context.dp(12), 0, context.dp(16), 0)
                addView(glyph, LinearLayout.LayoutParams(context.dp(18), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = context.dp(10) })
                addView(text)
            }
            addView(line, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setOnFocusChangeListener { _, focused ->
                fill.animate().alpha(if (focused) 1f else 0f).setDuration(if (focused) Tokens.FOCUS_IN_MS else Tokens.FOCUS_OUT_MS).start()
                glyph.setTextColor(if (focused) Tokens.background else Tokens.textMuted)
                text.setTextColor(if (focused) Tokens.background else Tokens.textSoft)
            }
        }

        fun fadeIn() {
            alpha = 0f
            animate().alpha(1f).setDuration(OPEN_MS).start()
        }
    }

    /** The small triangle on the panel's edge that points at the card. */
    private class Caret(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Tokens.surfaceHigh }
        private val path = Path()
        var pointsLeft = true
            set(value) {
                if (field != value) invalidate()
                field = value
            }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            path.reset()
            if (pointsLeft) {
                path.moveTo(w, 0f)
                path.lineTo(0f, h / 2)
                path.lineTo(w, h)
            } else {
                path.moveTo(0f, 0f)
                path.lineTo(w, h / 2)
                path.lineTo(0f, h)
            }
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    private companion object {
        const val MIN_W = 220
        const val MAX_W = 300
        const val ROW_H = 36
        const val RADIUS = 12f
        const val GAP = 10
        const val CARET_W = 8
        const val CARET_H = 16
        const val OPEN_MS = 180L
        const val CLOSE_MS = 100L
        const val OPEN_SCALE = 0.96f
        const val OPEN_SHIFT = 8f
        const val EDGE_LINE = 0x1FFFFFFF
    }
}

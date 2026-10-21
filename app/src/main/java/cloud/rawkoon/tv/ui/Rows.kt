package cloud.rawkoon.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** A row title in Fraunces, with the apricot bar to its left. */
@SuppressLint("ViewConstructor")
class SectionTitle(context: Context, text: String) : TextView(context) {
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Tokens.accent }
    private val rect = RectF()

    init {
        this.text = text
        textSize = 18f
        typeface = Fonts.title(context)
        setTextColor(Tokens.textPrimary)
        includeFontPadding = false
        maxLines = 1
        setPadding(context.dp(Tokens.CONTENT_START), 0, context.dp(Tokens.OVERSCAN_H), 0)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val x = paddingLeft - context.dpf(11f)
        rect.set(x, height * 0.12f, x + context.dpf(3f), height * 0.88f)
        canvas.drawRoundRect(rect, rect.width() / 2, rect.width() / 2, bar)
    }
}

/** A titled row of cards. [id] stays stable across reloads so unchanged rows are left alone. */
@SuppressLint("ViewConstructor")
class Section(context: Context, val id: String, title: String) : LinearLayout(context) {
    val row = FocusRow(context)
    var signature = ""

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        addView(SectionTitle(context, title))
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(6) })
        setPadding(0, 0, 0, context.dp(10))
    }
}

/**
 * The first [visible] rows (those on screen) stagger in, each a little after the one above; the
 * rest appear as they are.
 */
fun List<Section>.arrive(visible: Int = 3) = take(visible).forEachIndexed { i, section -> section.row.arrive(i * Tokens.ARRIVE_ROW_MS) }

/**
 * The vertical stack of sections. Like the rows, it glides by translation and anchors: the
 * focused section always sits at the top of the rows area, just under the header.
 */
class Rows(context: Context) : ScrollView(context) {
    /** Room kept above the focused row once it glides up, e.g. for its title under the screen edge. */
    var anchor = 0

    /** Where the column is gliding to, to tell a repeat of the same move from a new one. */
    private var glideTarget = 0f

    val column: LinearLayout = object : LinearLayout(context) {
        override fun requestChildFocus(child: View?, focused: View?) {
            super.requestChildFocus(child, focused)
            if (child != null) reveal(child)
        }
    }.apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
    }

    init {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFocusable = false
        // The top padding leaves room for the focused card to grow, the bottom lets the last row
        // still glide up to the anchor. The screen clips this view, so rows gliding above it never
        // pass over the header.
        column.setPadding(0, context.dp(6), 0, context.dp(200))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun sections(): List<Section> = (0 until column.childCount).map { column.getChildAt(it) as Section }

    /** Glides so [section] (a row, or any block in the column) sits at the top of this view. */
    fun reveal(section: View) {
        val maxShift = (column.height - height).coerceAtLeast(0)
        val target = -(section.top - column.paddingTop - anchor).coerceIn(0, maxShift).toFloat()
        if (glideTarget == target) return
        glideTarget = target
        column.animate().translationY(target).setDuration(Tokens.GLIDE_MS).setInterpolator(Tokens.ease).start()
    }

    /** The section holding focus, or the first one. */
    fun focusedSection(): Section? = (column.focusedChild as? Section) ?: sections().firstOrNull()

    fun reset() {
        column.animate().cancel()
        column.translationY = 0f
        glideTarget = 0f
    }

    override fun executeKeyEvent(event: KeyEvent) = false
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect) = 0
}

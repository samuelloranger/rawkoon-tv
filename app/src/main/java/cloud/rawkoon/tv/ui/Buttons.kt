package cloud.rawkoon.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A pill button: an optional icon and a label. [primary] is the light one for the main action.
 * Focus scales it and adds the ring, like a card.
 */
@SuppressLint("ViewConstructor")
class PillButton(context: Context, text: String, glyph: String? = null, primary: Boolean = false, height: Int = 34) : FrameLayout(context) {
    private val ring = FlatView(context)
    private val motion = FocusMotion(this, ring)
    val label: TextView

    init {
        isFocusable = true
        isClickable = true
        noClip()
        val radius = context.dpf(height / 2f)
        val inset = context.dp(Tokens.RING_INSET)
        val fg = if (primary) Tokens.background else Tokens.textPrimary
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(if (primary) Tokens.textPrimary else 0x24FFFFFF, radius)
            setPadding(context.dp(18), 0, context.dp(18), 0)
        }
        if (glyph != null) {
            row.addView(icon(context, glyph, 12f, fg), LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = context.dp(8) })
        }
        label = label(context, 13f, fg, Fonts.bold(context)).apply { this.text = text }
        row.addView(label)
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(height)).apply { setMargins(inset, inset, inset, inset) })
        ring.background = ringDrawable(context, radius)
        ring.alpha = 0f
        addView(ring, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setOnFocusChangeListener { _, focused -> motion.focus(focused) }
    }

    /**
     * Sized by the pill alone: a match-parent ring would otherwise stretch a wrap-content button to
     * all the room its parent offers.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val row = getChildAt(0)
        measureChildWithMargins(row, widthMeasureSpec, 0, heightMeasureSpec, 0)
        val params = row.layoutParams as LayoutParams
        val w = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(widthMeasureSpec) else row.measuredWidth + params.leftMargin + params.rightMargin
        val h = row.measuredHeight + params.topMargin + params.bottomMargin
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
            row.measure(MeasureSpec.makeMeasureSpec(w - params.leftMargin - params.rightMargin, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(row.measuredHeight, MeasureSpec.EXACTLY))
        }
        ring.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        setMeasuredDimension(w, h)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isConfirmKey(keyCode) && event.repeatCount == 0) motion.press(true)
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isConfirmKey(keyCode) && hasFocus()) motion.press(false)
        return super.onKeyUp(keyCode, event)
    }
}

/** A round action button with an icon: watched, favorite, more. [setOn] paints the icon apricot. */
class CircleButton(context: Context, glyph: String, description: String, private val filled: Boolean = false) : Focusable(context) {
    private val ring = FlatView(context)
    private val icon: TextView = icon(context, glyph, 14f, if (filled) Tokens.background else Tokens.textPrimary)
    override val motion = FocusMotion(this, ring)

    init {
        contentDescription = description
        val inset = context.dp(Tokens.RING_INSET)
        val size = context.dp(SIZE)
        icon.background = oval(if (filled) Tokens.textPrimary else 0x24FFFFFF)
        addView(icon, LayoutParams(size, size).apply { setMargins(inset, inset, inset, inset) })
        ring.background = ovalRing(context)
        ring.alpha = 0f
        addView(ring, LayoutParams(size + inset * 2, size + inset * 2))
        listenForFocus()
    }

    fun setOn(on: Boolean) = icon.setTextColor(if (on) Tokens.accent else if (filled) Tokens.background else Tokens.textPrimary)

    fun setGlyph(glyph: String) {
        icon.text = glyph
    }

    companion object {
        const val SIZE = 40
    }
}

package cloud.rawkoon.tv.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * A plain view for backgrounds, scrims and focus rings. Nothing inside it overlaps, so
 * skipping the offscreen layer keeps alpha fades cheap.
 */
class FlatView(context: Context) : View(context) {
    override fun hasOverlappingRendering() = false

    /**
     * Takes no room unless told its exact size. A match-parent ring inside a wrap-content frame
     * would otherwise grow the frame to all the space its parent offers; FrameLayout measures it
     * again at the frame's final size.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(widthMeasureSpec) else 0
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec) else 0
        setMeasuredDimension(w, h)
    }
}


/**
 * A row with one view in its exact middle and two groups beside it: [start] ends a gap before
 * the middle view, [end] begins a gap after it. Hiding a button on one side never moves the middle.
 */
class CenteredRow(context: Context, private val start: View, private val middle: View, private val end: View, private val gap: Int) : ViewGroup(context) {
    init {
        addView(start)
        addView(middle)
        addView(end)
        clipChildren = false
        clipToPadding = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        for (child in listOf(start, middle, end)) child.measure(unspecified, unspecified)
        val height = maxOf(start.measuredHeight, middle.measuredHeight, end.measuredHeight)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val h = b - t
        val center = (r - l) / 2
        fun place(v: View, left: Int) {
            val top = (h - v.measuredHeight) / 2
            v.layout(left, top, left + v.measuredWidth, top + v.measuredHeight)
        }
        place(middle, center - middle.measuredWidth / 2)
        place(start, middle.left - gap - start.measuredWidth)
        place(end, middle.right + gap)
    }
}

/** A row or column of labels that never overlap, so fading it needs no offscreen layer. */
internal class FlatLinearLayout(context: Context) : LinearLayout(context) {
    override fun hasOverlappingRendering() = false
}

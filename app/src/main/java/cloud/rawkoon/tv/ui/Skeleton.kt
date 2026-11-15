package cloud.rawkoon.tv.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Where a page's cards will be, drawn as quiet blocks that breathe while it loads, so the page has
 * its shape before its content. One cell's [blocks] (in dp from the cell's corner) repeat
 * [columns] × [rows] times, [stepX] and [stepY] dp apart; each row down is fainter, so the
 * placeholder fades into the page instead of ending on a hard edge.
 */
class Skeleton(
    context: Context,
    private val columns: Int,
    private val rows: Int,
    private val stepX: Float,
    private val stepY: Float,
    private val blocks: List<RectF>,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Tokens.surfaceHigh }
    private val radius = context.dpf(Tokens.CARD_RADIUS)
    private val rect = RectF()
    private val pulse = ObjectAnimator.ofFloat(this, ALPHA, 1f, LOW).apply {
        duration = PULSE_MS
        interpolator = AccelerateDecelerateInterpolator()
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
    }

    override fun hasOverlappingRendering() = false

    override fun onDraw(canvas: Canvas) {
        val dp = context.dpf(1f)
        for (row in 0 until rows) {
            paint.alpha = (255 * (1f - row * ROW_FADE)).toInt().coerceAtLeast(0)
            for (column in 0 until columns) {
                val x = paddingLeft + column * stepX * dp
                val y = paddingTop + row * stepY * dp
                blocks.forEach { b ->
                    rect.set(x + b.left * dp, y + b.top * dp, x + b.right * dp, y + b.bottom * dp)
                    // Caption bars are pills; the art keeps the cards' corners
                    val r = if (b.height() < BAR_MAX_H) rect.height() / 2 else radius
                    canvas.drawRoundRect(rect, r, r, paint)
                }
            }
        }
    }

    /** Breathes only while it can be seen: a loaded page leaves no animator running under it. */
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible && isAttachedToWindow) pulse.start() else pulse.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isShown) pulse.start()
    }

    override fun onDetachedFromWindow() {
        pulse.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val LOW = 0.45f
        const val PULSE_MS = 900L
        const val ROW_FADE = 0.4f

        /** A block thinner than this is a caption line, drawn as a pill. */
        const val BAR_MAX_H = 16f
    }
}

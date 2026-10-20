package cloud.rawkoon.tv.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View

/**
 * The player's progress bar: buffered and played spans, and a knob that grows when the bar has
 * focus. While scrubbing, the knob shows the target and a faint mark stays at the real position.
 */
class SeekBar(context: Context) : View(context) {
    var durationMs = 0L
    var positionMs = 0L
    var bufferedMs = 0L

    /** Where a scrub would land, or null when not scrubbing. */
    var scrubMs: Long? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFFFFF }
    private val buffer = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }
    private val played = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Tokens.accent }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Tokens.textPrimary }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55F0A35E }
    private val rect = RectF()
    private val pad = context.dpf(10f)
    private val idleHeight = context.dpf(3.5f)
    private val focusedHeight = context.dpf(5f)
    private val idleKnob = context.dpf(5.5f)
    private val focusedKnob = context.dpf(8f)
    private val haloGrow = context.dpf(4f)

    init {
        isFocusable = true
    }

    /** Sets what the bar shows, and redraws only when that moves the drawing by a pixel or more. */
    fun update(durationMs: Long, positionMs: Long, bufferedMs: Long) {
        val before = pixels()
        this.durationMs = durationMs
        this.positionMs = positionMs
        this.bufferedMs = bufferedMs
        if (pixels() != before) invalidate()
    }

    private fun pixels() = (xFor(positionMs) * 2).toInt() * 31 + (xFor(bufferedMs) * 2).toInt()

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }

    /** The x of [ms] on the bar, for placing the trickplay thumbnail over it. */
    fun xFor(ms: Long): Float {
        val fraction = if (durationMs > 0) (ms.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        return pad + (width - pad * 2) * fraction
    }

    override fun onDraw(canvas: Canvas) {
        val h = if (isFocused) focusedHeight else idleHeight
        val cy = height / 2f
        val left = pad
        val right = width - pad
        rect.set(left, cy - h / 2, right, cy + h / 2)
        canvas.drawRoundRect(rect, h / 2, h / 2, track)
        rect.right = xFor(bufferedMs)
        canvas.drawRoundRect(rect, h / 2, h / 2, buffer)
        rect.right = xFor(positionMs)
        canvas.drawRoundRect(rect, h / 2, h / 2, played)
        val at = xFor(scrubMs ?: positionMs)
        val r = if (isFocused) focusedKnob else idleKnob
        if (isFocused) canvas.drawCircle(at, cy, r + haloGrow, halo)
        canvas.drawCircle(at, cy, r, knob)
    }
}

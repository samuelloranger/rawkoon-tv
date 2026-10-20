package cloud.rawkoon.tv.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
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

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFFFFF }
    private val buffer = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }
    private val played = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Tokens.accent }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Tokens.textPrimary }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55F0A35E }
    private val rect = RectF()
    private val pad = context.dpf(10f)

    init {
        isFocusable = true
        setOnFocusChangeListener { _, _ -> invalidate() }
    }

    /** The x of [ms] on the bar, for placing the trickplay thumbnail over it. */
    fun xFor(ms: Long): Float {
        val fraction = if (durationMs > 0) (ms.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        return pad + (width - pad * 2) * fraction
    }

    override fun onDraw(canvas: Canvas) {
        val h = context.dpf(if (isFocused) 5f else 3.5f)
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
        val r = context.dpf(if (isFocused) 8f else 5.5f)
        if (isFocused) canvas.drawCircle(at, cy, r + context.dpf(4f), halo)
        canvas.drawCircle(at, cy, r, knob)
    }
}

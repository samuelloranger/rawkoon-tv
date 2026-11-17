package cloud.rawkoon.tv.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View

/**
 * A view that draws one bitmap. Unlike ImageView, setting the bitmap only invalidates: it never
 * requests a layout, so a burst of images arriving costs draws, not layout passes. The
 * [placeholder] is drawn until a bitmap is set, and under one still fading in ([show]).
 */
open class BitmapView(context: Context, private val mode: Mode = Mode.FIT_XY) : View(context) {
    enum class Mode {
        /** Stretched over the whole view: art already decoded at the view's size. */
        FIT_XY,

        /** Scaled to cover the view, cropping the overflow evenly. */
        CENTER_CROP,

        /** Drawn at its own size (shrunk to fit if larger) in the bottom-left corner: logos. */
        CONTAIN_BOTTOM_START,
    }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = Rect()

    var placeholder: Drawable? = null
        set(value) {
            field = value
            if (bitmap == null) invalidate()
        }

    var bitmap: Bitmap? = null
        set(value) {
            if (field === value) return
            field = value
            fade?.cancel()
            paint.alpha = 255
            invalidate()
        }

    private var fade: ValueAnimator? = null

    /**
     * How the owner fetches this art again, set by art that can come back from the disk cache.
     * Only such art is dropped by [release]; a view without one keeps its bitmap.
     */
    var reload: (() -> Unit)? = null

    /** Drops the bitmap while the app is in the background, if [reload] can bring it back. */
    fun release() {
        if (reload != null) bitmap = null
    }

    /** Asks again for art [release] dropped. */
    fun restore() {
        if (bitmap == null) reload?.invoke()
    }

    /**
     * Sets [value]; with [fading] it comes in over the placeholder instead of popping, for art
     * that arrived after the view was already on screen. Art that was in memory shows at once.
     */
    fun show(value: Bitmap, fading: Boolean) {
        // The same art again (two waiters on one load): a fade in progress carries on
        if (value === bitmap) return
        bitmap = value
        if (!fading || !isShown) return
        paint.alpha = 0
        fade = ValueAnimator.ofInt(0, 255).apply {
            duration = Tokens.ART_FADE_MS
            interpolator = Tokens.settle
            addUpdateListener {
                paint.alpha = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        fade?.cancel()
        paint.alpha = 255
        super.onDetachedFromWindow()
    }

    override fun hasOverlappingRendering() = false

    override fun onDraw(canvas: Canvas) {
        val b = bitmap
        if (b == null || paint.alpha < 255) {
            placeholder?.apply {
                setBounds(0, 0, width, height)
                draw(canvas)
            }
            if (b == null) return
        }
        when (mode) {
            Mode.FIT_XY -> {
                dst.set(0, 0, width, height)
                canvas.drawBitmap(b, null, dst, paint)
            }
            Mode.CENTER_CROP -> {
                val scale = maxOf(width.toFloat() / b.width, height.toFloat() / b.height)
                val cropW = (width / scale).toInt().coerceIn(1, b.width)
                val cropH = (height / scale).toInt().coerceIn(1, b.height)
                val left = (b.width - cropW) / 2
                val top = (b.height - cropH) / 2
                src.set(left, top, left + cropW, top + cropH)
                dst.set(0, 0, width, height)
                canvas.drawBitmap(b, src, dst, paint)
            }
            Mode.CONTAIN_BOTTOM_START -> {
                val scale = minOf(1f, width.toFloat() / b.width, height.toFloat() / b.height)
                val w = (b.width * scale).toInt()
                val h = (b.height * scale).toInt()
                dst.set(0, height - h, w, height)
                canvas.drawBitmap(b, null, dst, paint)
            }
        }
    }
}

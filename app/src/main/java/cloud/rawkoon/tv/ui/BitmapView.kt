package cloud.rawkoon.tv.ui

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
 * [placeholder] is drawn until a bitmap is set (and never under it).
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
            invalidate()
        }

    override fun hasOverlappingRendering() = false

    override fun onDraw(canvas: Canvas) {
        val b = bitmap
        if (b == null) {
            placeholder?.apply {
                setBounds(0, 0, width, height)
                draw(canvas)
            }
            return
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

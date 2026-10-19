package cloud.rawkoon.tv.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors

/**
 * Small image pipeline: [DiskCache] for remote art, a memory cache for decoded bitmaps, and
 * every bitmap produced at the exact pixel size (and corner radius) it will be drawn at, so
 * the GPU only ever blits.
 */
class Images(context: Context, fetcher: Fetcher) {
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    val disk = DiskCache(context, fetcher)
    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /**
     * Loads [url] center-cropped to [w] x [h]. [isCurrent] lets the caller drop stale requests
     * (a card scrolled away, a hero that moved on); it is also checked from the worker thread,
     * so it must only read a volatile value.
     */
    fun load(
        url: String,
        w: Int,
        h: Int,
        radius: Float,
        opaque: Boolean = false,
        contain: Boolean = false,
        isCurrent: () -> Boolean,
        onReady: (Bitmap) -> Unit,
    ) {
        val key = "$url|$w|$h|$radius|$opaque|$contain"
        memory.get(key)?.let {
            onReady(it)
            return
        }
        pool.execute {
            if (!isCurrent()) return@execute
            val bitmap = try {
                decode(url, w, h, radius, opaque, contain)
            } catch (e: Exception) {
                null
            } ?: return@execute
            memory.put(key, bitmap)
            main.post { if (isCurrent()) onReady(bitmap) }
        }
    }

    private fun decode(url: String, w: Int, h: Int, radius: Float, opaque: Boolean, contain: Boolean): Bitmap? {
        val file = disk.file(url) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        val raw = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            if (opaque) inPreferredConfig = Bitmap.Config.RGB_565
        }) ?: return null
        return if (contain) inside(raw, w, h) else fit(raw, w, h, radius, opaque)
    }

    /** Scales [raw] down to fit inside w x h, keeping its shape: logos, which must never be cropped. */
    private fun inside(raw: Bitmap, w: Int, h: Int): Bitmap {
        val scale = minOf(w.toFloat() / raw.width, h.toFloat() / raw.height, 1f)
        val out = Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt().coerceAtLeast(1), (raw.height * scale).toInt().coerceAtLeast(1), true)
        if (out !== raw) raw.recycle()
        return out
    }

    /** Center-crops [raw] into a w x h bitmap, rounding the corners when [radius] is set. */
    private fun fit(raw: Bitmap, w: Int, h: Int, radius: Float, opaque: Boolean): Bitmap {
        val scale = maxOf(w.toFloat() / raw.width, h.toFloat() / raw.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((w - raw.width * scale) / 2f, (h - raw.height * scale) / 2f)
        }
        val out = Bitmap.createBitmap(w, h, if (opaque) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val canvas = Canvas(out)
        if (radius > 0f) {
            paint.shader = BitmapShader(raw, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
            canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, paint)
        } else {
            canvas.drawBitmap(raw, matrix, paint)
        }
        raw.recycle()
        return out
    }
}

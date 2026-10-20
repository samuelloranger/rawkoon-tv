package cloud.rawkoon.tv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.FrameLayout
import cloud.rawkoon.tv.data.DiskCache
import java.util.concurrent.Executors

/**
 * The page background. Instead of drawing the image, the tint and the scrims as separate layers on
 * every frame, it blends them once, off the main thread, into a single screen-sized hardware
 * bitmap, then cross-fades two BitmapViews. While nothing changes the GPU draws one textured quad;
 * during the cross-fade, two.
 */
class Backdrop(context: Context, private val disk: DiskCache) : FrameLayout(context) {
    private val bottom = layer()
    private val top = layer()
    private val main = Handler(Looper.getMainLooper())

    /** The key (url and strength) asked for last; read from the worker to drop superseded composes. */
    @Volatile
    private var requested: String? = null
    private var shown: String? = null

    /** Whether [top] holds a bitmap still fading in: a new fade commits it to [bottom] first. */
    private var fading = false
    private var fadeId = 0

    /** The last few composed backdrops, so going back to one skips the decode and the blend. Main thread only. */
    private val composed = LruCache<String, Bitmap>(CACHED)

    /** How much of the image shows: 1 on home, lower behind denser pages. Part of the cache key. */
    var strength = 1f

    init {
        setBackgroundColor(Tokens.background)
        addView(bottom, fill())
        addView(top, fill())
    }

    private fun layer() = BitmapView(context)

    /** Shows [url] (an image URL, or null for the plain page colour) once it is composed. */
    fun show(url: String?) {
        val strength = strength
        val key = url?.let { "$it|$strength" }
        if (key == requested) return
        requested = key
        if (key == shown) return
        if (url == null) {
            crossfade(null, null)
            return
        }
        composed.get(key)?.let {
            crossfade(key, it)
            return
        }
        val w = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val h = height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        worker.execute {
            if (requested != key) return@execute
            val bitmap = try {
                disk.file(url)?.let { compose(it.path, w, h, strength) }
            } catch (e: Exception) {
                null
            }
            main.post {
                if (bitmap == null) return@post
                composed.put(key, bitmap)
                if (requested == key) crossfade(key, bitmap)
            }
        }
    }

    /**
     * Fades to [bitmap] (or to the plain page colour when null). The fade is cancelled when the
     * next one starts and a cancelled animation runs no end action, so a fade in progress is
     * committed to [bottom] by hand first: the new fade starts from the image that was arriving,
     * not from an older one.
     */
    private fun crossfade(key: String?, bitmap: Bitmap?) {
        if (key == shown) return
        shown = key
        val id = ++fadeId
        top.animate().cancel()
        bottom.animate().cancel()
        if (fading) {
            bottom.bitmap = top.bitmap
            bottom.alpha = 1f
            top.bitmap = null
            top.alpha = 0f
            fading = false
        }
        if (bitmap == null) {
            bottom.animate().alpha(0f).setDuration(Tokens.BACKDROP_FADE_MS).setInterpolator(Tokens.ease).withEndAction {
                if (id != fadeId) return@withEndAction
                bottom.bitmap = null
                bottom.alpha = 1f
            }.start()
            return
        }
        fading = true
        top.bitmap = bitmap
        top.alpha = 0f
        top.animate().alpha(1f).setDuration(Tokens.BACKDROP_FADE_MS).setInterpolator(Tokens.ease).withEndAction {
            if (id != fadeId) return@withEndAction
            // The top layer is opaque and covers the bottom one, so resetting it is not visible
            bottom.bitmap = bitmap
            bottom.alpha = 1f
            top.bitmap = null
            top.alpha = 0f
            fading = false
        }.start()
    }

    companion object {
        private const val CACHED = 4
        private val worker = Executors.newSingleThreadExecutor()

        /**
         * Blends one backdrop: the image over the whole screen, then scrims tinted with a dark colour
         * taken from the image (on the left behind the header, at the bottom behind the rows, and a
         * light one at the top for the clock).
         */
        fun compose(path: String, w: Int, h: Int, strength: Float): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
            val image = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val tint = darkTint(image)
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            canvas.drawColor(tint)

            // The image fills the screen, cropped to fill, aligned to the top
            val scale = maxOf(w.toFloat() / image.width, h.toFloat() / image.height)
            val cropW = (w / scale).toInt()
            val cropH = (h / scale).toInt()
            val left = (image.width - cropW) / 2
            paint.alpha = (strength * 255).toInt()
            canvas.drawBitmap(image, Rect(left, 0, left + cropW, cropH), Rect(0, 0, w, h), paint)
            paint.alpha = 255
            image.recycle()
            // An even darkening first, so text stays readable over bright art anywhere on screen
            canvas.drawColor(withAlpha(Color.BLACK, Tokens.BACKDROP_DIM))

            val wf = w.toFloat()
            val hf = h.toFloat()
            paint.shader = LinearGradient(0f, 0f, 0f, hf * 0.2f, withAlpha(Color.BLACK, 0.45f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, wf, hf * 0.2f, paint)
            paint.shader = LinearGradient(0f, 0f, wf * 0.7f, 0f, withAlpha(tint, 0.92f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, wf, hf, paint)
            paint.shader = LinearGradient(
                0f, hf * 0.35f, 0f, hf,
                intArrayOf(Color.TRANSPARENT, withAlpha(tint, 0.7f), withAlpha(tint, 0.92f)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, hf * 0.35f, wf, hf, paint)
            return out.copy(Bitmap.Config.HARDWARE, false).also { out.recycle() }
        }

        private fun withAlpha(color: Int, alpha: Float) =
            Color.argb((alpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

        /**
         * The image's average colour, kept in hue but pushed dark (value 0.14) and softened, so scrims
         * read as part of the image instead of a flat brown band.
         */
        private fun darkTint(image: Bitmap): Int {
            // Halve in steps down to a few dozen pixels: one bilinear jump from a big image would
            // sample only four source pixels per output pixel
            var tiny = image
            while (tiny.width >= 64 && tiny.height >= 36) {
                val next = Bitmap.createScaledBitmap(tiny, tiny.width / 2, tiny.height / 2, true)
                if (tiny !== image) tiny.recycle()
                tiny = next
            }
            var r = 0L
            var g = 0L
            var b = 0L
            for (y in 0 until tiny.height) {
                for (x in 0 until tiny.width) {
                    val c = tiny.getPixel(x, y)
                    r += Color.red(c)
                    g += Color.green(c)
                    b += Color.blue(c)
                }
            }
            val n = tiny.width * tiny.height
            if (tiny !== image) tiny.recycle()
            val hsv = FloatArray(3)
            Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
            hsv[1] = minOf(hsv[1], 0.55f)
            hsv[2] = 0.14f
            return Color.HSVToColor(hsv)
        }
    }
}

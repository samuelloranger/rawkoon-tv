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
import android.os.Process
import android.util.LruCache
import java.io.File
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Small image pipeline: [DiskCache] for remote art, a memory cache for decoded bitmaps, and
 * every bitmap produced at the exact pixel size (and corner radius) it will be drawn at, so
 * the GPU only ever blits.
 */
class Images(context: Context, fetcher: Fetcher) {
    private val main = Handler(Looper.getMainLooper())
    val disk = DiskCache(context, fetcher)
    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** One job per key being fetched and decoded, with everyone waiting on it. Main thread only. */
    private val inflight = HashMap<String, Job>()

    /** URLs that failed lately and when, so a bad one is not retried on every bind. */
    private val failed = Collections.synchronizedMap(HashMap<String, Long>())
    private val warming = Collections.synchronizedSet(HashSet<String>())

    /** Visible loads run before background ones (prefetch); each lane is first come, first served. */
    private val pool = ThreadPoolExecutor(
        THREADS, THREADS, 0L, TimeUnit.MILLISECONDS, PriorityBlockingQueue<Runnable>(),
        object : ThreadFactory {
            private val count = AtomicInteger()
            override fun newThread(task: Runnable) = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                task.run()
            }, "images-${count.incrementAndGet()}")
        },
    )
    private val sequence = AtomicLong()

    private class Task(val background: Boolean, val order: Long, val body: () -> Unit) : Runnable, Comparable<Task> {
        override fun run() = body()

        override fun compareTo(other: Task) =
            compareValuesBy(this, other, { it.background }, { it.order })
    }

    private fun submit(background: Boolean, body: () -> Unit) = pool.execute(Task(background, sequence.getAndIncrement(), body))

    private class Waiter(val isCurrent: () -> Boolean, val onReady: (Bitmap) -> Unit)

    private class Job(
        val url: String,
        val key: String,
        val w: Int,
        val h: Int,
        val radius: Float,
        val opaque: Boolean,
        val contain: Boolean,
        var background: Boolean,
    ) {
        val waiters = CopyOnWriteArrayList<Waiter>()
        val started = AtomicBoolean()

        fun anyCurrent() = waiters.any { it.isCurrent() }
    }

    /**
     * Loads [url] center-cropped to [w] x [h]. [isCurrent] lets the caller drop stale requests
     * (a card scrolled away, a hero that moved on); it is also checked from the worker thread,
     * so it must only read a volatile value. Call from the main thread: concurrent loads of the
     * same bitmap share one fetch and decode. A [background] load (prefetch) waits behind visible ones.
     */
    fun load(
        url: String,
        w: Int,
        h: Int,
        radius: Float,
        opaque: Boolean = false,
        contain: Boolean = false,
        background: Boolean = false,
        isCurrent: () -> Boolean,
        onReady: (Bitmap) -> Unit,
    ) {
        val key = "$url|$w|$h|$radius|$opaque|$contain"
        memory.get(key)?.let {
            if (isCurrent()) onReady(it)
            return
        }
        if (recentlyFailed(url)) return
        val waiter = Waiter(isCurrent, onReady)
        inflight[key]?.let { job ->
            job.waiters += waiter
            // A visible load joining a background job must not wait behind the queue
            if (job.background && !background) {
                job.background = false
                submit(false) { run(job) }
            }
            return
        }
        val job = Job(url, key, w, h, radius, opaque, contain, background)
        job.waiters += waiter
        inflight[key] = job
        submit(background) { run(job) }
    }

    private fun recentlyFailed(url: String): Boolean {
        val at = failed[url] ?: return false
        if (System.currentTimeMillis() - at < FAILURE_MEMORY_MS) return true
        failed.remove(url)
        return false
    }

    private fun fail(url: String) {
        if (failed.size > MAX_FAILURES) failed.clear()
        failed[url] = System.currentTimeMillis()
    }

    /** Worker side of a job: download, then decode, giving up between the two if nobody wants it any more. */
    private fun run(job: Job) {
        if (!job.started.compareAndSet(false, true)) return
        var bitmap: Bitmap? = null
        var abandoned = false
        try {
            if (!job.anyCurrent()) {
                abandoned = true
            } else {
                val file = disk.file(job.url)
                if (file != null) {
                    if (!job.anyCurrent()) abandoned = true else bitmap = decode(file, job.w, job.h, job.radius, job.opaque, job.contain)
                }
            }
        } catch (e: Exception) {
            bitmap = null
        }
        if (bitmap != null) memory.put(job.key, bitmap)
        else if (!abandoned) fail(job.url)
        main.post { finish(job, bitmap, abandoned) }
    }

    private fun finish(job: Job, bitmap: Bitmap?, abandoned: Boolean) {
        inflight.remove(job.key)
        if (bitmap != null) {
            for (waiter in job.waiters) if (waiter.isCurrent()) waiter.onReady(bitmap)
        } else if (abandoned) {
            // Someone may have joined after the worker looked: send those through again
            for (waiter in job.waiters) {
                if (waiter.isCurrent()) load(job.url, job.w, job.h, job.radius, job.opaque, job.contain, false, waiter.isCurrent, waiter.onReady)
            }
        }
    }

    /** Downloads [url] into the disk cache in the background, so a later load skips the network. */
    fun warm(url: String) {
        if (recentlyFailed(url) || !warming.add(url)) return
        submit(true) {
            try {
                disk.file(url)
            } catch (e: Exception) {
                fail(url)
            } finally {
                warming.remove(url)
            }
        }
    }

    private fun decode(file: File, w: Int, h: Int, radius: Float, opaque: Boolean, contain: Boolean): Bitmap? {
        // Rounded corners need an alpha channel: RGB_565 would paint them black
        val rgb = opaque && radius <= 0f
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        val raw = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            if (rgb) inPreferredConfig = Bitmap.Config.RGB_565
        }) ?: return null
        return if (contain) inside(raw, w, h) else fit(raw, w, h, radius, rgb)
    }

    /** Scales [raw] down to fit inside w x h, keeping its shape: logos, which must never be cropped. */
    private fun inside(raw: Bitmap, w: Int, h: Int): Bitmap {
        val scale = minOf(w.toFloat() / raw.width, h.toFloat() / raw.height, 1f)
        val out = Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt().coerceAtLeast(1), (raw.height * scale).toInt().coerceAtLeast(1), true)
        if (out !== raw) raw.recycle()
        return out
    }

    /** Center-crops [raw] into a w x h bitmap, rounding the corners when [radius] is set. */
    private fun fit(raw: Bitmap, w: Int, h: Int, radius: Float, rgb: Boolean): Bitmap {
        val scale = maxOf(w.toFloat() / raw.width, h.toFloat() / raw.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((w - raw.width * scale) / 2f, (h - raw.height * scale) / 2f)
        }
        val out = Bitmap.createBitmap(w, h, if (rgb) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888)
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

    private companion object {
        const val THREADS = 3
        const val FAILURE_MEMORY_MS = 30_000L
        const val MAX_FAILURES = 256
    }
}

package cloud.rawkoon.tv.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import cloud.rawkoon.tv.data.sha1Hex
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Trickplay thumbnails for the seek bar. Tiles are fetched with the auth header into the cache,
 * and only the one thumbnail needed is decoded out of a tile, so scrubbing never decodes a whole
 * grid of images. The worker and the cache directory only exist once a thumbnail is asked for.
 */
class TrickplayLoader(private val context: Context, private val authorization: String) {
    private val dir: File by lazy { File(context.cacheDir, "trickplay").apply { mkdirs() } }
    private val workerLazy = lazy {
        Executors.newSingleThreadExecutor().also { it.execute { prune() } }
    }
    private val worker: ExecutorService get() = workerLazy.value
    private val main = Handler(Looper.getMainLooper())
    private val decoders = object : LruCache<String, BitmapRegionDecoder>(4) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: BitmapRegionDecoder, newValue: BitmapRegionDecoder?) = oldValue.recycle()
    }
    private val lock = Any()

    /** The tile and thumbnail the latest request is for; a result for anything else is dropped. */
    @Volatile
    private var wanted: String? = null

    @Volatile
    private var connection: HttpURLConnection? = null

    @Volatile
    private var released = false

    /** Loads the thumbnail for [positionMs]; [onReady] runs on the main thread, only if no newer request came in. */
    fun load(trickplay: Trickplay, positionMs: Long, onReady: (Bitmap) -> Unit) {
        if (released) return
        val (url, x, y) = trickplay.locate(positionMs)
        val key = "$url#${positionMs / trickplay.intervalMs}"
        if (key == wanted) return
        wanted = key
        worker.execute {
            if (wanted != key) return@execute
            val bitmap = synchronized(lock) {
                try {
                    val decoder = decoders.get(url) ?: decoder(url)?.also { decoders.put(url, it) }
                    decoder?.decodeRegion(Rect(x, y, x + trickplay.width, y + trickplay.height), BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
                } catch (e: Exception) {
                    null
                }
            } ?: return@execute
            main.post { if (wanted == key) onReady(bitmap) }
        }
    }

    /** Forgets the pending request, so a thumbnail still on its way (for another item, or an old scrub) never shows. */
    fun clear() {
        wanted = null
    }

    private fun decoder(url: String): BitmapRegionDecoder? {
        val file = File(dir, sha1Hex(url))
        if (file.isFile) {
            file.setLastModified(System.currentTimeMillis())
        } else {
            val connection = URL(url).openConnection() as HttpURLConnection
            this.connection = connection
            try {
                connection.setRequestProperty("Authorization", authorization)
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                if (connection.responseCode != 200) return null
                val tmp = File(dir, file.name + ".tmp")
                connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(file)
            } finally {
                connection.disconnect()
                this.connection = null
            }
        }
        @Suppress("DEPRECATION")
        return BitmapRegionDecoder.newInstance(file.path, false)
    }

    /** Tiles are only worth keeping while they are used: a hit refreshes the file's time, and the ones untouched for a week go. */
    private fun prune() {
        val old = System.currentTimeMillis() - 7 * 24 * 3_600_000L
        dir.listFiles()?.filter { it.lastModified() < old }?.forEach { it.delete() }
    }

    /** Stops the worker, aborts a tile download in flight and frees the decoders. */
    fun release() {
        released = true
        wanted = null
        connection?.disconnect()
        if (workerLazy.isInitialized()) worker.shutdownNow()
        synchronized(lock) { decoders.evictAll() }
    }
}

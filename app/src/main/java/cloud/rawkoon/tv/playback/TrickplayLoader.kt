package cloud.rawkoon.tv.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Trickplay thumbnails for the seek bar. Tiles are fetched with the auth header into the cache,
 * and only the one thumbnail needed is decoded out of a tile, so scrubbing never decodes a whole
 * grid of images.
 */
class TrickplayLoader(context: Context, private val authorization: String) {
    private val dir = File(context.cacheDir, "trickplay").apply { mkdirs() }
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val decoders = object : LruCache<String, BitmapRegionDecoder>(4) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: BitmapRegionDecoder, newValue: BitmapRegionDecoder?) = oldValue.recycle()
    }

    @Volatile
    private var wanted: Long = -1

    init {
        // Tiles are only worth keeping for a while: drop the ones not used in a week
        worker.execute {
            val old = System.currentTimeMillis() - 7 * 24 * 3_600_000L
            dir.listFiles()?.filter { it.lastModified() < old }?.forEach { it.delete() }
        }
    }

    /** Loads the thumbnail for [positionMs]; [onReady] runs on the main thread, only if no newer request came in. */
    fun load(trickplay: Trickplay, positionMs: Long, onReady: (Bitmap) -> Unit) {
        val (url, x, y) = trickplay.locate(positionMs)
        val key = positionMs / trickplay.intervalMs
        if (key == wanted) return
        wanted = key
        worker.execute {
            if (wanted != key) return@execute
            val bitmap = try {
                val decoder = decoders.get(url) ?: decoder(url)?.also { decoders.put(url, it) } ?: return@execute
                decoder.decodeRegion(Rect(x, y, x + trickplay.width, y + trickplay.height), BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
            } catch (e: Exception) {
                null
            } ?: return@execute
            main.post { if (wanted == key) onReady(bitmap) }
        }
    }

    private fun decoder(url: String): BitmapRegionDecoder? {
        val file = File(dir, sha1(url))
        if (!file.isFile) {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", authorization)
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            if (connection.responseCode != 200) return null
            val tmp = File(dir, file.name + ".tmp")
            connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(file)
        }
        @Suppress("DEPRECATION")
        return BitmapRegionDecoder.newInstance(file.path, false)
    }

    fun release() {
        worker.execute { decoders.evictAll() }
        worker.shutdown()
    }

    private fun sha1(text: String) = MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

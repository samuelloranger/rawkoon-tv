package cloud.rawkoon.tv.data

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Local copies of remote art, trimmed to a size budget, least recently used first. */
class DiskCache(context: Context, private val fetcher: Fetcher) {
    private val dir = File(context.cacheDir, "img").apply { mkdirs() }

    @Volatile
    private var lastTrim = 0L

    /** One lock per file being downloaded, so two callers asking for a URL fetch it once. */
    private val locks = ConcurrentHashMap<String, Any>()

    /** Local copy of [url]: remote art goes through the cache, local paths are used as they are. */
    fun file(url: String): File? {
        if (url.startsWith("/")) return File(url).takeIf { it.isFile }
        val cached = File(dir, sha1Hex(url))
        if (hit(cached)) return cached
        val lock = locks.computeIfAbsent(cached.name) { Any() }
        try {
            synchronized(lock) {
                if (hit(cached)) return cached
                // A temp file of its own: a half-written download is never visible under the final name
                val tmp = File.createTempFile(cached.name, ".tmp", dir)
                try {
                    fetcher.open(url).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    if (!tmp.renameTo(cached)) return null
                } finally {
                    tmp.delete()
                }
            }
        } finally {
            locks.remove(cached.name)
        }
        trim()
        return cached
    }

    /** A hit counts as a use, so trimming drops what was read least recently. */
    private fun hit(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        file.setLastModified(System.currentTimeMillis())
        return true
    }

    @Synchronized
    private fun trim() {
        val now = System.currentTimeMillis()
        if (now - lastTrim < TRIM_EVERY_MS) return
        lastTrim = now
        trimDir(dir, now, BUDGET, STALE_TMP_MS)
    }

    /** Bytes the cached files take, downloads in progress included. */
    fun size(): Long = dirSize(dir)

    /**
     * Deletes every cached copy. A download in progress keeps its temp file and lands afterwards;
     * a reader that already opened a deleted file still reads it, and one that did not fetches again.
     */
    fun clear() = clearDir(dir)

    private companion object {
        const val BUDGET = 60L * 1024 * 1024
        const val TRIM_EVERY_MS = 60_000L
        const val STALE_TMP_MS = 60 * 60_000L
    }
}

/** The SHA-1 of [text] as lowercase hex: the name a cached file gets from its URL. */
fun sha1Hex(text: String): String =
    MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

/**
 * Deletes the least recently modified files of [dir] until the rest fit [budget] bytes. A `.tmp`
 * file is a download in progress: it is left alone unless older than [staleTmpMs], and never counts.
 */
internal fun trimDir(dir: File, now: Long, budget: Long, staleTmpMs: Long) {
    val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
    var total = 0L
    for (file in files) {
        if (file.name.endsWith(".tmp")) {
            if (now - file.lastModified() > staleTmpMs) file.delete()
            continue
        }
        total += file.length()
        if (total > budget) file.delete()
    }
}

/** [bytes] for people: "512 B", "3.5 MB". Binary steps, one decimal from KB up. */
fun formatBytes(bytes: Long, locale: java.util.Locale = java.util.Locale.getDefault()): String {
    if (bytes < 1024) return "${bytes.coerceAtLeast(0)} B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(locale, "%.1f %s", value, units[unit])
}

/** The bytes the files of [dir] take. */
internal fun dirSize(dir: File): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

/** Deletes the files of [dir], except `.tmp` ones: those are downloads in progress. */
internal fun clearDir(dir: File) {
    dir.listFiles()?.forEach { if (!it.name.endsWith(".tmp")) it.delete() }
}

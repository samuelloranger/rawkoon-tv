package cloud.rawkoon.tv.jellyfin

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Each profile's TMDB → Jellyfin index: read once per app start, saved to disk for the next one,
 * and rebuilt when the library changes. A build that fails is retried after [RETRY_MS] and the
 * saved copy answers meanwhile, so a shelf never waits on a broken server.
 */
class TmdbIndexStore(context: Context) {
    // filesDir, not DiskCache: that is an LRU of images trimmed by size, and Android may purge cacheDir.
    // The index is small and costs a full library read to rebuild, and offline it is the only copy.
    private val dir = File(context.filesDir, "tmdb-index").apply { mkdirs() }
    private val memory = ConcurrentHashMap<String, TmdbIndex>()
    private val built = ConcurrentHashMap.newKeySet<String>()
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * The index for [session]'s profile, or null when the first build failed and nothing was saved:
     * an index that is missing is not an index that is empty, and the caller must not read it as
     * "no title is in the library". [refresh] is for a library that just changed: the memory
     * copy is dropped and the server read again, with the saved copy still there if that fails.
     */
    suspend fun index(session: JellyfinSession, refresh: Boolean = false): TmdbIndex? {
        val id = session.profile.id
        if (refresh) invalidate(id)
        memory[id]?.let { return it }
        val lock = locks.computeIfAbsent(id) { Mutex() }
        return lock.withLock {
            memory[id]?.let { return@withLock it }
            val saved = read(id)
            val retry = (failedAt[id] ?: 0L).let { it != 0L && System.currentTimeMillis() - it < RETRY_MS }
            if (built.contains(id) || retry) return@withLock saved
            try {
                val fresh = session.buildTmdbIndex()
                memory[id] = fresh
                built += id
                failedAt.remove(id)
                withContext(Dispatchers.IO) { write(id, fresh) }
                fresh
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failedAt[id] = System.currentTimeMillis()
                saved
            }
        }
    }

    /** The saved index without asking the server, for a page that can wait no longer. */
    suspend fun saved(profileId: String): TmdbIndex? = memory[profileId] ?: read(profileId)

    /** The library changed: the next [index] reads the server again. */
    fun invalidate() {
        built.clear()
        memory.clear()
        failedAt.clear()
    }

    private fun invalidate(profileId: String) {
        built.remove(profileId)
        memory.remove(profileId)
        failedAt.remove(profileId)
    }

    private suspend fun read(profileId: String): TmdbIndex? = withContext(Dispatchers.IO) {
        runCatching { TmdbIndex.fromJson(File(dir, "$profileId.json").readText()) }.getOrNull()
    }

    private fun write(profileId: String, index: TmdbIndex) {
        runCatching {
            val file = File(dir, "$profileId.json")
            val partial = File(file.path + ".tmp")
            partial.writeText(index.toJson().toString())
            partial.renameTo(file)
        }
    }

    private companion object {
        /** A failed read is retried this soon, so a server that just came back is asked again. */
        const val RETRY_MS = 60_000L
    }
}

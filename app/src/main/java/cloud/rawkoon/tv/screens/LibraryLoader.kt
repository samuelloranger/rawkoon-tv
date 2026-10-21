package cloud.rawkoon.tv.screens

import android.util.Log
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.LibraryQuery
import cloud.rawkoon.tv.jellyfin.PagedItems
import cloud.rawkoon.tv.jellyfin.libraryLetterIndex
import cloud.rawkoon.tv.jellyfin.libraryPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Loads the pages of one library grid: the first page, the pages fetched as focus nears them, the
 * jump to a letter and the refresh when the page is shown again. It owns the jobs and the [query];
 * what to show for each outcome is the [host]'s.
 */
class LibraryLoader(
    private val scope: CoroutineScope,
    private val session: JellyfinSession,
    private val paged: PagedItems,
    private val types: List<BaseItemKind>,
    private val parentId: String?,
    private val host: Host,
) {
    interface Host {
        /** The grid is starting over; [jumping] when it will scroll to a letter once loaded. */
        fun restarted(jumping: Boolean)

        /** The first page arrived; [jumpIndex] is where the letter starts, when a jump was asked for. */
        fun firstPageLoaded(total: Int, jumpIndex: Int?)

        /** A page fetched as focus neared it arrived. */
        fun pageFetched(page: Int)

        /** A page fetched again by [LibraryLoader.refresh] arrived. */
        fun pageRefreshed(page: Int)

        fun loadFailed(e: Exception)

        fun jumpTo(index: Int)
    }

    var query = LibraryQuery()
    private var loading: Job? = null
    private var jumping: Job? = null
    private var refreshing: Job? = null

    /** The page fetches on their way, by page; a restarted grid has no use for them. */
    private val pageJobs = HashMap<Int, Job>()

    /**
     * Fetches the first page and starts the grid over. With [jump], also asks where that letter
     * is and scrolls there; the pages around it are fetched as its posters are shown.
     */
    fun reload(jump: Char? = null) {
        loading?.cancel()
        refreshing?.cancel()
        // The pages on their way belong to the grid being replaced
        pageJobs.values.forEach { it.cancel() }
        pageJobs.clear()
        jumping?.cancel()
        val gen = paged.begin()
        host.restarted(jump != null)
        loading = scope.launch {
            try {
                val (first, index) = coroutineScope {
                    val page = async { session.libraryPage(types, parentId, query, 0, paged.pageSize) }
                    val at = jump?.let { letter -> async { session.libraryLetterIndex(types, parentId, query, letter) } }
                    page.await() to (at?.await() ?: 0)
                }
                if (!paged.put(gen, 0, first.items, first.total)) return@launch
                host.firstPageLoaded(first.total, if (jump != null) index else null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                host.loadFailed(e)
            }
        }
    }

    /** Fetches [pages] of the current grid; a failed page is asked for again the next time focus nears it. */
    fun fetch(pages: List<Int>) {
        val gen = paged.generation
        for (page in pages) {
            val job = scope.launch {
                try {
                    val result = session.libraryPage(types, parentId, query, page * paged.pageSize, paged.pageSize)
                    if (paged.put(gen, page, result.items, result.total)) host.pageFetched(page)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    paged.release(gen, page)
                }
            }
            pageJobs[page] = job
            job.invokeOnCompletion { pageJobs.remove(page, job) }
        }
    }

    /** Scrolls the grid to [letter]; it is already sorted by title. */
    fun jumpTo(letter: Char) {
        val gen = paged.generation
        jumping?.cancel()
        jumping = scope.launch {
            try {
                val index = session.libraryLetterIndex(types, parentId, query, letter)
                if (gen == paged.generation && paged.total > 0) host.jumpTo(index.coerceAtMost(paged.total - 1))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The grid stays where it is; picking the letter again retries
                Log.w(TAG, "letter jump failed", e)
            }
        }
    }

    /**
     * Coming back to the page: the posters around [position] are fetched again so watched marks
     * are current, without moving anything. The rest are dropped and refetched if scrolled to. If
     * the library's size changed meanwhile, the grid starts over. [prefetch] is how far around the
     * position to fetch; a visible message just retries the load. With [restartOnResize] false the
     * grid keeps every page (none is refetched at shifted offsets): posters outside the window keep
     * their marks, and a size change waits, until the next visit.
     */
    fun refresh(position: Int, prefetch: Int, messageShown: Boolean, restartOnResize: Boolean = true) {
        if (messageShown) return reload()
        if (loading?.isActive == true || paged.total == 0) return
        val gen = paged.generation
        val first = (position - prefetch).coerceAtLeast(0) / paged.pageSize
        val last = (position + prefetch).coerceAtMost(paged.total - 1) / paged.pageSize
        if (restartOnResize) paged.keepOnly((first..last).toSet())
        refreshing?.cancel()
        refreshing = scope.launch {
            for (page in first..last) {
                launch {
                    try {
                        val result = session.libraryPage(types, parentId, query, page * paged.pageSize, paged.pageSize)
                        if (gen != paged.generation) return@launch
                        if (result.total != paged.total) return@launch if (restartOnResize) reload() else Unit
                        if (paged.put(gen, page, result.items, result.total)) host.pageRefreshed(page)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // The posters on screen stay as they were
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "LibraryScreen"
    }
}

package cloud.rawkoon.tv.rawkoon

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The shelves as a screen reads them: Rawkoon's answer, saved for the next time, or the last one
 * saved when Rawkoon can't be reached. A server without the route answers "no shelves", which is
 * not a failure and never falls back to an older list.
 */
class ShelfSource(private val api: RawkoonDiscover, private val cache: ShelfCache) {
    suspend fun shelves(): List<ShelfSummary> = try {
        api.shelves()?.also { saved -> withContext(Dispatchers.IO) { cache.saveList(saved) } } ?: emptyList()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RawkoonException.Unauthorized) {
        throw e
    } catch (e: Exception) {
        withContext(Dispatchers.IO) { cache.list() }
    }

    /** Null when the shelf is gone (or the server never had shelves); the saved copy covers a failed read. */
    suspend fun shelf(id: Int): ShelfDetail? = try {
        api.shelf(id)?.also { detail -> withContext(Dispatchers.IO) { cache.saveDetail(detail) } }
    } catch (e: CancellationException) {
        throw e
    } catch (e: RawkoonException.Unauthorized) {
        throw e
    } catch (e: Exception) {
        withContext(Dispatchers.IO) { cache.detail(id) }
    }
}

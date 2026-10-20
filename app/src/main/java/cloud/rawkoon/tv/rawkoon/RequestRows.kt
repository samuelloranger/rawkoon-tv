package cloud.rawkoon.tv.rawkoon

import cloud.rawkoon.tv.jellyfin.HomeRow
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.jellyfin.TmdbRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

/** The "My requests" row: the account's own requests, with their state. */
object RequestRows {
    private const val LIVE_STATUS_PARALLELISM = 4
    private const val RECENT_DAYS = 14L

    /** A request with where it sorts: its [group], then newest [at] first. */
    private data class Ranked(val group: Int, val at: Instant, val request: RequestSummary)

    suspend fun row(discover: RawkoonDiscover, all: List<RequestSummary>, userId: String, text: RawkoonText, now: Instant): HomeRow? {
        val mine = withContext(Dispatchers.Default) { requests(all, userId, now) }
        if (mine.isEmpty()) return null
        val gate = Semaphore(LIVE_STATUS_PARALLELISM)
        val items = coroutineScope {
            mine.map { request ->
                async {
                    // Only an approved request is worth a live look at the library
                    val live = if (request.status == "approved") gate.withPermit { liveStatus(discover, request) } else null
                    item(request, TitleState.ofRequest(request, live), text)
                }
            }.awaitAll()
        }
        return HomeRow(RawkoonHome.REQUESTS_ID, text.requestsTitle(), items, landscape = false)
    }

    private suspend fun liveStatus(discover: RawkoonDiscover, request: RequestSummary): TitleStatus? = try {
        request.tmdbId?.let { discover.status(request.isMovie, it) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /**
     * The account's own movie and show requests: active first, then recently available, then
     * recently denied; newest first within each, at most [RawkoonHome.MAX_ITEMS].
     */
    fun requests(all: List<RequestSummary>, userId: String, now: Instant): List<RequestSummary> {
        val cutoff = now.minus(RECENT_DAYS, ChronoUnit.DAYS)
        fun parse(text: String?) = text?.let { runCatching { Instant.parse(it) }.getOrNull() }
        fun settled(r: RequestSummary) = parse(r.decidedAt) ?: parse(r.createdAt) ?: Instant.EPOCH
        fun group(r: RequestSummary): Int? = when (r.status) {
            "pending", "approved" -> 0
            "available" -> 1.takeIf { !settled(r).isBefore(cutoff) }
            "denied" -> 2.takeIf { !settled(r).isBefore(cutoff) }
            else -> null
        }
        return all
            .filter { it.requestedBy == userId && (it.type == "movie" || it.type == "show") && it.tmdbId != null }
            .mapNotNull { r -> group(r)?.let { g -> Ranked(g, if (g == 0) parse(r.createdAt) ?: Instant.EPOCH else settled(r), r) } }
            .sortedWith(compareBy<Ranked> { it.group }.thenByDescending { it.at })
            .map { it.request }
            .take(RawkoonHome.MAX_ITEMS)
    }

    fun item(request: RequestSummary, state: TitleState, text: RawkoonText): MediaItem = MediaItem.tmdb(
        id = "request-${request.id}",
        isMovie = request.isMovie,
        title = request.title,
        // Only requests with a TMDB id are listed
        tmdb = TmdbRef(
            id = checkNotNull(request.tmdbId),
            inLibrary = state == TitleState.Available,
            badge = text.badge(state),
            subtitle = request.year?.toString(),
        ),
        year = request.year,
        posterUrl = request.posterUrl,
    )
}

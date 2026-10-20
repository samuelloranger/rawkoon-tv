package cloud.rawkoon.tv.rawkoon

import cloud.rawkoon.tv.jellyfin.HomeRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/**
 * The two Rawkoon rows of the home page: "Coming soon" (what releases or airs next, from the
 * dashboard) and "My requests" (the account's own, with their state). Either is left out when
 * empty or when its call fails, so a Rawkoon hiccup never costs the Jellyfin rows.
 */
object RawkoonHome {
    const val UPCOMING_ID = "rawkoon:upcoming"
    const val REQUESTS_ID = "rawkoon:requests"
    const val MAX_ITEMS = 20

    /** Fetches both rows at once; a row that fails is just missing, but a refused session is thrown for the caller to decide. */
    suspend fun load(
        discover: RawkoonDiscover,
        /** The account's id, or null when Rawkoon has none to give (then there is no way to tell its requests apart). */
        userId: suspend () -> String?,
        text: RawkoonText,
        today: LocalDate,
        now: Instant,
        locale: Locale,
    ): List<HomeRow> = coroutineScope {
        val upcoming = async { optional { upcomingRow(discover, text, today, locale) } }
        val requests = async { optional { userId()?.let { RequestRows.row(discover, discover.requests(), it, text, now) } } }
        listOfNotNull(upcoming.await(), requests.await())
    }

    private suspend fun upcomingRow(discover: RawkoonDiscover, text: RawkoonText, today: LocalDate, locale: Locale): HomeRow? {
        val entries = discover.upcoming()
        return withContext(Dispatchers.Default) { UpcomingRows.row(entries, text, today, locale) }
    }

    private suspend fun <T> optional(block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RawkoonException.Unauthorized) {
        throw e
    } catch (e: Exception) {
        null
    }

    fun isRawkoonRow(row: HomeRow) = row.id == UPCOMING_ID || row.id == REQUESTS_ID

    /** Rows kept from before (cached or on screen) with their dates worded for [today] again. */
    fun refreshed(rows: List<HomeRow>, text: RawkoonText, today: LocalDate, locale: Locale): List<HomeRow> =
        rows.mapNotNull { if (it.id == UPCOMING_ID) UpcomingRows.refreshed(it, text, today, locale) else it }

    /** The Rawkoon rows to show once a refresh ended as [outcome]: new ones, the [previous] ones kept, or none. */
    fun rowsFor(outcome: RawkoonOutcome, previous: List<HomeRow>): List<HomeRow> = when (outcome) {
        is RawkoonOutcome.Loaded -> outcome.rows
        RawkoonOutcome.Unavailable -> previous.filter(::isRawkoonRow)
        RawkoonOutcome.Refused, RawkoonOutcome.NoSession -> emptyList()
    }

    /** The Rawkoon rows go right after Continue watching and Next up, or first when those are empty. */
    fun merge(jellyfin: List<HomeRow>, rawkoon: List<HomeRow>): List<HomeRow> {
        if (rawkoon.isEmpty()) return jellyfin
        val at = jellyfin.indexOfLast { it.id == "resume" || it.id == "nextup" } + 1
        return jellyfin.take(at) + rawkoon + jellyfin.drop(at)
    }
}

/** How the Rawkoon side of a home refresh ended. */
sealed interface RawkoonOutcome {
    data class Loaded(val rows: List<HomeRow>) : RawkoonOutcome

    /** Failed or too slow: the rows shown before are still the best there is. */
    data object Unavailable : RawkoonOutcome

    /** Rawkoon refused the token: the old rows belong to a revoked session. Discover asks to link again. */
    data object Refused : RawkoonOutcome

    data object NoSession : RawkoonOutcome
}

package cloud.rawkoon.tv.rawkoon

import cloud.rawkoon.tv.jellyfin.Badge
import cloud.rawkoon.tv.jellyfin.HomeRow
import cloud.rawkoon.tv.jellyfin.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A release or an episode airing on [date], ready to be a card. */
data class UpcomingCard(
    val entryId: String,
    val tmdbId: Int,
    val isMovie: Boolean,
    val title: String,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val date: LocalDate,
    val inLibrary: Boolean,
    val season: Int?,
    val episode: Int?,
    val episodeEnd: Int?,
    val episodeCount: Int,
)

/**
 * The two Rawkoon rows of the home page: "Coming soon" (what releases or airs next, from the
 * dashboard) and "My requests" (the account's own, with their state). Either is left out when
 * empty or when its call fails, so a Rawkoon hiccup never costs the Jellyfin rows.
 */
object RawkoonHome {
    const val UPCOMING_ID = "rawkoon:upcoming"
    const val REQUESTS_ID = "rawkoon:requests"
    const val MAX_ITEMS = 20
    private const val LIVE_STATUS_PARALLELISM = 4
    private const val WEEK_DAYS = 6L
    private const val RECENT_DAYS = 14L
    private val ID_PATTERN = Regex("^(?:movie|tv)-(\\d+)")
    private val URL_PATTERN = Regex("/(?:movie|tv)/(\\d+)")

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
        val upcoming = async { optional { upcomingRow(discover.upcoming(), text, today, locale) } }
        val requests = async { optional { userId()?.let { requestsRow(discover, discover.requests(), it, text, now) } } }
        listOfNotNull(upcoming.await(), requests.await())
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

    fun upcomingRow(entries: List<UpcomingEntry>, text: RawkoonText, today: LocalDate, locale: Locale): HomeRow? {
        val items = upcoming(entries, today).map { upcomingItem(it, text, today, locale) }
        return items.takeIf { it.isNotEmpty() }?.let { HomeRow(UPCOMING_ID, text.upcomingTitle(), it, landscape = false) }
    }

    private suspend fun requestsRow(discover: RawkoonDiscover, all: List<RequestSummary>, userId: String, text: RawkoonText, now: Instant): HomeRow? {
        val mine = requests(all, userId, now)
        if (mine.isEmpty()) return null
        val gate = Semaphore(LIVE_STATUS_PARALLELISM)
        val items = coroutineScope {
            mine.map { request ->
                async {
                    // Only an approved request is worth a live look at the library
                    val live = if (request.status == "approved") gate.withPermit { liveStatus(discover, request) } else null
                    requestItem(request, TitleState.ofRequest(request, live), text)
                }
            }.awaitAll()
        }
        return HomeRow(REQUESTS_ID, text.requestsTitle(), items, landscape = false)
    }

    private suspend fun liveStatus(discover: RawkoonDiscover, request: RequestSummary): TitleStatus? = try {
        request.tmdbId?.let { discover.status(request.isMovie, it) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** The TMDB id of an upcoming entry, from its id or its TMDB link; entries known only to a downloader have none. */
    fun tmdbIdOf(entry: UpcomingEntry): Int? =
        (ID_PATTERN.find(entry.id) ?: entry.tmdbUrl?.let { URL_PATTERN.find(it) })?.groupValues?.get(1)?.toIntOrNull()

    /** Today or later, soonest first, at most [MAX_ITEMS]. */
    fun upcoming(entries: List<UpcomingEntry>, today: LocalDate): List<UpcomingCard> = entries
        // The server may send the same entry more than once
        .distinctBy { it.id.ifBlank { null } ?: Any() }
        .mapNotNull { entry ->
            val date = entry.releaseDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val tmdbId = tmdbIdOf(entry)
            if (date == null || date.isBefore(today) || tmdbId == null || entry.title.isBlank()) return@mapNotNull null
            UpcomingCard(
                entryId = entry.id,
                tmdbId = tmdbId,
                isMovie = entry.isMovie,
                title = entry.title,
                overview = entry.overview,
                posterUrl = entry.posterUrl,
                backdropUrl = entry.backdropUrl,
                date = date,
                inLibrary = entry.libraryId != null,
                season = entry.season.takeIf { !entry.isMovie },
                episode = entry.episode.takeIf { !entry.isMovie },
                episodeEnd = entry.episodeEnd.takeIf { !entry.isMovie },
                episodeCount = (entry.episodeCount ?: 1).coerceAtLeast(1),
            )
        }
        .sortedWith(compareBy<UpcomingCard> { it.date }.thenBy { it.title })
        .take(MAX_ITEMS)

    /** "S1 · E7", "S1 · E3–4" when a range airs, "2 episodes" when there are several with no numbers; null for a movie. */
    fun episodePill(card: UpcomingCard, text: RawkoonText): String? {
        if (card.isMovie) return null
        val season = card.season
        val episode = card.episode
        return when {
            season != null && episode != null -> {
                val end = card.episodeEnd?.takeIf { it != episode }
                if (end != null) "S$season · E$episode–$end" else "S$season · E$episode"
            }
            card.episodeCount > 1 -> text.episodes(card.episodeCount)
            else -> null
        }
    }

    /** Today, tomorrow, the weekday for the next six days, otherwise a short date (with the year when it is not this one). */
    fun dateText(date: LocalDate, today: LocalDate, locale: Locale, text: RawkoonText): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> text.today()
            days == 1L -> text.tomorrow()
            days in 2..WEEK_DAYS -> weekday(date.dayOfWeek, locale)
            else -> shortDate(date, today, locale)
        }
    }

    private fun weekday(day: DayOfWeek, locale: Locale) = day.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }

    private fun shortDate(date: LocalDate, today: LocalDate, locale: Locale): String {
        val dayFirst = locale.language != "en"
        val withYear = date.year != today.year
        val pattern = when {
            dayFirst && withYear -> "d MMM yyyy"
            dayFirst -> "d MMM"
            withYear -> "MMM d, yyyy"
            else -> "MMM d"
        }
        return DateTimeFormatter.ofPattern(pattern, locale).format(date)
    }

    fun upcomingItem(card: UpcomingCard, text: RawkoonText, today: LocalDate, locale: Locale): MediaItem {
        val pill = episodePill(card, text)
        return MediaItem(
            id = "upcoming-${card.entryId.ifBlank { card.tmdbId.toString() }}",
            kind = if (card.isMovie) MediaItem.Kind.MOVIE else MediaItem.Kind.SERIES,
            title = card.title,
            seriesName = null,
            episodeLabel = null,
            overview = card.overview.orEmpty(),
            year = null,
            runtimeMinutes = null,
            officialRating = null,
            communityRating = null,
            posterUrl = tmdbImage(card.posterUrl),
            thumbUrl = null,
            backdropUrl = tmdbImage(card.backdropUrl),
            logoUrl = null,
            progress = null,
            played = false,
            tmdbId = card.tmdbId,
            inLibrary = card.inLibrary,
            subtitle = dateText(card.date, today, locale, text),
            badge = when {
                pill != null -> Badge(pill, Badge.Tone.ACTIVE)
                card.inLibrary -> Badge(text.inLibrary(), Badge.Tone.READY)
                else -> null
            },
        )
    }

    /**
     * The account's own movie and show requests: active first, then recently available, then
     * recently denied; newest first within each, at most [MAX_ITEMS].
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
            .mapNotNull { r -> group(r)?.let { g -> Triple(g, if (g == 0) parse(r.createdAt) ?: Instant.EPOCH else settled(r), r) } }
            .sortedWith(compareBy<Triple<Int, Instant, RequestSummary>> { it.first }.thenByDescending { it.second })
            .map { it.third }
            .take(MAX_ITEMS)
    }

    fun requestItem(request: RequestSummary, state: TitleState, text: RawkoonText): MediaItem = MediaItem(
        id = "request-${request.id}",
        kind = if (request.isMovie) MediaItem.Kind.MOVIE else MediaItem.Kind.SERIES,
        title = request.title,
        seriesName = null,
        episodeLabel = null,
        overview = "",
        year = request.year,
        runtimeMinutes = null,
        officialRating = null,
        communityRating = null,
        posterUrl = tmdbImage(request.posterUrl),
        thumbUrl = null,
        backdropUrl = null,
        logoUrl = null,
        progress = null,
        played = false,
        tmdbId = request.tmdbId,
        inLibrary = state == TitleState.Available,
        subtitle = request.year?.toString(),
        badge = text.badge(state),
    )

    fun isRawkoonRow(row: HomeRow) = row.id == UPCOMING_ID || row.id == REQUESTS_ID

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

package cloud.rawkoon.tv.rawkoon

import cloud.rawkoon.tv.jellyfin.Badge
import cloud.rawkoon.tv.jellyfin.HomeRow
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.jellyfin.TmdbRef
import java.time.DayOfWeek
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

/** The "Coming soon" row: what the dashboard says releases or airs next. */
object UpcomingRows {
    private const val WEEK_DAYS = 6L
    private val ID_PATTERN = Regex("^(?:movie|tv)-(\\d+)")
    private val URL_PATTERN = Regex("/(?:movie|tv)/(\\d+)")

    fun row(entries: List<UpcomingEntry>, text: RawkoonText, today: LocalDate, locale: Locale): HomeRow? {
        val items = upcoming(entries, today).map { item(it, text, today, locale) }
        return items.takeIf { it.isNotEmpty() }?.let { HomeRow(RawkoonHome.UPCOMING_ID, text.upcomingTitle(), it, landscape = false) }
    }

    /** The TMDB id of an upcoming entry, from its id or its TMDB link; entries known only to a downloader have none. */
    fun tmdbIdOf(entry: UpcomingEntry): Int? =
        (ID_PATTERN.find(entry.id) ?: entry.tmdbUrl?.let { URL_PATTERN.find(it) })?.groupValues?.get(1)?.toIntOrNull()

    /** Today or later, soonest first, at most [RawkoonHome.MAX_ITEMS]. */
    fun upcoming(entries: List<UpcomingEntry>, today: LocalDate): List<UpcomingCard> = entries
        .withoutRepeats()
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
        .take(RawkoonHome.MAX_ITEMS)

    /** The server may send the same entry more than once; an entry without an id is never taken for a repeat. */
    private fun List<UpcomingEntry>.withoutRepeats(): List<UpcomingEntry> {
        val seen = HashSet<String>()
        return filter { it.id.isBlank() || seen.add(it.id) }
    }

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

    fun item(card: UpcomingCard, text: RawkoonText, today: LocalDate, locale: Locale): MediaItem {
        val pill = episodePill(card, text)
        return MediaItem.tmdb(
            id = "upcoming-${card.entryId.ifBlank { card.tmdbId.toString() }}",
            isMovie = card.isMovie,
            title = card.title,
            tmdb = TmdbRef(
                id = card.tmdbId,
                inLibrary = card.inLibrary,
                badge = when {
                    pill != null -> Badge(pill, Badge.Tone.ACTIVE)
                    card.inLibrary -> Badge(text.inLibrary(), Badge.Tone.READY)
                    else -> null
                },
                subtitle = dateText(card.date, today, locale, text),
                airDate = card.date,
            ),
            overview = card.overview.orEmpty(),
            posterUrl = card.posterUrl,
            backdropUrl = card.backdropUrl,
        )
    }

    /**
     * A cached row words its dates when it was built, which is stale by the next launch: say them
     * again, and drop the cards whose date has passed.
     */
    fun refreshed(row: HomeRow, text: RawkoonText, today: LocalDate, locale: Locale): HomeRow? {
        val items = row.items.mapNotNull { item ->
            val ref = item.tmdb ?: return@mapNotNull item
            val date = ref.airDate ?: return@mapNotNull item
            if (date.isBefore(today)) null else item.copy(tmdb = ref.copy(subtitle = dateText(date, today, locale, text)))
        }
        return if (items.isEmpty()) null else row.copy(items = items)
    }
}

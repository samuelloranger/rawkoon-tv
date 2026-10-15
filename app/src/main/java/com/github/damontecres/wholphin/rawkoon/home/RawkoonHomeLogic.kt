package com.github.damontecres.wholphin.rawkoon.home

import com.github.damontecres.wholphin.rawkoon.TitleStatus
import com.github.damontecres.wholphin.rawkoon.seerr.RkRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One title of `GET /api/dashboard/upcoming` */
@Serializable
data class RkUpcomingItem(
    val id: String = "",
    val title: String = "",
    @SerialName("media_type") val mediaType: String = "",
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("backdrop_url") val backdropUrl: String? = null,
    val overview: String? = null,
    @SerialName("tmdb_url") val tmdbUrl: String? = null,
    @SerialName("library_id") val libraryId: Int? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    // Newer servers only: the last episode of a range, and how many episodes air that day
    @SerialName("episode_number_end") val episodeNumberEnd: Int? = null,
    @SerialName("episode_count") val episodeCount: Int? = null,
)

@Serializable
data class RkUpcoming(
    val enabled: Boolean = false,
    val items: List<RkUpcomingItem> = emptyList(),
)

/** A title for the Coming soon row */
data class UpcomingCard(
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
) {
    val pill: EpisodePill? get() = RawkoonHomeLogic.episodePill(this)
}

/** What the pill on a poster says about the episodes */
sealed interface EpisodePill {
    /** "S1 · E7" or "S1 · E3–4" */
    data class Numbers(
        val text: String,
    ) : EpisodePill

    /** Several episodes with no numbers: "2 episodes" */
    data class Count(
        val count: Int,
    ) : EpisodePill
}

/** How a date reads on a card; the words "Today" and "Tomorrow" are strings, so only their kind is decided here */
sealed interface DateLabel {
    data object Today : DateLabel

    data object Tomorrow : DateLabel

    data class Text(
        val text: String,
    ) : DateLabel
}

object RawkoonHomeLogic {
    const val MAX_ITEMS = 20
    private const val WEEK_DAYS = 6L
    private const val RECENT_DAYS = 14L
    private val ID_PATTERN = Regex("^(movie|tv)-(\\d+)")
    private val URL_PATTERN = Regex("/(movie|tv)/(\\d+)")

    /** The TMDB id of an upcoming item, from its id or its TMDB link; items known only to a downloader have none */
    fun tmdbIdOf(item: RkUpcomingItem): Int? =
        (ID_PATTERN.find(item.id) ?: item.tmdbUrl?.let { URL_PATTERN.find(it) })
            ?.groupValues
            ?.get(2)
            ?.toIntOrNull()

    /** Today or later, soonest first, at most [MAX_ITEMS] */
    fun upcoming(
        items: List<RkUpcomingItem>,
        today: LocalDate,
    ): List<UpcomingCard> =
        items
            // The server may send the same item more than once
            .distinctBy { it.id.ifBlank { null } ?: Any() }
            .mapNotNull { item ->
                val date = item.releaseDate?.take(DATE_LENGTH)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val tmdbId = tmdbIdOf(item)
                if (date == null || date.isBefore(today) || tmdbId == null || item.title.isBlank()) return@mapNotNull null
                if (item.mediaType != "movie" && item.mediaType != "tv") return@mapNotNull null
                UpcomingCard(
                    tmdbId = tmdbId,
                    isMovie = item.mediaType == "movie",
                    title = item.title,
                    overview = item.overview,
                    posterUrl = item.posterUrl,
                    backdropUrl = item.backdropUrl,
                    date = date,
                    inLibrary = item.libraryId != null,
                    season = item.seasonNumber.takeIf { item.mediaType == "tv" },
                    episode = item.episodeNumber.takeIf { item.mediaType == "tv" },
                    episodeEnd = item.episodeNumberEnd.takeIf { item.mediaType == "tv" },
                    episodeCount = (item.episodeCount ?: 1).coerceAtLeast(1),
                )
            }.sortedWith(compareBy<UpcomingCard> { it.date }.thenBy { it.title })
            .take(MAX_ITEMS)

    private const val DATE_LENGTH = 10

    /** Numbers when known (a range when the last episode differs), else the count when there are several, else nothing */
    fun episodePill(card: UpcomingCard): EpisodePill? {
        if (card.isMovie) return null
        val season = card.season
        val episode = card.episode
        return when {
            season != null && episode != null -> {
                val end = card.episodeEnd?.takeIf { it != episode }
                EpisodePill.Numbers(if (end != null) "S$season · E$episode–$end" else "S$season · E$episode")
            }

            card.episodeCount > 1 -> {
                EpisodePill.Count(card.episodeCount)
            }

            else -> {
                null
            }
        }
    }

    /** Today, tomorrow, the weekday for the next six days, otherwise a short date (with the year when it is not this one) */
    fun dateLabel(
        date: LocalDate,
        today: LocalDate,
        locale: Locale,
    ): DateLabel {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> DateLabel.Today
            days == 1L -> DateLabel.Tomorrow
            days in 2..WEEK_DAYS -> DateLabel.Text(weekday(date.dayOfWeek, locale))
            else -> DateLabel.Text(shortDate(date, today, locale))
        }
    }

    private fun weekday(
        day: DayOfWeek,
        locale: Locale,
    ): String = day.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }

    private fun shortDate(
        date: LocalDate,
        today: LocalDate,
        locale: Locale,
    ): String {
        val dayFirst = locale.language != "en"
        val withYear = date.year != today.year
        val pattern =
            when {
                dayFirst && withYear -> "d MMM yyyy"
                dayFirst -> "d MMM"
                withYear -> "MMM d, yyyy"
                else -> "MMM d"
            }
        return DateTimeFormatter.ofPattern(pattern, locale).format(date)
    }

    /** The user's own movie and show requests: active first, then recent available, then recent denied; newest first, at most [MAX_ITEMS] */
    fun requests(
        all: List<RkRequest>,
        userId: String,
        now: Instant,
    ): List<RkRequest> {
        val cutoff = now.minus(RECENT_DAYS, ChronoUnit.DAYS)

        fun time(request: RkRequest): Instant = parse(request.decidedAt) ?: parse(request.createdAt) ?: Instant.EPOCH

        fun group(request: RkRequest): Int? =
            when (request.status) {
                "pending", "approved" -> 0
                "available" -> 1.takeIf { !time(request).isBefore(cutoff) }
                "denied" -> 2.takeIf { !time(request).isBefore(cutoff) }
                else -> null
            }
        return all
            .filter { it.requestedBy?.id == userId && (it.type == "movie" || it.type == "show") && it.tmdbId != null }
            .mapNotNull { request -> group(request)?.let { Triple(it, if (it == 0) parse(request.createdAt) ?: Instant.EPOCH else time(request), request) } }
            .sortedWith(compareBy<Triple<Int, Instant, RkRequest>> { it.first }.thenByDescending { it.second })
            .map { it.third }
            .take(MAX_ITEMS)
    }

    private fun parse(text: String?): Instant? = text?.let { runCatching { Instant.parse(it) }.getOrNull() }

    /** Only approved requests are worth a live look at the library */
    fun needsLiveStatus(request: RkRequest): Boolean = request.status == "approved"

    /** What the status chip on a request says */
    fun chip(
        request: RkRequest,
        live: TitleStatus?,
    ): RequestChip =
        when (request.status) {
            "pending" -> RequestChip.Pending
            "denied" -> RequestChip.Denied
            "available" -> RequestChip.Ready
            else -> approvedChip(live)
        }

    private fun approvedChip(live: TitleStatus?): RequestChip {
        val library = live?.library ?: return RequestChip.Approved
        val total = library.episodeCount ?: 0
        val done = library.downloadedEpisodeCount ?: 0
        return when {
            library.status == "downloaded" || library.status == "upgrading" -> RequestChip.Ready
            library.status == "downloading" -> RequestChip.Downloading(if (total > 0) (100 * done / total).coerceIn(0, 99) else null)
            done > 0 && total in 1..done -> RequestChip.Ready
            else -> RequestChip.Approved
        }
    }
}

sealed interface RequestChip {
    data object Pending : RequestChip

    data object Approved : RequestChip

    /** [percent] is null when Rawkoon has no episode counts, as for a movie */
    data class Downloading(
        val percent: Int?,
    ) : RequestChip

    data object Ready : RequestChip

    data object Denied : RequestChip
}

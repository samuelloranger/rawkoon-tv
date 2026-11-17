package cloud.rawkoon.tv.jellyfin

import java.time.LocalDate
import java.time.Period

/** A cast or crew member. */
data class Person(val id: String, val name: String, val role: String?, val imageUrl: String?)

/** What Jellyfin keeps about a person, from TMDB: the biography, birth and death, birthplace and photo. */
data class PersonInfo(
    val id: String,
    val name: String,
    val bio: String,
    val born: LocalDate?,
    val died: LocalDate?,
    val birthplace: String?,
    val imageUrl: String?,
) {
    /** The age on [today], or at death; null without a birth date. */
    fun age(today: LocalDate): Int? = born?.let { Period.between(it, died ?: today).years }
}

/** What a details page shows beyond the card: genres, cast, the studio, the show's span. */
data class Details(
    val item: MediaItem,
    val genres: List<String>,
    val studio: String?,
    val endYear: Int?,
    val continuing: Boolean,
    val seasonCount: Int?,
    val episodeCount: Int?,
    val people: List<Person>,
    /** The title's TMDB id in Jellyfin, when it has one: what ties the page to Rawkoon's watchlist. */
    val tmdbId: Int? = null,
)

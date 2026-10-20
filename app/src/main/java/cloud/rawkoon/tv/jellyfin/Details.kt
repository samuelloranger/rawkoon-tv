package cloud.rawkoon.tv.jellyfin

/** A cast or crew member. */
data class Person(val id: String, val name: String, val role: String?, val imageUrl: String?)

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
)

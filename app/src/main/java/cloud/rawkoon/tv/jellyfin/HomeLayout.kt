package cloud.rawkoon.tv.jellyfin

import cloud.rawkoon.tv.data.ContinueOpen
import java.time.LocalDateTime

/** How a home load asks Jellyfin for its rows, from the settings. The defaults are what home always did. */
data class HomeFetch(
    /** Items asked for in each row. */
    val limit: Int = DEFAULT_LIMIT,
    /** Next up leaves out shows not watched for this many days; 0 is no limit. */
    val nextUpDays: Int = 0,
    /** Whether Next up also offers episodes of shows already finished. */
    val rewatching: Boolean = false,
) {
    /** The oldest last-watched date Next up still counts, or null for no limit. */
    fun nextUpCutoff(now: LocalDateTime): LocalDateTime? = if (nextUpDays > 0) now.minusDays(nextUpDays.toLong()) else null

    /** The request's rewatching flag; left unset (the server's own default) when off. */
    fun nextUpRewatching(): Boolean? = if (rewatching) true else null

    companion object {
        const val DEFAULT_LIMIT = 20
    }
}

/**
 * [ids] in the order the user arranged them: those in [order] first, in that order (ids that are
 * no longer there skipped), then the ones it doesn't know, such as a new library, in their natural order.
 */
fun orderedIds(ids: List<String>, order: List<String>): List<String> {
    val present = ids.toSet()
    val known = order.filter { it in present }.distinct()
    val knownSet = known.toSet()
    return known + ids.filter { it !in knownSet }
}

/** [rows] arranged to the saved [order], without the [hidden] ones. Nothing saved leaves them as they are. */
fun arrangeRows(rows: List<HomeRow>, order: List<String>, hidden: Set<String>): List<HomeRow> {
    val byId = rows.associateBy { it.id }
    return orderedIds(rows.map { it.id }, order).filter { it !in hidden }.mapNotNull { byId[it] }
}

/** [ids] with [id] moved [by] places (negative is up), stopping at the ends. */
fun movedBy(ids: List<String>, id: String, by: Int): List<String> {
    val from = ids.indexOf(id)
    if (from < 0) return ids
    val to = (from + by).coerceIn(0, ids.lastIndex)
    return ids.toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * Whether OK on a card plays it at once rather than opening its page: a movie or episode in
 * Continue watching, or an episode in Next up, when the setting asks for it. Anything else keeps
 * opening its page.
 */
fun playsRightAway(mode: ContinueOpen, rowId: String, item: MediaItem): Boolean {
    if (mode != ContinueOpen.PLAY || item.tmdb != null) return false
    return when (rowId) {
        "resume" -> item.kind == MediaItem.Kind.MOVIE || item.kind == MediaItem.Kind.EPISODE
        "nextup" -> item.kind == MediaItem.Kind.EPISODE
        else -> false
    }
}

package cloud.rawkoon.tv.jellyfin

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest

/**
 * The kinds of result the search and favorites pages show, in the order their rows appear.
 * Episodes are the only landscape row: their stills carry a caption, the rest are posters.
 */
enum class FindGroup(val kind: BaseItemKind, val landscape: Boolean) {
    MOVIES(BaseItemKind.MOVIE, landscape = false),
    SERIES(BaseItemKind.SERIES, landscape = false),
    EPISODES(BaseItemKind.EPISODE, landscape = true),
    COLLECTIONS(BaseItemKind.BOX_SET, landscape = false),
}

/** The pure rules behind search and favorites, apart from the network. */
object Find {
    /** Jellyfin matches on substrings, so a single letter returns half the library. */
    const val MIN_TERM = 2

    /** The term to search for, or null when what was typed is too short to be worth a request. */
    fun term(typed: CharSequence): String? = typed.trim().toString().takeIf { it.length >= MIN_TERM }

    /** The groups that found something, in [FindGroup] order whatever order [found] holds them in. */
    fun nonEmpty(found: Map<FindGroup, List<MediaItem>>): Map<FindGroup, List<MediaItem>> =
        FindGroup.entries.mapNotNull { group -> found[group]?.takeIf { it.isNotEmpty() }?.let { group to it } }.toMap()
}

/** Library search by title, one request per kind so a flood of episodes never crowds out the films. */
suspend fun JellyfinSession.search(term: String): Map<FindGroup, List<MediaItem>> =
    Find.nonEmpty(fetch(term = term, favorite = false))

/** The profile's favorite films, shows, episodes and collections, each sorted by name. */
suspend fun JellyfinSession.favorites(): Map<FindGroup, List<MediaItem>> =
    Find.nonEmpty(fetch(term = null, favorite = true))

/**
 * The films and shows a person is credited on, newest first: by Jellyfin person [personId] when
 * known, else by [name] (a cast member from TMDB has no Jellyfin id).
 */
suspend fun JellyfinSession.personTitles(personId: String?, name: String): Map<FindGroup, List<MediaItem>> =
    Find.nonEmpty(fetch(term = null, favorite = false, person = Credited(personId, name), groups = listOf(FindGroup.MOVIES, FindGroup.SERIES)))

private class Credited(val id: String?, val name: String)

private suspend fun JellyfinSession.fetch(
    term: String?,
    favorite: Boolean,
    person: Credited? = null,
    groups: List<FindGroup> = FindGroup.entries,
): Map<FindGroup, List<MediaItem>> = coroutineScope {
    groups.map { group ->
        group to async {
            api.libraryApi.getItems(
                GetItemsRequest(
                    userId = userId,
                    searchTerm = term,
                    includeItemTypes = listOf(group.kind),
                    recursive = true,
                    personIds = person?.id?.let { listOf(JellyfinSession.parseId(it)) },
                    person = person?.name?.takeIf { person.id == null },
                    // Search shows top hits; favorites and a person's titles are pages to browse, so they show them all
                    limit = if (favorite || person != null) null else RESULT_LIMIT,
                    fields = listOf(ItemFields.OVERVIEW, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
                    filters = if (favorite) listOf(ItemFilter.IS_FAVORITE) else null,
                    // A search keeps Jellyfin's relevance order
                    sortBy = when {
                        person != null -> listOf(ItemSortBy.PREMIERE_DATE, ItemSortBy.SORT_NAME)
                        favorite -> listOf(ItemSortBy.SORT_NAME)
                        else -> null
                    },
                    sortOrder = if (person != null) listOf(SortOrder.DESCENDING, SortOrder.ASCENDING) else null,
                    enableTotalRecordCount = false,
                ),
            ).content.items.map { MediaItem.from(it, images) }
        }
    }.associate { (group, items) -> group to items.await() }
}

private const val RESULT_LIMIT = 24

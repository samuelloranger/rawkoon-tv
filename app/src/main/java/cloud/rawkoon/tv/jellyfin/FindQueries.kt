package cloud.rawkoon.tv.jellyfin

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
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

private suspend fun JellyfinSession.fetch(term: String?, favorite: Boolean): Map<FindGroup, List<MediaItem>> = coroutineScope {
    FindGroup.entries.map { group ->
        group to async {
            api.itemsApi.getItems(
                GetItemsRequest(
                    userId = userId,
                    searchTerm = term,
                    includeItemTypes = listOf(group.kind),
                    recursive = true,
                    // Search shows top hits; favorites is a page to browse, so it shows them all
                    limit = if (favorite) null else RESULT_LIMIT,
                    fields = listOf(ItemFields.OVERVIEW, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
                    filters = if (favorite) listOf(ItemFilter.IS_FAVORITE) else null,
                    // A search keeps Jellyfin's relevance order
                    sortBy = if (favorite) listOf(ItemSortBy.SORT_NAME) else null,
                    enableTotalRecordCount = false,
                ),
            ).content.items.map { MediaItem.from(it, images) }
        }
    }.associate { (group, items) -> group to items.await() }
}

private const val RESULT_LIMIT = 24

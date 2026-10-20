package cloud.rawkoon.tv.jellyfin

import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

/** How the library grids can be ordered; [newestFirst] is the direction that reads naturally for it. */
enum class LibrarySort(val keys: List<ItemSortBy>, val newestFirst: Boolean) {
    // Bulk additions share a timestamp: the name keeps their order stable from one page to the next
    ADDED(listOf(ItemSortBy.DATE_CREATED, ItemSortBy.SORT_NAME), true),
    TITLE(listOf(ItemSortBy.SORT_NAME), false),
    YEAR(listOf(ItemSortBy.PRODUCTION_YEAR, ItemSortBy.SORT_NAME), true),
    RATING(listOf(ItemSortBy.COMMUNITY_RATING, ItemSortBy.SORT_NAME), true),
}

enum class LibraryFilter(val filters: List<ItemFilter>?) {
    ALL(null),
    UNWATCHED(listOf(ItemFilter.IS_UNPLAYED)),
    FAVORITES(listOf(ItemFilter.IS_FAVORITE)),
}

/** What a library grid shows: the order (newest or A first unless [reverse]) and the filter. */
data class LibraryQuery(val sort: LibrarySort = LibrarySort.ADDED, val reverse: Boolean = false, val filter: LibraryFilter = LibraryFilter.ALL) {
    val descending: Boolean get() = sort.newestFirst != reverse

    /** One direction per sort key: only the main key flips, ties on the title always read A to Z. */
    val sortOrder: List<SortOrder>
        get() = sort.keys.indices.map { if (it == 0 && descending) SortOrder.DESCENDING else SortOrder.ASCENDING }
}

/** One page of a library, with the number of titles in all of it. */
data class LibraryPage(val items: List<MediaItem>, val total: Int)

/**
 * A bound on the sort name, as the server filters it: an ordinal comparison on the sort name, which
 * Jellyfin stores lowercased with articles stripped, so bounds are lowercase too.
 */
data class NameBound(val lessThan: String? = null, val orGreater: String? = null)

/**
 * The titles that come before the first one starting with [letter] ('#' for anything that is not a
 * letter), or null when that is none (the index is 0). The grid is sorted by title, so the count of
 * those titles is the position of the letter: no need to page through the library to find it.
 */
fun letterBound(letter: Char, descending: Boolean): NameBound? = when {
    letter == '#' -> if (descending) NameBound(orGreater = "a") else null
    // Z ends the alphabet: reversed, its titles come first
    descending && letter == 'Z' -> null
    descending -> NameBound(orGreater = (letter + 1).lowercase())
    else -> NameBound(lessThan = letter.lowercase())
}

internal fun libraryRequest(
    userId: UUID,
    types: List<BaseItemKind>,
    parentId: UUID?,
    query: LibraryQuery,
    startIndex: Int,
    limit: Int,
    bound: NameBound? = null,
) = GetItemsRequest(
    userId = userId,
    parentId = parentId,
    includeItemTypes = types,
    // A collection's own titles are its direct children; the whole library is searched flat
    recursive = parentId == null,
    sortBy = query.sort.keys,
    sortOrder = query.sortOrder,
    filters = query.filter.filters,
    startIndex = startIndex,
    limit = limit,
    fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
    enableTotalRecordCount = true,
    nameLessThan = bound?.lessThan,
    nameStartsWithOrGreater = bound?.orGreater,
)

/** The [limit] titles of [types] (all libraries, or the collection [parentId]) from [startIndex]. */
suspend fun JellyfinSession.libraryPage(types: List<BaseItemKind>, parentId: String?, query: LibraryQuery, startIndex: Int, limit: Int): LibraryPage {
    val result = api.itemsApi.getItems(libraryRequest(userId, types, parentId?.let(JellyfinSession::parseId), query, startIndex, limit)).content
    return LibraryPage(result.items.map { MediaItem.from(it, images) }, result.totalRecordCount)
}

/** Where the first title starting with [letter] sits in a title-sorted grid; one request, whatever the size of the library. */
suspend fun JellyfinSession.libraryLetterIndex(types: List<BaseItemKind>, parentId: String?, query: LibraryQuery, letter: Char): Int {
    val bound = letterBound(letter, query.descending) ?: return 0
    // One item is enough: only the total is read
    val request = libraryRequest(userId, types, parentId?.let(JellyfinSession::parseId), query, 0, 1, bound)
    return api.itemsApi.getItems(request).content.totalRecordCount
}

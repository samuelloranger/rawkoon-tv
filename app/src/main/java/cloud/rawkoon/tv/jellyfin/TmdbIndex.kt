package cloud.rawkoon.tv.jellyfin

import cloud.rawkoon.tv.data.optIntOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.json.JSONArray
import org.json.JSONObject

/** How a shelf entry names a library title: its TMDB id and whether it is a show. */
data class TmdbKey(val tmdbId: Int, val isShow: Boolean)

/** One library title as the index read it: just enough to find its item later. */
data class IndexedTitle(val id: String, val tmdbId: Int?, val isShow: Boolean)

/** A page of the index read; [total] is the library's size when the server said. */
data class IndexPage(val titles: List<IndexedTitle>, val total: Int?)

/**
 * A profile's library titles by TMDB id, as item ids only. One paged read of Jellyfin's movies and
 * shows replaces a name search per shelf entry, which on a low-end TV made a shelf of forty titles
 * take minutes. Watched marks, progress and art change far more often than the library does, so
 * they are never stored here: the items are fetched by id each time a shelf is shown.
 */
class TmdbIndex(val ids: Map<TmdbKey, String>) {
    val size: Int get() = ids.size

    fun itemId(tmdbId: Int, isShow: Boolean): String? = ids[TmdbKey(tmdbId, isShow)]

    fun toJson(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put(
            "items",
            JSONArray().apply {
                ids.forEach { (key, id) -> put(JSONObject().put("tmdb", key.tmdbId).put("show", key.isShow).put("id", id)) }
            },
        )

    companion object {
        private const val VERSION = 2

        /** What a profile's last index was saved as; null when it is missing or from another version. */
        fun fromJson(text: String): TmdbIndex? = runCatching {
            val json = JSONObject(text)
            if (json.optInt("version") != VERSION) return null
            val array = json.optJSONArray("items") ?: return null
            val titles = (0 until array.length()).mapNotNull {
                val item = array.optJSONObject(it) ?: return@mapNotNull null
                val id = item.optString("id").takeIf(String::isNotEmpty) ?: return@mapNotNull null
                IndexedTitle(id, item.optIntOrNull("tmdb"), item.optBoolean("show"))
            }
            of(titles)
        }.getOrNull()

        /** A title without a TMDB id is left out: nothing on a shelf can point at it. */
        fun of(titles: List<IndexedTitle>): TmdbIndex = TmdbIndex(
            titles.mapNotNull { t -> t.tmdbId?.let { TmdbKey(it, t.isShow) to t.id } }.toMap(),
        )
    }
}

/**
 * Reads every movie and show the profile's Jellyfin account can see and keys them by TMDB id.
 * Off the main thread and paged: about a thousand titles are two requests.
 */
suspend fun JellyfinSession.buildTmdbIndex(): TmdbIndex = withContext(Dispatchers.IO) {
    buildTmdbIndex { start, limit ->
        val page = api.libraryApi.getItems(
            GetItemsRequest(
                userId = userId,
                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                recursive = true,
                fields = listOf(ItemFields.PROVIDER_IDS),
                enableImages = false,
                startIndex = start,
                limit = limit,
                enableTotalRecordCount = start == 0,
            ),
        ).content
        IndexPage(
            page.items.map { IndexedTitle(it.id.toString(), it.providerIds?.get("Tmdb")?.toIntOrNull(), it.type == BaseItemKind.SERIES) },
            page.totalRecordCount,
        )
    }
}

/**
 * The paging of the index read, apart from the server: pages of [pageSize] from [page] until the
 * total the first page reported is read, a page comes back empty, or [maxItems] is reached (a
 * ceiling, so a broken server can't keep a page loading for minutes).
 */
internal suspend fun buildTmdbIndex(
    pageSize: Int = PAGE_SIZE,
    maxItems: Int = MAX_ITEMS,
    page: suspend (start: Int, limit: Int) -> IndexPage,
): TmdbIndex {
    val titles = ArrayList<IndexedTitle>()
    var total: Int? = null
    while (titles.size < maxItems) {
        val read = page(titles.size, minOf(pageSize, maxItems - titles.size))
        if (total == null && read.total != null && read.total > 0) total = read.total
        titles += read.titles
        if (read.titles.isEmpty() || (total != null && titles.size >= total)) break
    }
    return TmdbIndex.of(titles)
}

/** A page of the index read: big enough that a large library is a handful of requests. */
internal const val PAGE_SIZE = 500

internal const val MAX_ITEMS = 20_000

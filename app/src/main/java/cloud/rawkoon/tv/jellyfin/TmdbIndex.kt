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

/**
 * A profile's library titles by TMDB id. One paged read of Jellyfin's movies and shows replaces a
 * name search per shelf entry, which on a low-end TV made a shelf of forty titles take minutes.
 */
class TmdbIndex(val byKey: Map<TmdbKey, MediaItem>) {
    val size: Int get() = byKey.size

    fun item(tmdbId: Int, isShow: Boolean): MediaItem? = byKey[TmdbKey(tmdbId, isShow)]

    fun toJson(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("items", JSONArray().apply { byKey.values.forEach { put(it.toJson()) } })

    companion object {
        private const val VERSION = 1

        /** What a profile's last index was saved as; null when it is missing or from another version. */
        fun fromJson(text: String): TmdbIndex? = runCatching {
            val json = JSONObject(text)
            if (json.optInt("version") != VERSION) return null
            val array = json.optJSONArray("items") ?: return null
            val items = (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(MediaItem::fromJson) }
            of(items)
        }.getOrNull()

        fun of(items: List<MediaItem>): TmdbIndex = TmdbIndex(
            items.mapNotNull { item ->
                // Only a movie or a show can stand for a shelf entry; a season's provider id is its own
                if (item.kind != MediaItem.Kind.MOVIE && item.kind != MediaItem.Kind.SERIES) return@mapNotNull null
                val tmdbId = item.libraryTmdbId ?: return@mapNotNull null
                TmdbKey(tmdbId, item.kind == MediaItem.Kind.SERIES) to item
            }.toMap(),
        )
    }
}

/**
 * Reads every movie and show the profile's Jellyfin account can see and keys them by TMDB id.
 * Off the main thread and paged: about a thousand titles are two requests, tens of milliseconds of
 * mapping. A title without a TMDB id is left out; nothing on a shelf can point at it.
 */
suspend fun JellyfinSession.buildTmdbIndex(): TmdbIndex = withContext(Dispatchers.IO) {
    val items = ArrayList<MediaItem>()
    var start = 0
    while (start < MAX_ITEMS) {
        val page = api.libraryApi.getItems(
            GetItemsRequest(
                userId = userId,
                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                recursive = true,
                // The provider ids are the whole point; the rest is what a card draws
                fields = listOf(ItemFields.PROVIDER_IDS, ItemFields.OVERVIEW, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
                startIndex = start,
                limit = PAGE_SIZE,
                enableTotalRecordCount = start == 0,
            ),
        ).content
        items += page.items.map { MediaItem.from(it, images) }
        start += page.items.size
        val total = page.totalRecordCount
        if (page.items.isEmpty() || (total != null && total > 0 && start >= total)) break
    }
    TmdbIndex.of(items)
}

/** A page of the index read: big enough that a large library is a handful of requests. */
private const val PAGE_SIZE = 500

/** A ceiling on the read, so a broken server can't keep a page loading for minutes. */
private const val MAX_ITEMS = 20_000

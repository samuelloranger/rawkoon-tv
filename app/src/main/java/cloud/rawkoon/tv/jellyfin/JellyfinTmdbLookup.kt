package cloud.rawkoon.tv.jellyfin

import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetItemsRequest

/**
 * Finds the library item for a TMDB title, so a title Rawkoon says is available can be opened
 * in Jellyfin. The Jellyfin API cannot filter on the value of a provider id, so this searches by
 * name and keeps the match whose TMDB id is the one asked for.
 */
class JellyfinTmdbLookup(private val session: JellyfinSession) {
    /** The item for [tmdbId], or null when Jellyfin has none, is ambiguous about it, or can't be reached. */
    suspend fun find(tmdbId: Int, title: String, year: Int?, isShow: Boolean): MediaItem? {
        if (title.isBlank()) return null
        return try {
            // A year that differs by one between TMDB and Jellyfin would hide the title: retry without
            val dtos = search(title, year, isShow).ifEmpty { if (year != null) search(title, null, isShow) else emptyList() }
            val picked = pick(dtos.map { Candidate(it.id.toString(), it.providerIds?.get("Tmdb")) }, tmdbId) ?: return null
            dtos.firstOrNull { it.id.toString() == picked.id }?.let { MediaItem.from(it, session.images) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun search(title: String, year: Int?, isShow: Boolean) = session.api.itemsApi.getItems(
        GetItemsRequest(
            userId = session.userId,
            searchTerm = title,
            includeItemTypes = listOf(if (isShow) BaseItemKind.SERIES else BaseItemKind.MOVIE),
            recursive = true,
            fields = listOf(ItemFields.OVERVIEW, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.PROVIDER_IDS),
            years = year?.let { listOf(it) },
            limit = LIMIT,
        ),
    ).content.items

    /** An item the name search found, with the TMDB id Jellyfin has for it, if any. */
    data class Candidate(val id: String, val tmdbId: String?)

    companion object {
        private const val LIMIT = 20

        /**
         * The candidate for [tmdbId]: the one whose TMDB id is verified, else the only candidate
         * when Jellyfin has no TMDB id for it. Several unverified candidates are ambiguous: null.
         */
        fun pick(candidates: List<Candidate>, tmdbId: Int): Candidate? =
            candidates.firstOrNull { it.tmdbId == tmdbId.toString() }
                ?: candidates.singleOrNull()?.takeIf { it.tmdbId.isNullOrBlank() }
    }
}

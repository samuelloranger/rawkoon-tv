package com.github.damontecres.wholphin.rawkoon.seerr

import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds the Jellyfin item for a TMDB title so that "go to" can open it. The Jellyfin API cannot filter on the value of a
 * provider id, so this searches by name and keeps the match whose TMDB id is the one asked for.
 */
@Singleton
class JellyfinTmdbLookup
    @Inject
    constructor(
        private val api: ApiClient,
    ) {
        private val found = ConcurrentHashMap<Pair<Boolean, Int>, UUID>()

        suspend fun find(
            tmdbId: Int,
            title: String?,
            year: Int?,
            isShow: Boolean,
        ): UUID? {
            found[isShow to tmdbId]?.let { return it }
            if (title.isNullOrBlank()) return null
            return try {
                val request =
                    GetItemsRequest(
                        searchTerm = title,
                        includeItemTypes = listOf(if (isShow) BaseItemKind.SERIES else BaseItemKind.MOVIE),
                        recursive = true,
                        fields = listOf(ItemFields.PROVIDER_IDS),
                        years = year?.let { listOf(it) },
                        limit = LOOKUP_LIMIT,
                    )
                val candidates =
                    api.itemsApi
                        .getItems(request)
                        .content.items
                        .map { Candidate(it.id, it.providerIds?.get("Tmdb")) }
                pick(candidates, tmdbId)?.also { found[isShow to tmdbId] = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Jellyfin lookup for tmdb %d failed", tmdbId)
                null
            }
        }

        /** An item the name search found, with the TMDB id Jellyfin has for it, if any */
        data class Candidate(
            val id: UUID,
            val tmdbId: String?,
        )

        companion object {
            private const val LOOKUP_LIMIT = 20

            /**
             * The Jellyfin item for [tmdbId] among name matches of the right type: the one whose TMDB id is verified, else
             * the only candidate when Jellyfin has no TMDB id for it. Several unverified candidates are ambiguous: null,
             * which sends the caller to the discover page instead.
             */
            fun pick(
                candidates: List<Candidate>,
                tmdbId: Int,
            ): UUID? =
                candidates.firstOrNull { it.tmdbId == tmdbId.toString() }?.id
                    ?: candidates.singleOrNull()?.takeIf { it.tmdbId.isNullOrBlank() }?.id
        }
    }

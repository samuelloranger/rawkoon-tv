package com.github.damontecres.wholphin.rawkoon.home

import android.content.Context
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.HomePageSettings
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.RawkoonHomeRow
import com.github.damontecres.wholphin.data.model.RawkoonRequestsRow
import com.github.damontecres.wholphin.data.model.RawkoonUpcomingRow
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.rawkoon.RawkoonClient
import com.github.damontecres.wholphin.rawkoon.RawkoonMediaType
import com.github.damontecres.wholphin.rawkoon.RawkoonSettingsRepository
import com.github.damontecres.wholphin.rawkoon.TitleStatus
import com.github.damontecres.wholphin.rawkoon.seerr.JellyfinTmdbLookup
import com.github.damontecres.wholphin.rawkoon.seerr.RkRequests
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.HomeRowConfigDisplay
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.hilt.IoCoroutineScope
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.util.ResStringProvider
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two Rawkoon rows of the home page, plugged into the home page settings as [RawkoonHomeRow] entries so they can
 * be reordered, hidden or removed like any other row. A row with nothing to show, or a failing call, is simply hidden.
 */
@Singleton
class RawkoonHomeRows
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val rawkoon: RawkoonClient,
        private val settings: RawkoonSettingsRepository,
        private val navigationManager: NavigationManager,
        private val backdropService: BackdropService,
        private val jellyfinLookup: JellyfinTmdbLookup,
        @param:IoCoroutineScope private val scope: CoroutineScope,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        internal var now: () -> Instant = Instant::now

        private data class Cached<T>(
            val at: Instant,
            val value: T,
        )

        private var upcomingCache: Cached<List<UpcomingCard>>? = null
        private var requestsCache: Cached<List<RequestCardData>>? = null

        /** The rows a new home page starts with */
        fun defaultRows(): List<HomeRowConfig> = listOf(RawkoonUpcomingRow(), RawkoonRequestsRow())

        fun title(config: RawkoonHomeRow) =
            ResStringProvider(if (config is RawkoonUpcomingRow) R.string.rawkoon_coming_soon else R.string.rawkoon_my_requests)

        fun resolve(
            id: Int,
            config: RawkoonHomeRow,
        ) = HomeRowConfigDisplay(id, title(config), config)

        /**
         * A saved home page from before these rows existed gets them once, right after Continue watching / Next up
         *
         * @return the settings to save, or null when there is nothing to change
         */
        suspend fun withRows(
            userId: UUID,
            saved: HomePageSettings,
        ): HomePageSettings? {
            val key = userId.toString()
            if (settings.homeRowsAddedFor(key)) return null
            settings.markHomeRowsAdded(key)
            if (saved.rows.any { it is RawkoonHomeRow }) return null
            val after = saved.rows.indexOfLast { it.isContinueOrNextUp() }
            val rows = saved.rows.toMutableList().apply { addAll(after + 1, defaultRows()) }
            return saved.copy(rows = rows)
        }

        private fun HomeRowConfig.isContinueOrNextUp() =
            this is HomeRowConfig.ContinueWatching || this is HomeRowConfig.NextUp || this is HomeRowConfig.ContinueWatchingCombined

        suspend fun fetch(config: RawkoonHomeRow): HomeRowLoadingState {
            val hidden = HomeRowLoadingState.Success(title(config), emptyList(), config.viewOptions, config)
            return try {
                if (!settings.current().isConfigured) return hidden
                when (config) {
                    is RawkoonUpcomingRow -> upcomingRow(config) ?: hidden
                    is RawkoonRequestsRow -> requestsRow(config) ?: hidden
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Rawkoon home row failed, hiding it")
                hidden
            }
        }

        private suspend fun upcomingRow(config: RawkoonUpcomingRow): HomeRowLoadingState? {
            val cards = cached(upcomingCache, UPCOMING_TTL_SECONDS) ?: loadUpcoming().also { upcomingCache = Cached(now(), it) }
            if (cards.isEmpty()) return null
            return HomeRowLoadingState.Custom(title(config)) { modifier ->
                UpcomingRowContent(
                    title = title(config).getString(),
                    cards = cards,
                    onClick = ::openUpcoming,
                    onFocus = { card, details -> preview(card.title, details, card.overview, card.backdropUrl ?: card.posterUrl, "upcoming_${card.tmdbId}") },
                    modifier = modifier,
                )
            }
        }

        private suspend fun requestsRow(config: RawkoonRequestsRow): HomeRowLoadingState? {
            val cards = cached(requestsCache, REQUESTS_TTL_SECONDS) ?: loadRequests().also { requestsCache = Cached(now(), it) }
            if (cards.isEmpty()) return null
            return HomeRowLoadingState.Custom(title(config)) { modifier ->
                RequestsRowContent(
                    title = title(config).getString(),
                    cards = cards,
                    onClick = ::openRequest,
                    onFocus = { card, details ->
                        preview(card.request.title, details, null, card.request.posterUrl, "request_${card.request.id}")
                    },
                    modifier = modifier,
                )
            }
        }

        /** Home's header and backdrop show the focused card instead of the last Jellyfin item */
        private fun preview(
            title: String,
            details: String,
            overview: String?,
            backdropUrl: String?,
            key: String,
        ) {
            RawkoonHomePreview.show(RawkoonPreview(title, details, overview))
            scope.launchIO { backdropService.submit("rawkoon_$key", backdropUrl) }
        }

        private fun <T> cached(
            entry: Cached<T>?,
            ttlSeconds: Long,
        ): T? = entry?.takeIf { now().epochSecond - it.at.epochSecond < ttlSeconds }?.value

        private suspend fun loadUpcoming(): List<UpcomingCard> {
            val result = json.decodeFromString<RkUpcoming>(rawkoon.call("GET", "api/dashboard/upcoming").bodyOrThrow())
            if (!result.enabled) return emptyList()
            return RawkoonHomeLogic.upcoming(result.items, LocalDate.now())
        }

        private suspend fun loadRequests(): List<RequestCardData> {
            val userId = settings.current().userId.ifBlank { rawkoon.bootstrap().user.id.also { id -> settings.update { it.copy(userId = id) } } }
            val all = json.decodeFromString<RkRequests>(rawkoon.call("GET", "api/requests").bodyOrThrow()).requests
            val mine = RawkoonHomeLogic.requests(all, userId, now())
            val semaphore = Semaphore(LIVE_STATUS_PARALLELISM)
            return coroutineScope {
                mine
                    .map { request ->
                        async {
                            val live =
                                if (RawkoonHomeLogic.needsLiveStatus(request)) {
                                    semaphore.withPermit { liveStatus(request) }
                                } else {
                                    null
                                }
                            RequestCardData(request, RawkoonHomeLogic.chip(request, live))
                        }
                    }.awaitAll()
            }
        }

        private suspend fun liveStatus(request: com.github.damontecres.wholphin.rawkoon.seerr.RkRequest): TitleStatus? =
            try {
                rawkoon.status(request.tmdbId!!, if (request.type == "movie") RawkoonMediaType.MOVIE else RawkoonMediaType.SHOW)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "No live status for request %d", request.id)
                null
            }

        private fun discoverItem(
            tmdbId: Int,
            isMovie: Boolean,
            title: String,
            overview: String?,
            posterUrl: String?,
            backdropUrl: String?,
            year: Int?,
            availability: SeerrAvailability,
        ) = DiscoverItem(
            id = tmdbId,
            type = if (isMovie) SeerrItemType.MOVIE else SeerrItemType.TV,
            title = title,
            subtitle = null,
            overview = overview,
            availability = availability,
            releaseDate = year?.let { LocalDate.of(it, 1, 1) },
            posterUrl = posterUrl,
            backDropUrl = backdropUrl,
            logoUrl = null,
            jellyfinItemId = null,
        )

        private fun openUpcoming(card: UpcomingCard) {
            navigationManager.navigateTo(
                Destination.DiscoveredItem(
                    discoverItem(
                        card.tmdbId,
                        card.isMovie,
                        card.title,
                        card.overview,
                        card.posterUrl,
                        card.backdropUrl,
                        card.date.year,
                        if (card.inLibrary) SeerrAvailability.PROCESSING else SeerrAvailability.UNKNOWN,
                    ),
                ),
            )
        }

        /** The Jellyfin item when the title is ready and Jellyfin has it, else the discover page */
        private fun openRequest(card: RequestCardData) {
            val request = card.request
            val isMovie = request.type == "movie"
            scope.launchIO {
                val jellyfinId =
                    if (card.chip == RequestChip.Ready) {
                        jellyfinLookup.find(request.tmdbId!!, request.title, request.year, !isMovie)
                    } else {
                        null
                    }
                if (jellyfinId != null) {
                    navigationManager.navigateTo(Destination.MediaItem(jellyfinId, if (isMovie) BaseItemKind.MOVIE else BaseItemKind.SERIES))
                } else {
                    navigationManager.navigateTo(
                        Destination.DiscoveredItem(
                            discoverItem(
                                request.tmdbId!!,
                                isMovie,
                                request.title,
                                null,
                                request.posterUrl,
                                null,
                                request.year,
                                SeerrAvailability.UNKNOWN,
                            ),
                        ),
                    )
                }
            }
        }

        companion object {
            const val UPCOMING_TTL_SECONDS = 15 * 60L
            const val REQUESTS_TTL_SECONDS = 60L
            const val LIVE_STATUS_PARALLELISM = 4
        }
    }

data class RequestCardData(
    val request: com.github.damontecres.wholphin.rawkoon.seerr.RkRequest,
    val chip: RequestChip,
)

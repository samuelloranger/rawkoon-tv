package cloud.rawkoon.tv.jellyfin

import android.content.Context
import cloud.rawkoon.tv.BuildConfig
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.data.Profile
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.android.androidDevice
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.api.request.GetSeasonsRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest

/** The SDK, set up once with this app's name and the device id Jellyfin already knows this TV by. */
object JellyfinSdk {
    private var instance: Jellyfin? = null

    fun get(context: Context): Jellyfin = instance ?: createJellyfin {
        this.context = context.applicationContext
        clientInfo = ClientInfo(name = "Rawkoon TV", version = BuildConfig.VERSION_NAME)
        // The SDK's own device id: the first RawkoonTV used it too, so its sessions stay valid.
        deviceInfo = androidDevice(context.applicationContext)
    }.also { instance = it }
}

/** One profile's connection to its Jellyfin server. */
class JellyfinSession(context: Context, val profile: Profile) {
    val api: ApiClient = JellyfinSdk.get(context).createApi(profile.jellyfinUrl, profile.jellyfinToken)
    val images = ImageUrls(profile.jellyfinUrl)
    val userId: UUID = parseId(profile.jellyfinUserId)
    private val strings = context.applicationContext

    /**
     * The home rows: continue watching, next up, then the latest additions of each movie and show
     * library, in the user's library order. Rows that come back empty are left out, and a row that
     * fails is dropped on its own; the call only fails when every row did.
     */
    suspend fun homeRows(): List<HomeRow> = withContext(Dispatchers.Default) { loadHomeRows() }

    /** Off the main thread (see [homeRows]): decoding the responses and mapping them is the cost. */
    private suspend fun loadHomeRows(): List<HomeRow> = coroutineScope {
        val resume = async {
            attempt {
                val items = api.itemsApi.getResumeItems(
                    GetResumeItemsRequest(userId = userId, limit = ROW_LIMIT, fields = FIELDS, mediaTypes = listOf(MediaType.VIDEO), enableTotalRecordCount = false),
                ).content.items
                HomeRow("resume", strings.getString(R.string.row_resume), items.map { MediaItem.from(it, images) }, landscape = true)
            }
        }
        val nextUp = async {
            attempt {
                val items = api.tvShowsApi.getNextUp(
                    GetNextUpRequest(userId = userId, limit = ROW_LIMIT, fields = FIELDS, enableResumable = false, enableTotalRecordCount = false),
                ).content.items
                HomeRow("nextup", strings.getString(R.string.row_next_up), items.map { MediaItem.from(it, images) }, landscape = true)
            }
        }
        val latest = async {
            val views = attempt {
                api.userViewsApi.getUserViews(userId = userId).content.items
                    .filter { it.collectionType == CollectionType.MOVIES || it.collectionType == CollectionType.TVSHOWS }
            }.getOrElse { return@async listOf(Result.failure(it)) }
            coroutineScope {
                views.map { view ->
                    async {
                        attempt {
                            val items = api.userLibraryApi.getLatestMedia(
                                GetLatestMediaRequest(userId = userId, parentId = view.id, limit = ROW_LIMIT, fields = FIELDS),
                            ).content
                            HomeRow("latest:${view.id}", strings.getString(R.string.row_latest, view.name.orEmpty()), items.map { MediaItem.from(it, images) }, landscape = false)
                        }
                    }
                }.awaitAll()
            }
        }
        val results = listOf(resume.await(), nextUp.await()) + latest.await()
        val rows = results.mapNotNull { it.getOrNull() }
        if (rows.isEmpty()) results.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
        rows.filter { it.items.isNotEmpty() }
    }

    /** Runs [block], keeping a failure as a value so one row can fail alone; cancellation still propagates. */
    private inline fun <T> attempt(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** A movie's or show's page: the item with its genres, studio, cast, and a show's span and counts. */
    suspend fun details(id: String): Details {
        val dto = api.userLibraryApi.getItem(itemId = parseId(id), userId = userId).content
        val item = MediaItem.from(dto, images)
        val people = dto.people.orEmpty().take(PEOPLE_LIMIT).map { p ->
            Person(
                id = p.id.toString(),
                name = p.name.orEmpty(),
                role = p.role?.takeIf { it.isNotBlank() } ?: p.type.serialName,
                imageUrl = p.primaryImageTag?.let { tag -> images.item(p.id, ImageType.PRIMARY, tag) },
            )
        }
        return Details(
            item = item,
            genres = dto.genres.orEmpty().take(3),
            studio = dto.studios?.firstOrNull()?.name,
            endYear = dto.endDate?.year,
            continuing = dto.status.equals("Continuing", ignoreCase = true),
            seasonCount = dto.childCount.takeIf { item.kind == MediaItem.Kind.SERIES },
            episodeCount = dto.recursiveItemCount?.takeIf { item.kind == MediaItem.Kind.SERIES },
            people = people,
        )
    }

    suspend fun seasons(seriesId: String): List<MediaItem> =
        api.tvShowsApi.getSeasons(GetSeasonsRequest(seriesId = parseId(seriesId), userId = userId, fields = FIELDS + ItemFields.CHILD_COUNT)).content.items
            .map { MediaItem.from(it, images) }

    /** A season's episodes, with the media streams the chips need. */
    suspend fun episodes(seriesId: String, seasonId: String): List<MediaItem> =
        api.tvShowsApi.getEpisodes(
            GetEpisodesRequest(seriesId = parseId(seriesId), userId = userId, seasonId = parseId(seasonId), fields = FIELDS + ItemFields.MEDIA_STREAMS),
        ).content.items.map { MediaItem.from(it, images) }

    /** The episode a show resumes at: the one in progress or the next unwatched one. */
    suspend fun nextEpisode(seriesId: String): MediaItem? =
        api.tvShowsApi.getNextUp(GetNextUpRequest(userId = userId, seriesId = parseId(seriesId), limit = 1, fields = FIELDS, enableResumable = true))
            .content.items.firstOrNull()?.let { MediaItem.from(it, images) }

    suspend fun similar(id: String): List<MediaItem> =
        api.libraryApi.getSimilarItems(GetSimilarItemsRequest(itemId = parseId(id), userId = userId, limit = ROW_LIMIT, fields = FIELDS))
            .content.items.map { MediaItem.from(it, images) }

    suspend fun setPlayed(id: String, played: Boolean) {
        if (played) api.playStateApi.markPlayedItem(itemId = parseId(id), userId = userId) else api.playStateApi.markUnplayedItem(itemId = parseId(id), userId = userId)
    }

    suspend fun setFavorite(id: String, favorite: Boolean) {
        if (favorite) api.userLibraryApi.markFavoriteItem(itemId = parseId(id), userId = userId) else api.userLibraryApi.unmarkFavoriteItem(itemId = parseId(id), userId = userId)
    }

    companion object {
        private const val PEOPLE_LIMIT = 12
        private const val ROW_LIMIT = 20
        private val FIELDS = listOf(ItemFields.OVERVIEW, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO)

        /** Jellyfin ids come with or without dashes. */
        fun parseId(id: String): UUID {
            val hex = id.replace("-", "")
            return UUID.fromString("${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}")
        }
    }
}

/** True when the server answered 401: the session ended, so retrying can't help. */
fun Exception.isSessionEnded() = this is InvalidStatusException && status == 401

package cloud.rawkoon.tv.jellyfin

import android.content.Context
import cloud.rawkoon.tv.BuildConfig
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.data.Profile
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.android.androidDevice
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.QuickConnectDto
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import java.util.UUID

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

/** A Jellyfin account a sign-in just produced, ready to become a profile. */
data class SignedInUser(val url: String, val userId: String, val name: String, val token: String, val avatarTag: String?)

/** Signs in to the Jellyfin server at [url] with Quick Connect, once [approve] has had the code approved. */
suspend fun quickConnect(context: Context, url: String, approve: suspend (code: String) -> Unit): SignedInUser {
    val api = JellyfinSdk.get(context).createApi(url.trimEnd('/'))
    // Fails fast with a clear error when the server is unreachable or not Jellyfin
    api.systemApi.getPublicSystemInfo()
    var state = api.quickConnectApi.initiateQuickConnect().content
    approve(state.code)
    while (!state.authenticated) {
        delay(1_000)
        state = api.quickConnectApi.getQuickConnectState(secret = state.secret).content
    }
    val result = api.userApi.authenticateWithQuickConnect(QuickConnectDto(secret = state.secret)).content
    val user = checkNotNull(result.user)
    return SignedInUser(
        url = url.trimEnd('/'),
        userId = user.id.toString().replace("-", ""),
        name = user.name.orEmpty(),
        token = checkNotNull(result.accessToken),
        avatarTag = user.primaryImageTag,
    )
}

/** One profile's connection to its Jellyfin server. */
class JellyfinSession(context: Context, val profile: Profile) {
    val api: ApiClient = JellyfinSdk.get(context).createApi(profile.jellyfinUrl, profile.jellyfinToken)
    val images = ImageUrls(profile.jellyfinUrl)
    val userId: UUID = parseId(profile.jellyfinUserId)
    private val strings = context.applicationContext

    /**
     * The home rows: continue watching, next up, then the latest additions of each movie and show
     * library, in the user's library order. Rows that come back empty are left out.
     */
    suspend fun homeRows(): List<HomeRow> = coroutineScope {
        val resume = async {
            api.itemsApi.getResumeItems(
                GetResumeItemsRequest(userId = userId, limit = ROW_LIMIT, fields = FIELDS, mediaTypes = listOf(MediaType.VIDEO), enableTotalRecordCount = false),
            ).content.items
        }
        val nextUp = async {
            api.tvShowsApi.getNextUp(
                GetNextUpRequest(userId = userId, limit = ROW_LIMIT, fields = FIELDS, enableResumable = false, enableTotalRecordCount = false),
            ).content.items
        }
        val views = api.userViewsApi.getUserViews(userId = userId).content.items
            .filter { it.collectionType == CollectionType.MOVIES || it.collectionType == CollectionType.TVSHOWS }
        val latest = views.map { view ->
            async {
                view to api.userLibraryApi.getLatestMedia(
                    GetLatestMediaRequest(userId = userId, parentId = view.id, limit = ROW_LIMIT, fields = FIELDS),
                ).content
            }
        }
        buildList {
            add(HomeRow("resume", strings.getString(R.string.row_resume), resume.await().map { MediaItem.from(it, images) }, landscape = true))
            add(HomeRow("nextup", strings.getString(R.string.row_next_up), nextUp.await().map { MediaItem.from(it, images) }, landscape = true))
            for (pending in latest) {
                val (view, items) = pending.await()
                add(HomeRow("latest:${view.id}", strings.getString(R.string.row_latest, view.name.orEmpty()), items.map { MediaItem.from(it, images) }, landscape = false))
            }
        }.filter { it.items.isNotEmpty() }
    }

    companion object {
        private const val ROW_LIMIT = 20
        private val FIELDS = listOf(ItemFields.OVERVIEW, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO)

        /** Jellyfin ids come with or without dashes. */
        fun parseId(id: String): UUID {
            val hex = id.replace("-", "")
            return UUID.fromString("${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}")
        }
    }
}


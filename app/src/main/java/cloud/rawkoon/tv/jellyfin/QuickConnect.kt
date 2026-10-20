package cloud.rawkoon.tv.jellyfin

import android.content.Context
import kotlinx.coroutines.delay
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.QuickConnectDto

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


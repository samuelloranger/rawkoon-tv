package com.github.damontecres.wholphin.rawkoon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Rawkoon calls a series a "show" */
enum class RawkoonMediaType(
    val wire: String,
) {
    MOVIE("movie"),
    SHOW("show"),
}

@Serializable
data class RawkoonUser(
    val id: String = "",
    val name: String = "",
    @SerialName("is_admin") val isAdmin: Boolean = false,
)

@Serializable
internal data class DeviceCodeRequest(
    @SerialName("client_id") val clientId: String,
)

/** A code the user approves on another device, then [deviceCode] is exchanged for a token */
@Serializable
data class DeviceCode(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerialName("expires_in") val expiresIn: Int = 600,
    val interval: Int = 5,
) {
    /** ABCD1234 shown as ABCD-1234 */
    val displayCode: String
        get() = if (userCode.length == 8 && '-' !in userCode) "${userCode.take(4)}-${userCode.drop(4)}" else userCode
}

@Serializable
internal data class DeviceTokenRequest(
    @SerialName("grant_type") val grantType: String,
    @SerialName("device_code") val deviceCode: String,
    @SerialName("client_id") val clientId: String,
)

@Serializable
internal data class DeviceTokenResponse(
    @SerialName("access_token") val accessToken: String,
)

@Serializable
internal data class ErrorBody(
    val error: String = "",
)

@Serializable
internal data class EmailSignInRequest(
    val email: String,
    val password: String,
)

@Serializable
data class BootstrapJellyfin(
    val url: String,
    val linked: Boolean = false,
)

@Serializable
data class BootstrapTmdb(
    val key: String,
)

/** What a signed-in device needs to set itself up: its user, the Jellyfin server and the TMDB key */
@Serializable
data class Bootstrap(
    val user: RawkoonUser = RawkoonUser(),
    val jellyfin: BootstrapJellyfin? = null,
    val tmdb: BootstrapTmdb? = null,
)

@Serializable
internal data class QuickConnectApproval(
    val code: String,
)

@Serializable
data class LibraryEntry(
    val id: Int = 0,
    val status: String = "",
    @SerialName("episode_count") val episodeCount: Int? = null,
    @SerialName("downloaded_episode_count") val downloadedEpisodeCount: Int? = null,
)

@Serializable
data class RequestEntry(
    val id: Int = 0,
    val status: String = "",
    @SerialName("deny_reason") val denyReason: String? = null,
)

/** What Rawkoon knows about a title: its library entry and the user's request, either may be absent */
@Serializable
data class TitleStatus(
    val library: LibraryEntry? = null,
    val request: RequestEntry? = null,
)

@Serializable
internal data class CreateRequest(
    val type: String,
    @SerialName("tmdb_id") val tmdbId: Int,
    val title: String,
    @SerialName("poster_url") val posterUrl: String?,
    val year: Int?,
)

package com.github.damontecres.wholphin.rawkoon

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed class RawkoonException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class NotConfigured : RawkoonException("Not configured")

    /** The Rawkoon session is missing or ended: the user must sign in again */
    class Unauthorized : RawkoonException("Unauthorized")

    /** Wrong email or password */
    class InvalidCredentials : RawkoonException("Invalid credentials")

    class RateLimited : RawkoonException("Too many attempts")

    /** The Rawkoon user has no linked Jellyfin user: an admin links it in Rawkoon */
    class NotLinked : RawkoonException("Jellyfin account not linked")

    /** Rawkoon has no Jellyfin integration configured */
    class NoJellyfinIntegration : RawkoonException("No Jellyfin integration")

    /** Jellyfin did not know the Quick Connect code: start again */
    class QuickConnectExpired : RawkoonException("Quick Connect code expired")

    /** Rawkoon could not reach Jellyfin */
    class JellyfinUnreachable : RawkoonException("Jellyfin unreachable from Rawkoon")

    class InvalidSettings(
        cause: Throwable? = null,
    ) : RawkoonException("Invalid URL", cause)

    class Unreachable(
        cause: Throwable,
    ) : RawkoonException("Unreachable", cause)

    class Http(
        val code: Int,
    ) : RawkoonException("HTTP $code")

    class InvalidResponse(
        cause: Throwable,
    ) : RawkoonException("Invalid response", cause)
}

/**
 * Rawkoon API for a signed-in device: every call sends the stored bearer token, and a 401 ends the session
 */
@Singleton
class RawkoonClient
    @Inject
    constructor(
        private val http: RawkoonHttp,
        private val settings: RawkoonSettingsRepository,
        private val session: RawkoonSession,
    ) {
        suspend fun status(
            tmdbId: Int,
            type: RawkoonMediaType,
        ): TitleStatus {
            val query = mapOf("tmdb_id" to tmdbId.toString(), "type" to type.wire)
            return http.decode(authorized("GET", "api/requests/status", query, null))
        }

        /**
         * Request a whole title; "already requested" and "already in the library" both leave the title as wanted, so they are not errors
         */
        suspend fun request(
            tmdbId: Int,
            type: RawkoonMediaType,
            title: String,
            posterUrl: String?,
            year: Int?,
        ) {
            val body =
                http.json.encodeToString(CreateRequest.serializer(), CreateRequest(type.wire, tmdbId, title, posterUrl, year))
            try {
                authorized("POST", "api/requests", emptyMap(), body)
            } catch (e: RawkoonException.Http) {
                if (e.code != HTTP_BAD_REQUEST && e.code != HTTP_CONFLICT) throw e
                Timber.w("Request not created (HTTP %d): already requested or in the library", e.code)
            }
        }

        suspend fun bootstrap(): Bootstrap = http.decode(authorized("GET", "api/tv/bootstrap", emptyMap(), null))

        /**
         * Have Rawkoon approve a Jellyfin Quick Connect [code] for the Jellyfin user linked to this account
         */
        suspend fun approveQuickConnect(code: String) {
            val body = http.json.encodeToString(QuickConnectApproval.serializer(), QuickConnectApproval(code))
            val response = send("POST", "api/tv/jellyfin/quick-connect", emptyMap(), body)
            when (response.code) {
                HTTP_FORBIDDEN -> throw RawkoonException.NotLinked()
                HTTP_NOT_FOUND -> throw RawkoonException.QuickConnectExpired()
                HTTP_UNAVAILABLE -> throw RawkoonException.NoJellyfinIntegration()
                HTTP_BAD_GATEWAY -> throw RawkoonException.JellyfinUnreachable()
            }
            response.bodyOrThrow()
        }

        private suspend fun authorized(
            method: String,
            path: String,
            query: Map<String, String>,
            body: String?,
        ): String = send(method, path, query, body).bodyOrThrow()

        private suspend fun send(
            method: String,
            path: String,
            query: Map<String, String>,
            body: String?,
        ): RawResponse {
            val config = settings.current()
            // Values typed with a TV keyboard or pasted can carry newlines or zero-width characters
            val url = config.url.withoutInvisible().trim()
            if (url.isBlank()) throw RawkoonException.NotConfigured()
            if (config.token.isBlank()) throw RawkoonException.Unauthorized()
            val response = http.send(url, method, path, query, body, config.token)
            if (response.code == HTTP_UNAUTHORIZED) {
                session.expired()
                throw RawkoonException.Unauthorized()
            }
            return response
        }

        private companion object {
            const val HTTP_BAD_REQUEST = 400
            const val HTTP_UNAUTHORIZED = 401
            const val HTTP_FORBIDDEN = 403
            const val HTTP_NOT_FOUND = 404
            const val HTTP_CONFLICT = 409
            const val HTTP_BAD_GATEWAY = 502
            const val HTTP_UNAVAILABLE = 503
        }
    }

package com.github.damontecres.wholphin.rawkoon

import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import kotlinx.coroutines.delay
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.QuickConnectDto
import org.jellyfin.sdk.model.api.QuickConnectResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Jellyfin sign-in with Quick Connect: initiate, wait for someone to authorize the code, authenticate, and save the
 * session like any other Jellyfin sign-in
 */
@Singleton
class QuickConnectLogin
    @Inject
    constructor(
        private val jellyfin: Jellyfin,
        private val serverRepository: ServerRepository,
    ) {
        /**
         * @param onInitiated called with the initiated request, e.g. to show the code or have it authorized elsewhere
         * @param onStatus called after each poll
         */
        suspend fun login(
            server: JellyfinServer,
            existingUser: JellyfinUser?,
            pollIntervalMs: Long = POLL_INTERVAL_MS,
            onInitiated: suspend (QuickConnectResult) -> Unit = {},
            onStatus: (QuickConnectResult) -> Unit = {},
        ): CurrentUser {
            val api = jellyfin.createApi(server.url)
            var status =
                api
                    .quickConnectApi
                    .initiateQuickConnect()
                    .content
            onStatus(status)
            onInitiated(status)

            while (!status.authenticated) {
                delay(pollIntervalMs)
                status =
                    api.quickConnectApi
                        .getQuickConnectState(
                            secret = status.secret,
                        ).content
                onStatus(status)
            }
            val authenticationResult by api.userApi.authenticateWithQuickConnect(
                QuickConnectDto(secret = status.secret),
            )
            return serverRepository.changeUser(
                server.url,
                authenticationResult,
                existingUser,
            )
        }

        companion object {
            const val POLL_INTERVAL_MS = 5_000L
        }
    }

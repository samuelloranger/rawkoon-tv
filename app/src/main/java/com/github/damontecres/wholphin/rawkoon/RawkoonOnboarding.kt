package com.github.damontecres.wholphin.rawkoon

import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class OnboardingStep {
    READING_ACCOUNT,
    ADDING_SERVER,
    SIGNING_IN_JELLYFIN,
}

/**
 * Right after the Rawkoon sign-in: read the account's setup from Rawkoon (Jellyfin URL), add the Jellyfin
 * server, and sign in to it with a Quick Connect that Rawkoon approves
 */
@Singleton
class RawkoonOnboarding
    @Inject
    constructor(
        private val client: RawkoonClient,
        private val settings: RawkoonSettingsRepository,
        private val jellyfin: Jellyfin,
        private val serverRepository: ServerRepository,
        private val quickConnect: QuickConnectLogin,
    ) {
        /**
         * The account's setup. Keeps the user name; stops when there is no Jellyfin to sign in to.
         */
        suspend fun readAccount(): Bootstrap {
            val bootstrap = client.bootstrap()
            val jellyfinInfo = bootstrap.jellyfin ?: throw RawkoonException.NoJellyfinIntegration()
            if (!jellyfinInfo.linked) throw RawkoonException.NotLinked()
            settings.update { it.copy(userName = bootstrap.user.name) }
            return bootstrap
        }

        suspend fun run(onStep: (OnboardingStep) -> Unit): CurrentUser {
            onStep(OnboardingStep.READING_ACCOUNT)
            val url = checkNotNull(readAccount().jellyfin).url
            serverRepository.current.value
                ?.takeIf { sameServer(it.server.url, url) }
                ?.let { return it }
            onStep(OnboardingStep.ADDING_SERVER)
            val server = addServer(url)
            onStep(OnboardingStep.SIGNING_IN_JELLYFIN)
            return try {
                signIn(server)
            } catch (_: RawkoonException.QuickConnectExpired) {
                // Jellyfin forgot the code between the initiate and the approval: one more try
                signIn(server)
            }
        }

        private suspend fun signIn(server: JellyfinServer): CurrentUser =
            quickConnect.login(server, existingUser = null, pollIntervalMs = POLL_INTERVAL_MS, onInitiated = { client.approveQuickConnect(it.code) })

        /**
         * Same discovery and save as adding a server by hand
         */
        private suspend fun addServer(inputUrl: String): JellyfinServer {
            val best =
                try {
                    jellyfin.discovery
                        .getRecommendedServers(inputUrl)
                        .sortedBy { it.score }
                        .firstOrNull { it.score != RecommendedServerInfoScore.BAD }
                } catch (e: Exception) {
                    Timber.w(e, "Error discovering the Jellyfin server")
                    throw RawkoonException.JellyfinUnreachable()
                }
            val info = best?.systemInfo?.getOrNull()
            val id = info?.id?.toUUIDOrNull()
            if (best == null || info == null || id == null) throw RawkoonException.JellyfinUnreachable()
            val server = JellyfinServer(id = id, name = info.serverName, url = best.address, version = info.version)
            serverRepository.addAndChangeServer(server)
            return server
        }

        private fun sameServer(
            a: String,
            b: String,
        ) = a.trim().trimEnd('/').equals(b.trim().trimEnd('/'), ignoreCase = true)

        private companion object {
            const val POLL_INTERVAL_MS = 1_000L
        }
    }

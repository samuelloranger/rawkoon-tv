package com.github.damontecres.wholphin.rawkoon.seerr

import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrUser
import com.github.damontecres.wholphin.rawkoon.RawkoonSettingsRepository
import com.github.damontecres.wholphin.services.SeerrApi
import com.github.damontecres.wholphin.services.SeerrServerRepository
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns on the discover screens with Rawkoon as their Seerr: points the Seerr client at the interceptor's host and marks
 * the connection as established. Nothing is saved, it is redone for each Jellyfin user switch.
 */
@Singleton
class RawkoonSeerrActivator
    @Inject
    constructor(
        private val settings: RawkoonSettingsRepository,
        private val seerrApi: SeerrApi,
        private val seerrServerRepository: SeerrServerRepository,
    ) {
        /**
         * @return true when Rawkoon now provides discovery for [user]
         */
        suspend fun activate(user: JellyfinUser): Boolean {
            if (!settings.current().isConfigured) return false
            return try {
                seerrApi.update(RawkoonSeerrInterceptor.BASE_URL, null)
                val server = SeerrServer(url = RawkoonSeerrInterceptor.BASE_URL, name = "Rawkoon")
                val seerrUser = SeerrUser(user.rowId, server.id, SeerrAuthMethod.API_KEY, null, null, null)
                seerrServerRepository.set(server, seerrUser, seerrApi.api.usersApi.authMeGet())
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not turn on Rawkoon discovery")
                seerrServerRepository.clear()
                false
            }
        }
    }

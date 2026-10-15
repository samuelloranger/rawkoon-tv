package com.github.damontecres.wholphin.rawkoon

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.services.SetupNavigationManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ending the Rawkoon session: both ways land on the sign-in screen
 */
@Singleton
class RawkoonSession
    @Inject
    constructor(
        private val settings: RawkoonSettingsRepository,
        private val serverRepository: ServerRepository,
        private val setupNavigation: SetupNavigationManager,
    ) {
        /**
         * Rawkoon refused the token: forget it and ask for a new sign-in on the same server
         */
        suspend fun expired() {
            settings.clearSession()
            setupNavigation.navigateTo(SetupDestination.RawkoonFlow)
        }

        /**
         * The user signed out: forget the Rawkoon session and the Jellyfin one it handed over
         */
        suspend fun signOut() {
            settings.clearSession()
            serverRepository.current.value
                ?.user
                ?.let { serverRepository.removeUser(it) }
            setupNavigation.navigateTo(SetupDestination.RawkoonFlow)
        }
    }

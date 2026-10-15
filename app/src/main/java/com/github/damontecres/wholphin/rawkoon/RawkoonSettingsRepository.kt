package com.github.damontecres.wholphin.rawkoon

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class RawkoonConfig(
    val url: String = "",
    val token: String = "",
    val userName: String = "",
) {
    val hasUrl: Boolean get() = url.isNotBlank()

    /** A server and a session on it */
    val isConfigured: Boolean get() = hasUrl && token.isNotBlank()
}

/**
 * Rawkoon server and session, stored on the device only (excluded from Android backups)
 */
@Singleton
class RawkoonSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<RawkoonSettings>,
    ) {
        val config: Flow<RawkoonConfig> = dataStore.data.map { it.toConfig() }

        suspend fun current(): RawkoonConfig = config.first()

        suspend fun update(transform: (RawkoonConfig) -> RawkoonConfig) {
            dataStore.updateData { settings ->
                val updated = transform(settings.toConfig())
                settings
                    .toBuilder()
                    .setUrl(updated.url.trim())
                    .setToken(updated.token.trim())
                    .setUserName(updated.userName)
                    .build()
            }
        }

        /** Forget the session but keep the server URL */
        suspend fun clearSession() = update { it.copy(token = "", userName = "") }

        private fun RawkoonSettings.toConfig() = RawkoonConfig(url, token, userName)
    }

package com.github.damontecres.wholphin.rawkoon

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

enum class RawkoonStep {
    URL,
    SIGN_IN,
    SETUP,
}

/**
 * Which screen of the Rawkoon-first start is showing: what is stored decides, except when the user asked to change server
 */
@Singleton
class RawkoonFlow
    @Inject
    constructor(
        settings: RawkoonSettingsRepository,
    ) {
        private val forcedUrl = MutableStateFlow(false)

        val step: Flow<RawkoonStep> =
            combine(settings.config, forcedUrl) { config, forced ->
                when {
                    forced || !config.hasUrl -> RawkoonStep.URL
                    !config.isConfigured -> RawkoonStep.SIGN_IN
                    else -> RawkoonStep.SETUP
                }
            }.distinctUntilChanged()

        fun changeServer() {
            forcedUrl.value = true
        }

        fun serverChosen() {
            forcedUrl.value = false
        }
    }

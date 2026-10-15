package com.github.damontecres.wholphin.rawkoon.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.rawkoon.RawkoonConfig
import com.github.damontecres.wholphin.rawkoon.RawkoonSession
import com.github.damontecres.wholphin.rawkoon.RawkoonSettingsRepository
import com.github.damontecres.wholphin.ui.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class RawkoonSettingsViewModel
    @Inject
    constructor(
        private val repository: RawkoonSettingsRepository,
        private val session: RawkoonSession,
    ) : ViewModel() {
        val config: Flow<RawkoonConfig> = repository.config

        fun setInterfaceScale(percent: Int) {
            viewModelScope.launchIO { repository.update { it.copy(interfaceScalePercent = percent) } }
        }

        /** Forget the Rawkoon and Jellyfin sessions; the app returns to the Rawkoon sign-in */
        fun signOut() {
            viewModelScope.launchIO { session.signOut() }
        }
    }

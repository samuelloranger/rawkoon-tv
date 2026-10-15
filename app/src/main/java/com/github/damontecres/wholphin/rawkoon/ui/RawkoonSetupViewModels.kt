package com.github.damontecres.wholphin.rawkoon.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.rawkoon.DeviceCode
import com.github.damontecres.wholphin.rawkoon.DeviceSignIn
import com.github.damontecres.wholphin.rawkoon.DeviceSignInState
import com.github.damontecres.wholphin.rawkoon.OnboardingStep
import com.github.damontecres.wholphin.rawkoon.RawkoonAuthClient
import com.github.damontecres.wholphin.rawkoon.RawkoonException
import com.github.damontecres.wholphin.rawkoon.RawkoonFlow
import com.github.damontecres.wholphin.rawkoon.RawkoonOnboarding
import com.github.damontecres.wholphin.rawkoon.RawkoonSession
import com.github.damontecres.wholphin.rawkoon.RawkoonSettingsRepository
import com.github.damontecres.wholphin.rawkoon.RawkoonStep
import com.github.damontecres.wholphin.rawkoon.ui.messageRes
import com.github.damontecres.wholphin.rawkoon.ui.normalizeRawkoonUrl
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.services.SetupNavigationManager
import com.github.damontecres.wholphin.ui.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class RawkoonFlowViewModel
    @Inject
    constructor(
        flow: RawkoonFlow,
    ) : ViewModel() {
        val step: StateFlow<RawkoonStep?> =
            flow.step.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }

data class RawkoonUrlState(
    val url: String = "",
    val checking: Boolean = false,
    @param:StringRes val error: Int? = null,
)

/**
 * First screen: which Rawkoon server. The address is checked by asking it for a sign-in code.
 */
@HiltViewModel
class RawkoonUrlViewModel
    @Inject
    constructor(
        private val settings: RawkoonSettingsRepository,
        private val auth: RawkoonAuthClient,
        private val flow: RawkoonFlow,
    ) : ViewModel() {
        private val _state = MutableStateFlow(RawkoonUrlState())
        val state: StateFlow<RawkoonUrlState> = _state

        init {
            viewModelScope.launchIO {
                val stored = settings.current().url
                _state.update { if (it.url.isBlank()) it.copy(url = stored) else it }
            }
        }

        fun setUrl(value: String) = _state.update { it.copy(url = value, error = null) }

        fun submit() {
            if (_state.value.checking) return
            val url = normalizeRawkoonUrl(_state.value.url)
            if (url.isBlank()) return
            _state.update { it.copy(url = url, checking = true, error = null) }
            viewModelScope.launchIO {
                try {
                    auth.requestDeviceCode(url)
                    // A token belongs to the server that issued it
                    settings.update { if (it.url == url) it else it.copy(url = url, token = "", userName = "") }
                    _state.update { it.copy(checking = false) }
                    flow.serverChosen()
                } catch (e: RawkoonException) {
                    Timber.w(e, "Rawkoon server check failed")
                    _state.update { it.copy(checking = false, error = e.messageRes()) }
                }
            }
        }
    }

data class RawkoonSignInState(
    val serverUrl: String = "",
    /** The code to approve; null while one is requested */
    val code: DeviceCode? = null,
    @param:StringRes val error: Int? = null,
    val denied: Boolean = false,
    val emailForm: Boolean = false,
    val busy: Boolean = false,
)

/**
 * Sign in to the stored server: a code approved on a phone, or email and password
 */
@HiltViewModel
class RawkoonSignInViewModel
    @Inject
    constructor(
        private val settings: RawkoonSettingsRepository,
        private val deviceSignIn: DeviceSignIn,
        private val auth: RawkoonAuthClient,
        private val flow: RawkoonFlow,
    ) : ViewModel() {
        private val _state = MutableStateFlow(RawkoonSignInState())
        val state: StateFlow<RawkoonSignInState> = _state

        private var deviceJob: Job? = null
        private var serverUrl = ""

        fun startDeviceCode() {
            deviceJob?.cancel()
            _state.update { it.copy(code = null, error = null, denied = false) }
            deviceJob =
                viewModelScope.launchIO {
                    serverUrl = settings.current().url
                    _state.update { it.copy(serverUrl = serverUrl) }
                    deviceSignIn.run(serverUrl).collect { step ->
                        when (step) {
                            DeviceSignInState.RequestingCode -> _state.update { it.copy(code = null) }
                            is DeviceSignInState.Waiting -> _state.update { it.copy(code = step.code, error = null, denied = false) }
                            DeviceSignInState.Denied -> _state.update { it.copy(code = null, denied = true) }
                            is DeviceSignInState.Failed -> _state.update { it.copy(code = null, error = step.reason.messageRes()) }
                            is DeviceSignInState.SignedIn -> finish(step.token)
                        }
                    }
                }
        }

        fun stopDeviceCode() {
            deviceJob?.cancel()
        }

        fun showEmailForm(show: Boolean) {
            _state.update { it.copy(emailForm = show, error = null) }
            if (show) stopDeviceCode() else startDeviceCode()
        }

        fun signInWithEmail(
            email: String,
            password: String,
        ) {
            if (_state.value.busy) return
            _state.update { it.copy(busy = true, error = null) }
            viewModelScope.launchIO {
                try {
                    finish(auth.signInWithEmail(serverUrl.ifBlank { settings.current().url }, email, password))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RawkoonException) {
                    _state.update { it.copy(busy = false, error = e.messageRes()) }
                }
            }
        }

        fun changeServer() {
            stopDeviceCode()
            flow.changeServer()
        }

        private suspend fun finish(token: String) {
            settings.update { it.copy(token = token) }
        }

        override fun onCleared() {
            deviceJob?.cancel()
        }
    }

data class RawkoonSetupState(
    val step: OnboardingStep? = null,
    @param:StringRes val error: Int? = null,
    /** Retrying cannot help until an admin acts */
    val retryable: Boolean = true,
)

/**
 * Last stop: read the account from Rawkoon, sign in to Jellyfin, open the app
 */
@HiltViewModel
class RawkoonSetupViewModel
    @Inject
    constructor(
        private val onboarding: RawkoonOnboarding,
        private val session: RawkoonSession,
        private val setupNavigation: SetupNavigationManager,
        private val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(RawkoonSetupState())
        val state: StateFlow<RawkoonSetupState> = _state

        private var job: Job? = null

        fun start() {
            if (job?.isActive == true) return
            _state.value = RawkoonSetupState(step = OnboardingStep.READING_ACCOUNT)
            job =
                viewModelScope.launchIO {
                    try {
                        val current = onboarding.run { step -> _state.update { it.copy(step = step) } }
                        navigationManager.reloadHome()
                        setupNavigation.navigateTo(SetupDestination.AppContent(current))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: RawkoonException.Unauthorized) {
                        // The session is gone: the client already sent the user back to the sign-in
                        Timber.i(e, "Rawkoon session ended during setup")
                    } catch (e: RawkoonException) {
                        Timber.w(e, "Rawkoon setup failed")
                        _state.value =
                            RawkoonSetupState(
                                error = e.messageRes(),
                                retryable = e !is RawkoonException.NotLinked && e !is RawkoonException.NoJellyfinIntegration,
                            )
                    } catch (e: Exception) {
                        Timber.e(e, "Jellyfin sign-in failed")
                        _state.value = RawkoonSetupState(error = R.string.rawkoon_error_jellyfin_signin)
                    }
                }
        }

        fun signOut() {
            viewModelScope.launchIO { session.signOut() }
        }
    }

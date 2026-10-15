package com.github.damontecres.wholphin.rawkoon

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

sealed interface DeviceSignInState {
    data object RequestingCode : DeviceSignInState

    /** Show [code]; the user approves it on another device */
    data class Waiting(
        val code: DeviceCode,
    ) : DeviceSignInState

    data object Denied : DeviceSignInState

    data class Failed(
        val reason: RawkoonException,
    ) : DeviceSignInState

    data class SignedIn(
        val token: String,
    ) : DeviceSignInState
}

/**
 * Device-code sign-in: ask for a code, poll for the approval at the server's pace, and ask for a new code when it expires
 */
class DeviceSignIn
    @Inject
    constructor(
        private val auth: RawkoonAuthClient,
    ) {
        fun run(baseUrl: String): Flow<DeviceSignInState> =
            flow {
                emit(DeviceSignInState.RequestingCode)
                try {
                    var code = auth.requestDeviceCode(baseUrl)
                    emit(DeviceSignInState.Waiting(code))
                    var intervalMs = code.interval.coerceAtLeast(1) * MS
                    while (true) {
                        delay(intervalMs)
                        when (val poll = auth.pollDeviceToken(baseUrl, code.deviceCode)) {
                            DevicePoll.Pending -> {}

                            DevicePoll.SlowDown -> intervalMs += SLOW_DOWN_MS

                            DevicePoll.Expired -> {
                                code = auth.requestDeviceCode(baseUrl)
                                intervalMs = code.interval.coerceAtLeast(1) * MS
                                emit(DeviceSignInState.Waiting(code))
                            }

                            DevicePoll.Denied -> {
                                emit(DeviceSignInState.Denied)
                                return@flow
                            }

                            is DevicePoll.Granted -> {
                                emit(DeviceSignInState.SignedIn(poll.token))
                                return@flow
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RawkoonException) {
                    emit(DeviceSignInState.Failed(e))
                }
            }

        private companion object {
            const val MS = 1_000L
            const val SLOW_DOWN_MS = 5_000L
        }
    }

package com.github.damontecres.wholphin.rawkoon

import javax.inject.Inject
import javax.inject.Singleton

/** Answer to one poll of the device-code token endpoint */
sealed interface DevicePoll {
    data object Pending : DevicePoll

    data object SlowDown : DevicePoll

    data object Expired : DevicePoll

    data object Denied : DevicePoll

    data class Granted(
        val token: String,
    ) : DevicePoll
}

/**
 * The calls that happen before there is a session: device-code sign-in and email sign-in
 */
@Singleton
class RawkoonAuthClient
    @Inject
    constructor(
        private val http: RawkoonHttp,
    ) {
        suspend fun requestDeviceCode(baseUrl: String): DeviceCode {
            val body = http.json.encodeToString(DeviceCodeRequest.serializer(), DeviceCodeRequest(CLIENT_ID))
            return http.decode(http.send(clean(baseUrl), "POST", "api/auth/device/code", body = body).bodyOrThrow())
        }

        suspend fun pollDeviceToken(
            baseUrl: String,
            deviceCode: String,
        ): DevicePoll {
            val body =
                http.json.encodeToString(DeviceTokenRequest.serializer(), DeviceTokenRequest(DEVICE_GRANT, deviceCode, CLIENT_ID))
            val response = http.send(clean(baseUrl), "POST", "api/auth/device/token", body = body)
            if (response.code in 200..299) {
                return DevicePoll.Granted(http.decode<DeviceTokenResponse>(response.body).accessToken)
            }
            if (response.code != HTTP_BAD_REQUEST) throw RawkoonException.Http(response.code)
            return when (runCatching { http.json.decodeFromString<ErrorBody>(response.body).error }.getOrDefault("")) {
                "authorization_pending" -> DevicePoll.Pending
                "slow_down" -> DevicePoll.SlowDown
                "expired_token" -> DevicePoll.Expired
                "access_denied" -> DevicePoll.Denied
                else -> throw RawkoonException.Http(response.code)
            }
        }

        /**
         * Sign in with email and password; the token is in the `set-auth-token` response header
         */
        suspend fun signInWithEmail(
            baseUrl: String,
            email: String,
            password: String,
        ): String {
            val body = http.json.encodeToString(EmailSignInRequest.serializer(), EmailSignInRequest(email.trim(), password))
            val response = http.send(clean(baseUrl), "POST", "api/auth/sign-in/email", body = body)
            when (response.code) {
                HTTP_UNAUTHORIZED -> throw RawkoonException.InvalidCredentials()
                HTTP_TOO_MANY -> throw RawkoonException.RateLimited()
            }
            response.bodyOrThrow()
            return response.headers["set-auth-token"]?.takeIf { it.isNotBlank() }
                ?: throw RawkoonException.InvalidResponse(IllegalStateException("No set-auth-token header"))
        }

        private fun clean(url: String) = url.withoutInvisible().trim()

        companion object {
            const val CLIENT_ID = "rawkoon-tv"
            const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
            private const val HTTP_BAD_REQUEST = 400
            private const val HTTP_UNAUTHORIZED = 401
            private const val HTTP_TOO_MANY = 429
        }
    }

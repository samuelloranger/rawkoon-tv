package cloud.rawkoon.tv.rawkoon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Why a Rawkoon call failed, each with its own message on screen. */
sealed class RawkoonException(cause: Throwable? = null) : Exception(cause) {
    class Unreachable(cause: Throwable? = null) : RawkoonException(cause)
    class InvalidUrl : RawkoonException()
    class Http(val code: Int) : RawkoonException()
    class InvalidResponse(cause: Throwable? = null) : RawkoonException(cause)
    class InvalidCredentials : RawkoonException()
    class RateLimited : RawkoonException()
    class NoJellyfin : RawkoonException()
    class NotLinked : RawkoonException()
    class QuickConnectExpired : RawkoonException()

    /** The token was refused: the Rawkoon session ended, so the profile has to link its account again. */
    class Unauthorized : RawkoonException()
}

/** A code the user approves on another device, then [deviceCode] is exchanged for a token. */
data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String?,
    val interval: Int,
) {
    /** ABCD1234 shown as ABCD-1234. */
    val displayCode: String
        get() = if (userCode.length == 8 && '-' !in userCode) "${userCode.take(4)}-${userCode.drop(4)}" else userCode
}

sealed interface DevicePoll {
    data object Pending : DevicePoll
    data object SlowDown : DevicePoll
    data object Expired : DevicePoll
    data object Denied : DevicePoll
    data class Granted(val token: String) : DevicePoll
}

/** What a signed-in device needs to set itself up. */
data class Bootstrap(
    val userId: String,
    val userName: String,
    val jellyfinUrl: String?,
    val jellyfinLinked: Boolean,
    val tmdbKey: String?,
)

/**
 * The Rawkoon calls the TV makes: device-code and email sign-in, the account's setup, and the
 * approval of a Jellyfin Quick Connect code. See RAWKOON.md for the protocol.
 */
class RawkoonClient(baseUrl: String) {
    private val base = baseUrl.filterNot { it.isISOControl() || it in INVISIBLE }.trim().trimEnd('/')

    suspend fun requestDeviceCode(): DeviceCode {
        val response = send("POST", "api/auth/device/code", JSONObject().put("client_id", CLIENT_ID))
        if (response.code == 429) throw RawkoonException.RateLimited()
        val json = response.okJson()
        return try {
            DeviceCode(
                deviceCode = json.getString("device_code"),
                userCode = json.getString("user_code"),
                verificationUri = json.getString("verification_uri"),
                verificationUriComplete = json.optString("verification_uri_complete").takeIf { it.isNotEmpty() },
                interval = json.optInt("interval", 5),
            )
        } catch (e: JSONException) {
            throw RawkoonException.InvalidResponse(e)
        }
    }

    suspend fun pollDeviceToken(deviceCode: String): DevicePoll {
        val body = JSONObject().put("grant_type", DEVICE_GRANT).put("device_code", deviceCode).put("client_id", CLIENT_ID)
        val response = send("POST", "api/auth/device/token", body)
        if (response.code in 200..299) return DevicePoll.Granted(response.json().optString("access_token").ifEmpty { throw RawkoonException.InvalidResponse() })
        // Rate limited: same as being told to slow down
        if (response.code == 429) return DevicePoll.SlowDown
        if (response.code != 400) throw RawkoonException.Http(response.code)
        return when (runCatching { JSONObject(response.body).optString("error") }.getOrDefault("")) {
            "authorization_pending" -> DevicePoll.Pending
            "slow_down" -> DevicePoll.SlowDown
            "expired_token" -> DevicePoll.Expired
            "access_denied" -> DevicePoll.Denied
            else -> throw RawkoonException.Http(response.code)
        }
    }

    /** The token comes back in the `set-auth-token` header. */
    suspend fun signInWithEmail(email: String, password: String): String {
        val response = send("POST", "api/auth/sign-in/email", JSONObject().put("email", email.trim()).put("password", password))
        when (response.code) {
            401 -> throw RawkoonException.InvalidCredentials()
            429 -> throw RawkoonException.RateLimited()
        }
        response.okJson()
        return response.headers["set-auth-token"]?.takeIf { it.isNotBlank() } ?: throw RawkoonException.InvalidResponse()
    }

    suspend fun bootstrap(token: String): Bootstrap {
        val json = send("GET", "api/tv/bootstrap", token = token).okJson()
        val user = json.optJSONObject("user")
        val jellyfin = json.optJSONObject("jellyfin")
        return Bootstrap(
            userId = user?.optString("id").orEmpty(),
            userName = user?.optString("name").orEmpty(),
            jellyfinUrl = jellyfin?.optString("url")?.takeIf { it.isNotEmpty() },
            jellyfinLinked = jellyfin?.optBoolean("linked") == true,
            tmdbKey = json.optJSONObject("tmdb")?.optString("key")?.takeIf { it.isNotEmpty() },
        )
    }

    /** Asks Rawkoon to approve a Jellyfin Quick Connect [code] for the account's linked user. */
    suspend fun approveQuickConnect(token: String, code: String) {
        val response = send("POST", "api/tv/jellyfin/quick-connect", JSONObject().put("code", code), token)
        when (response.code) {
            in 200..299 -> Unit
            403 -> throw RawkoonException.NotLinked()
            404 -> throw RawkoonException.QuickConnectExpired()
            503 -> throw RawkoonException.NoJellyfin()
            else -> throw RawkoonException.Http(response.code)
        }
    }

    /** One answer; [RawkoonDiscover] reads its own calls through the same [send]. */
    internal class Response(val code: Int, val body: String, val headers: Map<String, String>) {
        fun json(): JSONObject = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw RawkoonException.InvalidResponse(e)
        }

        fun okJson(): JSONObject = when (code) {
            in 200..299 -> json()
            401 -> throw RawkoonException.Unauthorized()
            else -> throw RawkoonException.Http(code)
        }
    }

    internal suspend fun send(method: String, path: String, body: JSONObject? = null, token: String? = null): Response =
        withContext(Dispatchers.IO) {
            val url = try {
                URL("$base/$path").also { require(it.protocol == "https" || it.protocol == "http") }
            } catch (e: Exception) {
                throw RawkoonException.InvalidUrl()
            }
            try {
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = method
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("Accept", "application/json")
                if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val code = connection.responseCode
                val stream = if (code >= 400) connection.errorStream else connection.inputStream
                val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                val headers = connection.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }
                    .mapValues { it.value.firstOrNull().orEmpty() }
                connection.disconnect()
                Response(code, text, headers)
            } catch (e: IOException) {
                throw RawkoonException.Unreachable(e)
            }
        }

    companion object {
        const val CLIENT_ID = "rawkoon-tv"
        const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
        private const val TIMEOUT_MS = 10_000
        private val INVISIBLE = setOf('\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF')
    }
}

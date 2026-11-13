package cloud.rawkoon.tv.rawkoon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val INVISIBLE = setOf('\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF')

/** A typed server address without control or zero-width characters, whitespace and a trailing slash. */
fun cleanServerUrl(raw: String) = raw.filterNot { it.isISOControl() || it in INVISIBLE }.trim().trimEnd('/')

/**
 * The Rawkoon calls the TV makes: device-code and email sign-in, the account's setup, and the
 * approval of a Jellyfin Quick Connect code.
 */
class RawkoonClient(baseUrl: String) {
    private val base = cleanServerUrl(baseUrl)

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
        return devicePollOf(response.body) ?: throw RawkoonException.Http(response.code)
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
        )
    }

    /** Whether the server makes and serves preview clips. */
    suspend fun clipsEnabled(token: String): Boolean = send("GET", "api/clips/status", token = token).okJson().optBoolean("enabled")

    /** Asks Rawkoon to approve a Jellyfin Quick Connect [code] for the account's linked user. */
    suspend fun approveQuickConnect(token: String, code: String) {
        val response = send("POST", "api/tv/jellyfin/quick-connect", JSONObject().put("code", code), token)
        quickConnectFailure(response.code, response.body)?.let { throw it }
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
        // HttpURLConnection refuses PATCH; OkHttp, already in the app for Jellyfin, sends it
        if (method == "PATCH") sendWithOkHttp(method, path, body, token) else withContext(Dispatchers.IO) {
            val url = try {
                URL("$base/$path").also { require(it.protocol == "https" || it.protocol == "http") }
            } catch (e: Exception) {
                throw RawkoonException.InvalidUrl()
            }
            var connection: HttpURLConnection? = null
            try {
                connection = url.openConnection() as HttpURLConnection
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
                if (redirectsToHttps(url.protocol, code, connection.getHeaderField("Location"))) {
                    connection.disconnect()
                    throw RawkoonException.UseHttps()
                }
                val stream = if (code >= 400) connection.errorStream else connection.inputStream
                val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                val headers = connection.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }
                    .mapValues { it.value.firstOrNull().orEmpty() }
                connection.disconnect()
                Response(code, text, headers)
            } catch (e: IOException) {
                connection?.disconnect()
                throw RawkoonException.Unreachable(e)
            }
        }

    private suspend fun sendWithOkHttp(method: String, path: String, body: JSONObject?, token: String?): Response = withContext(Dispatchers.IO) {
        val url = "$base/$path".toHttpUrlOrNull() ?: throw RawkoonException.InvalidUrl()
        val request = Request.Builder().url(url).header("Accept", "application/json")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .method(method, (body ?: JSONObject()).toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            okHttp.newCall(request).execute().use { response ->
                if (redirectsToHttps(url.scheme, response.code, response.header("Location"))) throw RawkoonException.UseHttps()
                Response(response.code, response.body.string(), response.headers.toMultimap().mapValues { it.value.firstOrNull().orEmpty() })
            }
        } catch (e: IOException) {
            throw RawkoonException.Unreachable(e)
        }
    }

    companion object {
        /**
         * Redirects are not followed, like HttpURLConnection across protocols: a 301 would turn the
         * PATCH into a GET without its body. The longer read covers a save that waits on Jellyfin.
         */
        private val okHttp by lazy {
            OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(PATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .callTimeout(PATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()
        }

        /** True for an http:// request answered with a redirect to https://: HttpURLConnection never follows that across protocols. */
        internal fun redirectsToHttps(protocol: String, code: Int, location: String?): Boolean =
            protocol == "http" && code in setOf(301, 302, 303, 307, 308) && location?.startsWith("https://", ignoreCase = true) == true

        /**
         * Why a Quick Connect approval failed, or null when it went through. A server too old to
         * have the route answers with its catch-all "Not found" (or a bare page behind a proxy).
         */
        internal fun quickConnectFailure(code: Int, body: String): RawkoonException? = when (code) {
            in 200..299 -> null
            401 -> RawkoonException.Unauthorized()
            403 -> RawkoonException.NotLinked()
            404 -> when (runCatching { JSONObject(body).optString("error") }.getOrDefault("")) {
                "", "Not found" -> RawkoonException.ServerTooOld()
                else -> RawkoonException.QuickConnectExpired()
            }
            502 -> RawkoonException.JellyfinUnreachable()
            503 -> RawkoonException.NoJellyfin()
            else -> RawkoonException.Http(code)
        }

        /** The answer a device-token 400 body stands for, or null for an error the flow can't act on. */
        internal fun devicePollOf(body: String): DevicePoll? {
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
            return when (json.optString("error")) {
                "authorization_pending" -> DevicePoll.Pending
                "slow_down" -> DevicePoll.SlowDown
                "expired_token" -> DevicePoll.Expired
                // A code already used or deleted needs a fresh one; a refused client id never gets better
                "invalid_grant" -> if (json.optString("error_description") == INVALID_DEVICE_CODE) DevicePoll.Expired else null
                "access_denied" -> DevicePoll.Denied
                else -> null
            }
        }

        /** better-auth's description for a device code it no longer knows. */
        private const val INVALID_DEVICE_CODE = "Invalid device code"

        const val CLIENT_ID = "rawkoon-tv"
        const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
        private const val TIMEOUT_MS = 10_000

        /** Under the artwork picker's 30 s wait, so a slow save still leaves it time to fall back. */
        private const val PATCH_TIMEOUT_MS = 20_000L
    }
}

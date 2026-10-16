package com.github.damontecres.wholphin.rawkoon

import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class RawResponse(
    val code: Int,
    val body: String,
    /** Header names in lower case */
    val headers: Map<String, String> = emptyMap(),
) {
    fun bodyOrThrow(): String = if (code in 200..299) body else throw RawkoonException.Http(code)
}

/**
 * One HTTP call to a Rawkoon server: base URL (with sub path), optional bearer token, typed network errors
 */
@Singleton
class RawkoonHttp
    @Inject
    constructor(
        @StandardOkHttpClient okHttpClient: OkHttpClient,
    ) {
        private val client = okHttpClient.newBuilder().callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build()

        val json = Json { ignoreUnknownKeys = true }

        suspend fun send(
            baseUrl: String,
            method: String,
            path: String,
            query: Map<String, String> = emptyMap(),
            body: String? = null,
            token: String? = null,
        ): RawResponse {
            val url = buildUrl(baseUrl, path, query) ?: throw RawkoonException.InvalidSettings()
            val request =
                try {
                    Request
                        .Builder()
                        .url(url)
                        .header("Accept", "application/json")
                        .apply { if (token != null) header("Authorization", "Bearer $token") }
                        .method(method, body?.toRequestBody(JSON_MEDIA_TYPE))
                        .build()
                } catch (e: IllegalArgumentException) {
                    throw RawkoonException.InvalidSettings(e)
                }
            return withContext(WholphinDispatchers.IO) {
                try {
                    client.newCall(request).execute().use { response ->
                        RawResponse(
                            response.code,
                            response.body.string(),
                            response.headers
                                .toMultimap()
                                .mapKeys { it.key.lowercase() }
                                .mapValues { it.value.first() },
                        )
                    }
                } catch (e: IOException) {
                    throw RawkoonException.Unreachable(e)
                }
            }
        }

        inline fun <reified T> decode(text: String): T =
            try {
                json.decodeFromString<T>(text)
            } catch (e: IllegalArgumentException) {
                throw RawkoonException.InvalidResponse(e)
            }

        companion object {
            const val TIMEOUT_SECONDS = 10L
            private val JSON_MEDIA_TYPE = "application/json".toMediaType()

            fun buildUrl(
                base: String,
                path: String,
                query: Map<String, String>,
            ): HttpUrl? =
                base
                    .trim()
                    .trimEnd('/')
                    .toHttpUrlOrNull()
                    ?.newBuilder()
                    ?.addPathSegments(path.trimStart('/'))
                    ?.apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }
                    ?.build()
        }
    }

private val INVISIBLE = setOf('\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF')

/**
 * Drop control characters (newlines, tabs) and zero-width characters
 */
internal fun String.withoutInvisible(): String = filterNot { it.isISOControl() || it in INVISIBLE }

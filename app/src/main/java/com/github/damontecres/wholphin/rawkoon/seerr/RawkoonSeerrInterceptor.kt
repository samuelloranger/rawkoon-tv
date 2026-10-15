package com.github.damontecres.wholphin.rawkoon.seerr

import com.github.damontecres.wholphin.api.seerr.infrastructure.Serializer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catches calls to the Seerr API of [HOST] and answers them from Rawkoon ([RawkoonSeerrBackend]); nothing is sent to the
 * network for them. Other requests pass through.
 */
@Singleton
class RawkoonSeerrInterceptor
    @Inject
    constructor(
        private val backend: RawkoonSeerrBackend,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (request.url.host != HOST) return chain.proceed(request)
            val path = request.url.encodedPath.removePrefix(API_PREFIX)
            val query = request.url.queryParameterNames.associateWith { request.url.queryParameter(it).orEmpty() }
            val body = request.body?.let { Buffer().also { buffer -> it.writeTo(buffer) }.readUtf8() }
            // The Seerr client already runs its calls on an IO thread
            val reply = runBlocking { backend.handle(request.method, path, query, body) }
            return Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(reply.code)
                .message(if (reply.code in 200..299) "OK" else "Error")
                .body(encode(reply.body).toResponseBody(JSON))
                .build()
        }

        private fun encode(body: JsonElement) = Serializer.kotlinxSerializationJson.encodeToString(JsonElement.serializer(), body)

        companion object {
            /** A reserved name that never resolves: the Seerr "server" the app is pointed at when Rawkoon provides discovery */
            const val HOST = "rawkoon.seerr.invalid"
            const val BASE_URL = "https://$HOST"
            private const val API_PREFIX = "/api/v1"
            private val JSON = "application/json".toMediaType()
        }
    }

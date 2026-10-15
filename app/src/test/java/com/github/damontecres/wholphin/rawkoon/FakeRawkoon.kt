package com.github.damontecres.wholphin.rawkoon

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException

/**
 * Routes are matched on "METHOD path-suffix" or just "path-suffix" (any method). A route lists its responses, served in
 * order, the last one repeating.
 */
class FakeRawkoon(
    private val routes: Map<String, List<Pair<Int, String>>>,
    private val failWith: IOException? = null,
    private val headers: Map<String, String> = emptyMap(),
) : Interceptor {
    val requests = mutableListOf<Request>()
    val bodies = mutableListOf<String?>()
    private val served = mutableMapOf<String, Int>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        requests += request
        bodies += request.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }
        failWith?.let { throw it }
        val path = request.url.encodedPath
        val route =
            routes.entries.firstOrNull { (k, _) ->
                val parts = k.split(" ", limit = 2)
                if (parts.size == 2) request.method == parts[0] && path.endsWith(parts[1]) else path.endsWith(k)
            }
        val (code, body) =
            route?.let { (key, responses) ->
                val index = served.getOrDefault(key, 0)
                served[key] = index + 1
                responses[minOf(index, responses.lastIndex)]
            } ?: (404 to "")
        return Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }
}

package cloud.rawkoon.tv.jellyfin

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jellyfin.sdk.api.okhttp.OkHttpFactory

/**
 * Reads each response's body in full where OkHttp received it, on its own thread. The SDK reads
 * the body after resuming in the calling coroutine, which is the main thread for most pages: a body
 * still on its way (plain HTTP, a response longer than a packet) would then be read from the
 * network there, and Android refuses that with NetworkOnMainThreadException.
 */
object BufferedBodies : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        // A WebSocket upgrade keeps its stream open for the socket; a HEAD response has no body
        if (response.code == SWITCHING_PROTOCOLS || chain.request().method == "HEAD") return response
        val body = response.body
        return response.newBuilder().body(body.bytes().toResponseBody(body.contentType())).build()
    }

    /** The SDK's HTTP and WebSocket clients, built on a client that buffers bodies. */
    val factory: OkHttpFactory by lazy { OkHttpFactory(OkHttpClient.Builder().addInterceptor(BufferedBodies).build()) }

    private const val SWITCHING_PROTOCOLS = 101
}

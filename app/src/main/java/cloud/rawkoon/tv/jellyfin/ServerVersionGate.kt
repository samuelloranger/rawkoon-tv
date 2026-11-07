package cloud.rawkoon.tv.jellyfin

import android.util.Log
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.model.ServerVersion

/** The server is a Jellyfin older than [ServerVersionGate.MINIMUM], which this app's SDK no longer supports. */
class JellyfinTooOldException(val version: ServerVersion) : Exception("Jellyfin $version is older than ${ServerVersionGate.MINIMUM}")

/** The one place that says which Jellyfin servers the app works with. */
object ServerVersionGate {
    /** The oldest Jellyfin server the app supports. */
    val MINIMUM = ServerVersion(12, 0, 0)

    /** True only for a version that parses and is below [MINIMUM]; a missing or unreadable one never blocks. */
    fun isTooOld(raw: String?): Boolean = parse(raw)?.let { it < MINIMUM } ?: false

    fun parse(raw: String?): ServerVersion? = raw?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { ServerVersion.fromString(it) }.getOrNull() }

    /** Throws [JellyfinTooOldException] when [raw] is below [MINIMUM]. */
    fun require(raw: String?) {
        val version = parse(raw)
        if (version == null) {
            Log.w(TAG, "Server version unknown or unreadable, not blocking")
            return
        }
        if (version < MINIMUM) throw JellyfinTooOldException(version)
    }

    /** Reads the server's public version and applies [require]. Other failures are left to the caller's own calls. */
    suspend fun check(api: ApiClient) = require(api.systemApi.getPublicSystemInfo().content.version)

    private const val TAG = "ServerVersionGate"
}

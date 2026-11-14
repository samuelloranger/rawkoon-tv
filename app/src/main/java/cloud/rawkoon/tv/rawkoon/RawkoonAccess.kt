package cloud.rawkoon.tv.rawkoon

import android.content.Context
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.data.Profile
import java.util.Locale

/** The token and server address a profile's Rawkoon calls need, or null when it lacks either. */
private fun Profile.rawkoonAccess(context: Context): Pair<String, String>? {
    val token = rawkoonToken ?: return null
    val url = rawkoonUrl ?: Prefs.rawkoonUrl(context) ?: return null
    return token to url
}

/** Whether [rawkoonDiscover] has something to call: a Rawkoon session and a server address to make its calls to. */
fun Profile.hasRawkoonSession(context: Context): Boolean = rawkoonAccess(context) != null

/**
 * The profile's Rawkoon calls, or null when it has no Rawkoon session (a profile imported from
 * the first RawkoonTV, or never linked) or no server address to make them to.
 */
fun Profile.rawkoonDiscover(context: Context): RawkoonDiscover? =
    rawkoonAccess(context)?.let { (token, url) -> RawkoonDiscover(RawkoonClient(url), token, Locale.getDefault().toLanguageTag()) }

/** The profile's shelves, read through Rawkoon with its saved copy as the fallback; null without a session. */
fun Profile.shelfSource(context: Context): ShelfSource? =
    rawkoonDiscover(context)?.let { ShelfSource(it, ShelfCache(context, id)) }

/** Where a profile's Rawkoon streams are read from: its server and the token that session holds. */
data class RawkoonEndpoint(val profileId: String, val baseUrl: String, val token: String)

fun Profile.rawkoonEndpoint(context: Context): RawkoonEndpoint? =
    rawkoonAccess(context)?.let { (token, url) -> RawkoonEndpoint(id, cleanServerUrl(url), token) }

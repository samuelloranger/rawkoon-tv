package cloud.rawkoon.tv.rawkoon

import android.content.Context
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.data.Profile
import java.util.Locale

private fun Profile.serverUrl(context: Context): String? = rawkoonUrl ?: Prefs.rawkoonUrl(context)

/** Whether [rawkoonDiscover] has something to call: a Rawkoon session and a server address to make its calls to. */
fun Profile.hasRawkoonSession(context: Context): Boolean = rawkoonToken != null && serverUrl(context) != null

/**
 * The profile's Rawkoon calls, or null when it has no Rawkoon session (a profile imported from
 * the first RawkoonTV, or never linked) or no server address to make them to.
 */
fun Profile.rawkoonDiscover(context: Context): RawkoonDiscover? {
    val token = rawkoonToken ?: return null
    val url = serverUrl(context) ?: return null
    return RawkoonDiscover(RawkoonClient(url), token, Locale.getDefault().toLanguageTag())
}

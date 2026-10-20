package cloud.rawkoon.tv.rawkoon

import android.content.Context
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.data.Profile
import java.util.Locale

/**
 * The profile's Rawkoon calls, or null when it has no Rawkoon session (a profile imported from
 * the first RawkoonTV, or never linked) or no server address to make them to.
 */
fun Profile.rawkoonDiscover(context: Context): RawkoonDiscover? {
    val token = rawkoonToken ?: return null
    val url = rawkoonUrl ?: Prefs.rawkoonUrl(context) ?: return null
    return RawkoonDiscover(RawkoonClient(url), token, Locale.getDefault().toLanguageTag())
}

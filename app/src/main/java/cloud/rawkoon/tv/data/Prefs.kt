package cloud.rawkoon.tv.data

import android.content.Context
import android.content.SharedPreferences

/** The one preferences file: app-wide settings that don't belong to a profile. */
object Prefs {
    private const val NAME = "rawkoon"
    private const val RAWKOON_URL = "rawkoon:url"
    const val LANGUAGE = "language"

    fun of(context: Context): SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** The Rawkoon server new profiles sign in to; asked for once, on the first sign-in. */
    fun rawkoonUrl(context: Context): String? = of(context).getString(RAWKOON_URL, null)

    fun setRawkoonUrl(context: Context, url: String) = of(context).edit().putString(RAWKOON_URL, url.trim().trimEnd('/')).apply()

    /** "system", "fr" or "en". */
    fun language(context: Context): String = of(context).getString(LANGUAGE, "system") ?: "system"
}

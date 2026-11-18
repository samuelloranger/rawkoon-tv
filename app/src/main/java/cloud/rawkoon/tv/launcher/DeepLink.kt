package cloud.rawkoon.tv.launcher

import android.content.Intent
import cloud.rawkoon.tv.ui.Destination
import java.net.URI
import java.net.URLDecoder

/**
 * Where an intent opens the app: a launcher card's title, or a `rawkoon://` link such as
 * `rawkoon://movies`, `rawkoon://shelves/12` or `rawkoon://item/<id>?kind=movie`. A link opens in
 * its `?profile=`, or else in the profile used last.
 */
sealed interface DeepLink {
    /** Null for the profile used last. */
    val profileId: String?

    /** A movie's, show's or episode's page. */
    data class Title(override val profileId: String?, val card: LauncherLink) : DeepLink

    /** A rail page; [Destination.HOME] is the profile's home itself. */
    data class Page(override val profileId: String?, val destination: Destination) : DeepLink

    /** One Rawkoon shelf. */
    data class Shelf(override val profileId: String?, val shelfId: Int) : DeepLink

    companion object {
        fun from(intent: Intent?): DeepLink? {
            LauncherLink.from(intent)?.let { return Title(it.profileId, it) }
            return intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString?.let(::parse)
        }

        fun parse(url: String): DeepLink? {
            val uri = runCatching { URI(url) }.getOrNull()?.takeIf { it.scheme.equals("rawkoon", ignoreCase = true) } ?: return null
            val query = uri.rawQuery.orEmpty().split('&').mapNotNull { pair ->
                pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { (k, v) -> k to URLDecoder.decode(v, "UTF-8") }
            }.toMap()
            // MainActivity is exported: a profile that doesn't look like an id is refused, not ignored
            val profile = query["profile"]?.let { it.takeIf(LauncherLink::isId) ?: return null }
            // The authority, not the host: a host with an underscore parses to no host at all
            val segments = (listOfNotNull(uri.rawAuthority) + uri.path.orEmpty().split('/')).filter { it.isNotEmpty() }
            val path = segments.map { it.lowercase() }
            return when {
                path.size == 2 && path[0] == "item" -> title(profile, segments[1], query)
                path.size == 2 && path[0] == "shelves" -> segments[1].toIntOrNull()?.let { Shelf(profile, it) }
                path.size == 3 && path[0] == "library" && path[1] == "shelves" -> segments[2].toIntOrNull()?.let { Shelf(profile, it) }
                else -> PAGES[path.joinToString("/")]?.let { Page(profile, it) }
            }
        }

        private fun title(profile: String?, itemId: String, query: Map<String, String>): Title? {
            val card = LauncherLink.parse { key ->
                when (key) {
                    // The card's own profile is unused: the link's profile is resolved before it opens
                    LauncherLink.PROFILE -> profile ?: NO_PROFILE
                    LauncherLink.ITEM -> itemId
                    LauncherLink.KIND -> query["kind"]?.uppercase()
                    LauncherLink.SERIES -> query["series"]
                    LauncherLink.SEASON -> query["season"]
                    else -> null
                }
            } ?: return null
            return Title(profile, card)
        }

        private const val NO_PROFILE = "00000000000000000000000000000000"

        /** The rail pages by name, with the web app's paths for the same pages. */
        private val PAGES = mapOf(
            "" to Destination.HOME,
            "home" to Destination.HOME,
            "search" to Destination.SEARCH,
            "favorites" to Destination.FAVORITES,
            "discover" to Destination.DISCOVER,
            "find" to Destination.DISCOVER,
            "movies" to Destination.MOVIES,
            "library" to Destination.MOVIES,
            "shows" to Destination.SHOWS,
            "collections" to Destination.COLLECTIONS,
            "find/collections" to Destination.COLLECTIONS,
            "shelves" to Destination.SHELVES,
            "library/shelves" to Destination.SHELVES,
            "settings" to Destination.SETTINGS,
        )
    }
}

package cloud.rawkoon.tv.screens

import cloud.rawkoon.tv.jellyfin.JellyfinTmdbLookup
import cloud.rawkoon.tv.jellyfin.MediaItem
import kotlinx.coroutines.launch

/**
 * Opens a TMDB title's page from [screen]. With `preferLibrary`, a title Rawkoon has in its
 * library opens its Jellyfin page when Jellyfin has it (looked up by TMDB id), and falls back to the
 * TMDB page. A second press while that lookup is under way is ignored, so a page is pushed once.
 */
class TitleOpener(private val screen: RailScreen) {
    private var looking = false

    fun open(item: MediaItem, preferLibrary: Boolean = false) {
        val tmdbId = item.tmdb?.id ?: return
        val activity = screen.activity
        val session = screen.session
        if (!preferLibrary) {
            activity.push(DiscoverDetailsScreen(activity, session, item))
            return
        }
        if (looking) return
        looking = true
        screen.scope.launch {
            try {
                val found = JellyfinTmdbLookup(session).find(tmdbId, item.title, item.year, item.kind == MediaItem.Kind.SERIES)
                activity.push(if (found != null) DetailsScreen(activity, session, found) else DiscoverDetailsScreen(activity, session, item))
            } finally {
                looking = false
            }
        }
    }
}

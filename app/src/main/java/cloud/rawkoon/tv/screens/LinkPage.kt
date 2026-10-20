package cloud.rawkoon.tv.screens

import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.Screen
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.launcher.LauncherLink

/** The page a launcher card opens: a movie's or show's, or an episode's season with the episode focused. */
fun linkPage(activity: MainActivity, session: JellyfinSession, link: LauncherLink): Screen =
    if (link.kind == MediaItem.Kind.EPISODE) {
        SeasonScreen(activity, session, requireNotNull(link.seriesId), link.seasonId, link.itemId)
    } else {
        DetailsScreen(activity, session, link.placeholder())
    }

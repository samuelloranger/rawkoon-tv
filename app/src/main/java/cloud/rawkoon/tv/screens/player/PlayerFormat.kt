package cloud.rawkoon.tv.screens.player

import cloud.rawkoon.tv.jellyfin.MediaItem
import java.util.Locale

/** True for an episode, which has a next one and a show to read it from. */
internal val MediaItem.isEpisode get() = kind == MediaItem.Kind.EPISODE

/** Minutes and seconds, with hours when there are any. */
internal fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}

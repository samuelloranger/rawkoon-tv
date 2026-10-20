package cloud.rawkoon.tv.launcher

import android.content.Context
import android.content.Intent
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.jellyfin.MediaItem

/**
 * What a launcher card opens: an item in the profile it was published for. It travels as extras on
 * an explicit intent to [MainActivity], which the provider stores as a URI.
 */
data class LauncherLink(
    val profileId: String,
    val itemId: String,
    val kind: MediaItem.Kind,
    val seriesId: String?,
    val seasonId: String?,
    /** Shown on the page until it has loaded. */
    val title: String?,
    val posterUrl: String?,
) {
    fun intent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .putExtra(PROFILE, profileId).putExtra(ITEM, itemId).putExtra(KIND, kind.name)
        .putExtra(SERIES, seriesId).putExtra(SEASON, seasonId).putExtra(TITLE, title).putExtra(POSTER, posterUrl)

    /** The item as far as the link knows it, enough for its page to open while the rest loads. */
    fun placeholder() = MediaItem(
        id = itemId, kind = kind, title = title.orEmpty(), seriesName = null, episodeLabel = null, overview = "", year = null,
        runtimeMinutes = null, officialRating = null, communityRating = null, posterUrl = posterUrl, thumbUrl = null,
        backdropUrl = null, logoUrl = null, progress = null, played = false, seriesId = seriesId, seasonId = seasonId,
    )

    companion object {
        const val PROFILE = "cloud.rawkoon.tv.extra.PROFILE"
        const val ITEM = "cloud.rawkoon.tv.extra.ITEM"
        const val KIND = "cloud.rawkoon.tv.extra.KIND"
        const val SERIES = "cloud.rawkoon.tv.extra.SERIES"
        const val SEASON = "cloud.rawkoon.tv.extra.SEASON"
        const val TITLE = "cloud.rawkoon.tv.extra.TITLE"
        const val POSTER = "cloud.rawkoon.tv.extra.POSTER"

        /** Null for an item a page can't be opened for: an episode needs its show. */
        fun from(item: MediaItem, profileId: String): LauncherLink? {
            if (item.kind == MediaItem.Kind.EPISODE && item.seriesId == null) return null
            if (item.kind !in OPENABLE) return null
            return LauncherLink(profileId, item.id, item.kind, item.seriesId, item.seasonId, item.headerTitle, item.posterUrl)
        }

        fun from(intent: Intent?): LauncherLink? = intent?.let { parse { key -> it.getStringExtra(key) } }

        fun parse(extra: (String) -> String?): LauncherLink? {
            val profile = extra(PROFILE) ?: return null
            val item = extra(ITEM) ?: return null
            val kind = runCatching { MediaItem.Kind.valueOf(extra(KIND).orEmpty()) }.getOrNull()?.takeIf { it in OPENABLE } ?: return null
            val series = extra(SERIES)
            if (kind == MediaItem.Kind.EPISODE && series == null) return null
            return LauncherLink(profile, item, kind, series, extra(SEASON), extra(TITLE), extra(POSTER))
        }

        private val OPENABLE = setOf(MediaItem.Kind.MOVIE, MediaItem.Kind.SERIES, MediaItem.Kind.EPISODE)
    }
}

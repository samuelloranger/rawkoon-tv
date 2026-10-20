package cloud.rawkoon.tv.launcher

import android.content.Context
import android.content.Intent
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.data.Profile
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

    /** The link with what an outside sender could forge dropped: the poster is only used from the profile's own server. */
    fun trusted(profile: Profile): LauncherLink {
        val base = profile.jellyfinUrl.trim().trimEnd('/') + "/"
        return copy(posterUrl = posterUrl?.takeIf { it.startsWith(base, ignoreCase = true) })
    }

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
            // MainActivity is exported: any app can send these extras, so ids must look like ids
            val profile = extra(PROFILE)?.takeIf(::isId) ?: return null
            val item = extra(ITEM)?.takeIf(::isId) ?: return null
            val kind = runCatching { MediaItem.Kind.valueOf(extra(KIND).orEmpty()) }.getOrNull()?.takeIf { it in OPENABLE } ?: return null
            val series = extra(SERIES)?.takeIf(::isId)
            if (kind == MediaItem.Kind.EPISODE && series == null) return null
            return LauncherLink(profile, item, kind, series, extra(SEASON)?.takeIf(::isId), extra(TITLE), extra(POSTER))
        }

        /** 32 hex digits with optional dashes: a Jellyfin id or a profile id. */
        fun isId(text: String) = text.length in 32..36 && text.all { it == '-' || it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } &&
            text.count { it != '-' } == 32

        private val OPENABLE = setOf(MediaItem.Kind.MOVIE, MediaItem.Kind.SERIES, MediaItem.Kind.EPISODE)
    }
}

package cloud.rawkoon.tv.launcher

import android.media.tv.TvContract.PreviewPrograms
import android.media.tv.TvContract.WatchNextPrograms
import cloud.rawkoon.tv.jellyfin.HomeRow
import cloud.rawkoon.tv.jellyfin.MediaItem

/** What the launcher's rows show, taken from the home rows already loaded: no query of its own. */
data class LauncherFeed(val watchNext: List<MediaItem>, val recent: List<MediaItem>) {
    companion object {
        const val WATCH_NEXT_LIMIT = 10
        const val RECENT_LIMIT = 20

        /**
         * Resume then next up (a show already being resumed is not offered twice), and the latest
         * additions of every library taken in turn, so one busy library doesn't fill the row.
         */
        fun from(rows: List<HomeRow>): LauncherFeed {
            val resume = rows.firstOrNull { it.id == "resume" }?.items.orEmpty().filter(::playable).take(WATCH_NEXT_LIMIT)
            val resumedShows = resume.mapNotNull { it.seriesId }.toSet()
            val nextUp = rows.firstOrNull { it.id == "nextup" }?.items.orEmpty()
                .filter { playable(it) && it.seriesId !in resumedShows }
                .take(WATCH_NEXT_LIMIT)
            val latest = rows.filter { it.id.startsWith("latest:") }.map { row -> row.items.filter { it.kind in SHOWN } }
            val recent = (0 until (latest.maxOfOrNull { it.size } ?: 0)).flatMap { i -> latest.mapNotNull { it.getOrNull(i) } }
            return LauncherFeed((resume + nextUp).distinctBy { it.id }, recent.distinctBy { it.id }.take(RECENT_LIMIT))
        }

        private val SHOWN = setOf(MediaItem.Kind.MOVIE, MediaItem.Kind.SERIES, MediaItem.Kind.EPISODE)

        private fun playable(item: MediaItem) = item.kind == MediaItem.Kind.MOVIE || item.kind == MediaItem.Kind.EPISODE
    }
}

/**
 * One launcher card, as the TV provider stores it. Movies and shows use their poster (2:3), an
 * episode its still (16:9): [aspect] always describes [artUrl], so a card never stretches an image.
 */
data class ProgramSpec(
    val itemId: String,
    val type: Int,
    val title: String,
    val episodeTitle: String?,
    val description: String,
    val artUrl: String,
    val aspect: Int,
    val durationMs: Int?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val positionMs: Int,
    val link: LauncherLink,
) {
    /** Watch Next's "continue" needs a known length and a spot inside it; anything else is "next". */
    val resumable: Boolean get() = positionMs >= RESUME_MIN_MS && durationMs != null && positionMs < durationMs

    companion object {
        /** Under two minutes in, a title is offered as up next rather than as one to continue. */
        const val RESUME_MIN_MS = 120_000
        const val POSTER_WIDTH = 342
        const val STILL_WIDTH = 640

        /** Null for what a launcher can't show: another kind of item, or one with no picture. */
        fun from(item: MediaItem, profileId: String): ProgramSpec? {
            val type = when (item.kind) {
                MediaItem.Kind.MOVIE -> PreviewPrograms.TYPE_MOVIE
                MediaItem.Kind.SERIES -> PreviewPrograms.TYPE_TV_SERIES
                MediaItem.Kind.EPISODE -> PreviewPrograms.TYPE_TV_EPISODE
                else -> return null
            }
            val link = LauncherLink.from(item, profileId) ?: return null
            val (url, aspect) = art(item) ?: return null
            val episode = item.kind == MediaItem.Kind.EPISODE
            return ProgramSpec(
                itemId = item.id,
                type = type,
                title = if (episode) item.seriesName ?: item.title else item.title,
                episodeTitle = item.title.takeIf { episode },
                description = item.overview,
                artUrl = url,
                aspect = aspect,
                durationMs = item.runtimeMinutes?.takeIf { it > 0 }?.let { it * 60_000 },
                seasonNumber = item.seasonNumber.takeIf { episode },
                episodeNumber = item.episodeNumber.takeIf { episode },
                positionMs = item.resumeMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                link = link,
            )
        }

        /** The picture and its aspect: the poster for movies and shows, the still for an episode, the other one when it is missing. */
        fun art(item: MediaItem): Pair<String, Int>? {
            val poster = item.posterUrl?.let { sized(it, POSTER_WIDTH) to PreviewPrograms.ASPECT_RATIO_2_3 }
            val still = item.thumbUrl?.let { sized(it, STILL_WIDTH) to PreviewPrograms.ASPECT_RATIO_16_9 }
            return if (item.kind == MediaItem.Kind.EPISODE) still ?: poster else poster ?: still
        }

        /** The server scales the image down, so the launcher doesn't download a full-size poster per card. */
        fun sized(url: String, width: Int) = url + (if ('?' in url) "&" else "?") + "maxWidth=$width"
    }
}

/** A Watch Next row already in the provider. */
data class WatchNextRow(val rowId: Long, val itemId: String?, val browsable: Boolean)

/**
 * What a Watch Next sync does: [remove] rows (gone from the server, duplicates), [update] the
 * ones kept (progress and picture move on) and [add] the new ones. A row the user dismissed on the
 * launcher stays in the provider, hidden, for as long as the item is still wanted, so it isn't put back.
 */
data class WatchNextDiff(val remove: List<Long>, val update: Map<Long, String>, val add: List<String>) {
    companion object {
        fun of(existing: List<WatchNextRow>, wanted: List<String>): WatchNextDiff {
            val remove = ArrayList<Long>()
            val update = LinkedHashMap<Long, String>()
            val known = HashSet<String>()
            for ((itemId, rows) in existing.groupBy { it.itemId }) {
                if (itemId == null || itemId !in wanted) {
                    remove += rows.map { it.rowId }
                    continue
                }
                known += itemId
                // A dismissed row wins over duplicates: the user doesn't want it
                val keep = rows.firstOrNull { !it.browsable } ?: rows.first()
                remove += rows.filter { it !== keep }.map { it.rowId }
                if (keep.browsable) update[keep.rowId] = itemId
            }
            return WatchNextDiff(remove, update, wanted.filter { it !in known })
        }
    }
}

/** When the launcher rows are worth publishing again: a new profile at once, the same one after a few minutes. */
object LauncherSchedule {
    const val MIN_GAP_MS = 5 * 60_000L

    fun due(lastAt: Long, lastProfile: String?, now: Long, profile: String) =
        lastProfile != profile || now < lastAt || now - lastAt >= MIN_GAP_MS
}

/** Watch Next's type column for [spec]. */
fun ProgramSpec.watchNextType() = if (resumable) WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE else WatchNextPrograms.WATCH_NEXT_TYPE_NEXT

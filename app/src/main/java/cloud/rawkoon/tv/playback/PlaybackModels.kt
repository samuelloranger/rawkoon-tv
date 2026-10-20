package cloud.rawkoon.tv.playback

import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod

/** An audio or subtitle track of the file, as Jellyfin numbers it. */
data class Track(
    val index: Int,
    val title: String,
    val language: String?,
    val codec: String?,
    val external: Boolean,
    /** For subtitles: how the server delivers it (embedded in the file, a separate file, or burned in). */
    val delivery: SubtitleDeliveryMethod?,
    /** For a subtitle delivered as its own file. */
    val url: String?,
)

/** How to play one item: the URL, how it is delivered, and the tracks to choose from. */
data class Stream(
    val itemId: String,
    val mediaSourceId: String?,
    val playSessionId: String?,
    val url: String,
    val method: PlayMethod,
    val audio: List<Track>,
    val subtitles: List<Track>,
    val audioIndex: Int?,
    /** -1 when subtitles are off. */
    val subtitleIndex: Int,
    val durationMs: Long?,
)

/** A stretch of an episode worth skipping: intro, recap, credits, preview. */
data class Segment(val type: MediaSegmentType, val startMs: Long, val endMs: Long)

/** The thumbnails Jellyfin made along an item, in tiles of [columns] × [rows]. */
data class Trickplay(
    val width: Int,
    val height: Int,
    val columns: Int,
    val rows: Int,
    val count: Int,
    val intervalMs: Int,
    private val tileUrl: (Int) -> String,
) {
    /** The tile holding the thumbnail for [positionMs], and where in it that thumbnail sits. */
    fun locate(positionMs: Long): Triple<String, Int, Int> {
        val index = (positionMs / intervalMs).toInt().coerceIn(0, count - 1)
        val perTile = columns * rows
        val inTile = index % perTile
        return Triple(tileUrl(index / perTile), (inTile % columns) * width, (inTile / columns) * height)
    }
}

package cloud.rawkoon.tv.jellyfin

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import java.util.Locale
import java.util.UUID

/**
 * A library item as the UI uses it: text for the header and the image URLs the cards and the
 * backdrop need, picked once when the row loads (an episode borrows its show's poster, backdrop
 * and logo). Plain data, so views never touch the SDK.
 */
data class MediaItem(
    val id: String,
    val kind: Kind,
    val title: String,
    /** The show for an episode; null otherwise. */
    val seriesName: String?,
    /** "S1 · E3" for an episode. */
    val episodeLabel: String?,
    val overview: String,
    val year: Int?,
    val runtimeMinutes: Int?,
    val officialRating: String?,
    val communityRating: Float?,
    val posterUrl: String?,
    val thumbUrl: String?,
    val backdropUrl: String?,
    val logoUrl: String?,
    /** 0..1, or null when not started. */
    val progress: Float?,
    val played: Boolean,
) {
    enum class Kind { MOVIE, SERIES, SEASON, EPISODE, OTHER }

    /** What the header names: the show for an episode. */
    val headerTitle: String get() = seriesName ?: title

    /** "2026 · 1 h 37 min · CA-14A · ★ 7.3", or "S1 · E3 · Title · 52 min" for an episode. */
    fun metaLine(): String {
        val parts = mutableListOf<String>()
        if (kind == Kind.EPISODE) {
            episodeLabel?.let { parts += it }
            parts += title
        } else {
            year?.let { parts += it.toString() }
        }
        runtimeMinutes?.takeIf { it > 0 }?.let { parts += duration(it) }
        if (kind != Kind.EPISODE) {
            officialRating?.let { parts += it }
            communityRating?.let { parts += "★ " + String.format(Locale.ROOT, "%.1f", it) }
        }
        return parts.joinToString(" · ")
    }

    companion object {
        fun duration(minutes: Int) = if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"

        fun from(dto: BaseItemDto, images: ImageUrls): MediaItem {
            val kind = when (dto.type) {
                BaseItemKind.MOVIE -> Kind.MOVIE
                BaseItemKind.SERIES -> Kind.SERIES
                BaseItemKind.SEASON -> Kind.SEASON
                BaseItemKind.EPISODE -> Kind.EPISODE
                else -> Kind.OTHER
            }
            val tags = dto.imageTags.orEmpty()
            val own = { type: ImageType -> tags[type]?.let { images.item(dto.id, type, it) } }
            val backdrop = dto.backdropImageTags?.firstOrNull()?.let { images.item(dto.id, ImageType.BACKDROP, it) }
                ?: dto.parentBackdropItemId?.let { id ->
                    dto.parentBackdropImageTags?.firstOrNull()?.let { images.item(id, ImageType.BACKDROP, it) }
                }
            val seriesPoster = dto.seriesId?.let { id -> dto.seriesPrimaryImageTag?.let { images.item(id, ImageType.PRIMARY, it) } }
            val logo = own(ImageType.LOGO)
                ?: dto.parentLogoItemId?.let { id -> dto.parentLogoImageTag?.let { images.item(id, ImageType.LOGO, it) } }
            val parentThumb = dto.parentThumbItemId?.let { id -> dto.parentThumbImageTag?.let { images.item(id, ImageType.THUMB, it) } }
            val episode = kind == Kind.EPISODE
            val percent = dto.userData?.playedPercentage
            return MediaItem(
                id = dto.id.toString(),
                kind = kind,
                title = dto.name.orEmpty(),
                seriesName = if (episode) dto.seriesName else null,
                episodeLabel = if (episode) listOfNotNull(dto.parentIndexNumber?.let { "S$it" }, dto.indexNumber?.let { "E$it" })
                    .joinToString(" · ").ifEmpty { null } else null,
                overview = dto.overview.orEmpty(),
                year = dto.productionYear,
                runtimeMinutes = dto.runTimeTicks?.let { (it / 600_000_000L).toInt() },
                officialRating = dto.officialRating,
                communityRating = dto.communityRating,
                posterUrl = if (episode) seriesPoster ?: own(ImageType.PRIMARY) else own(ImageType.PRIMARY) ?: seriesPoster,
                // Landscape cards: an episode's still, else the item's thumb or backdrop
                thumbUrl = if (episode) own(ImageType.PRIMARY) ?: parentThumb ?: backdrop else own(ImageType.THUMB) ?: backdrop ?: parentThumb,
                backdropUrl = backdrop,
                logoUrl = logo,
                progress = percent?.takeIf { it > 0.0 }?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) },
                played = dto.userData?.played == true,
            )
        }
    }
}

/** Builds image URLs on one server. Tags make each URL change when the image does, so caches never go stale. */
class ImageUrls(private val base: String) {
    fun item(id: UUID, type: ImageType, tag: String) = "$base/Items/$id/Images/${type.serialName}?tag=$tag&quality=90"

    fun user(userId: String, tag: String) = "$base/Users/$userId/Images/Primary?tag=$tag&quality=90"
}

/** A titled row of items on the home screen. [landscape] rows show stills with progress. */
data class HomeRow(val id: String, val title: String, val items: List<MediaItem>, val landscape: Boolean)

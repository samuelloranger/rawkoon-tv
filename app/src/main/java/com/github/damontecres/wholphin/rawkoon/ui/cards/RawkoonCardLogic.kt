package com.github.damontecres.wholphin.rawkoon.ui.cards

import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonStatus

/** The pure decisions behind the Rawkoon item cards: which look to draw, and what the watched state maps to */
object RawkoonCardLogic {
    /** Cards narrower than this (width / height) are drawn with the poster look */
    private const val POSTER_MAX_RATIO = 0.8f

    /** Poster cards show their title in a panel over the image, every other shape shows it under the image */
    fun isPoster(aspectRatio: Float): Boolean = aspectRatio < POSTER_MAX_RATIO

    /**
     * The watched fraction (0 to 1) for the progress track, or null when the item is not in progress.
     * [percent] is Jellyfin's 0 to 100 value; anything outside the open range 0..100 is not in progress.
     */
    fun progress(percent: Double?): Float? =
        if (percent != null && percent > 0.0 && percent < 100.0) {
            (percent / 100.0).toFloat()
        } else {
            null
        }

    /** The status of a fully watched item, or null: only watched items get a status, as Jellyfin has no more */
    fun status(
        played: Boolean,
        percent: Double?,
    ): RawkoonStatus? = if (played && progress(percent) == null) RawkoonStatus.Watched else null

    /** The year pill text, or null when the year is missing or not a real year */
    fun yearText(year: Int?): String? = year?.takeIf { it > 0 }?.toString()

    /** What a Discover card says about a title: a [status], and whether it is only [partial]ly available */
    data class DiscoverBadge(
        val status: RawkoonStatus,
        val partial: Boolean = false,
    )

    /** Maps the request availability of a Discover title onto the Rawkoon status tones, or null for no badge */
    fun discoverBadge(availability: SeerrAvailability?): DiscoverBadge? =
        when (availability) {
            SeerrAvailability.PENDING -> DiscoverBadge(RawkoonStatus.Pending)

            SeerrAvailability.PROCESSING -> DiscoverBadge(RawkoonStatus.Approved)

            SeerrAvailability.PARTIALLY_AVAILABLE -> DiscoverBadge(RawkoonStatus.Downloaded, partial = true)

            SeerrAvailability.AVAILABLE -> DiscoverBadge(RawkoonStatus.Downloaded)

            SeerrAvailability.UNKNOWN,
            SeerrAvailability.BLOCKLISTED,
            SeerrAvailability.DELETED,
            null,
            -> null
        }
}

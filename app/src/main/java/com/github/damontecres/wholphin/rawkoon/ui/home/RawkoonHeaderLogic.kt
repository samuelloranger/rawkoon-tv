package com.github.damontecres.wholphin.rawkoon.ui.home

import androidx.annotation.StringRes
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonStatus
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.Locale

/** The pure parts of the Rawkoon home header: the type chip, genres, rating and status it shows for an item */
object RawkoonHeaderLogic {
    /** Most genre chips the header shows */
    const val MAX_GENRES = 3

    /** The label of the type chip, or null for kinds that get none */
    @StringRes
    fun typeLabelRes(kind: BaseItemKind?): Int? =
        when (kind) {
            BaseItemKind.MOVIE -> R.string.rawkoon_header_type_movie
            BaseItemKind.SERIES -> R.string.rawkoon_header_type_show
            BaseItemKind.SEASON -> R.string.rawkoon_header_type_season
            BaseItemKind.EPISODE -> R.string.rawkoon_header_type_episode
            else -> null
        }

    /** The community rating with one decimal ("7.6"), or null when there is none */
    fun ratingText(rating: Float?): String? = rating?.takeIf { it > 0f }?.let { String.format(Locale.US, "%.1f", it) }

    /** The first [MAX_GENRES] non-blank genres */
    fun genres(genres: List<String>?): List<String> = genres.orEmpty().filter { it.isNotBlank() }.take(MAX_GENRES)

    /**
     * The status pill of an item: a series that is still airing is Returning, a watched item is Watched, and
     * anything else has none.
     */
    fun status(
        kind: BaseItemKind?,
        seriesStatus: String?,
        played: Boolean,
    ): RawkoonStatus? =
        when {
            kind == BaseItemKind.SERIES && seriesStatus.equals("Continuing", ignoreCase = true) -> RawkoonStatus.Returning
            played -> RawkoonStatus.Watched
            else -> null
        }
}

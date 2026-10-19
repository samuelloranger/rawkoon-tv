package com.github.damontecres.wholphin.rawkoon.ui.home

import androidx.annotation.StringRes
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonStatus
import org.jellyfin.sdk.model.api.BaseItemKind

/** The pure parts of the Rawkoon home header: the type chip, genres and status it shows for an item */
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

    /**
     * Whether a header that follows focus should wait for focus to rest before going from the item it shows to
     * [targetTitle]/[targetSubtitle]. Another item waits. An update for the same item applies at once, and so does
     * filling an empty header or emptying a filled one.
     */
    fun waitsToSwitch(
        shownTitle: String?,
        shownSubtitle: String?,
        targetTitle: String?,
        targetSubtitle: String?,
    ): Boolean {
        val otherItem = targetTitle != shownTitle || targetSubtitle != shownSubtitle
        return otherItem && shownTitle != null && targetTitle != null
    }
}

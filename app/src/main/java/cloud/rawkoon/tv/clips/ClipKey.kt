package cloud.rawkoon.tv.clips

import cloud.rawkoon.tv.jellyfin.MediaItem

/** Which preview clip a card stands for: a movie, or one season of a show. */
data class ClipKey(val tmdbId: Int, val show: Boolean, val season: Int?) {
    /** The path and query of the clip's file on the Rawkoon server. */
    fun path(): String = "api/clips/file?tmdbId=$tmdbId&type=${if (show) "show" else "movie"}" + (season?.let { "&season=$it" } ?: "")

    companion object {
        /** The show whose TMDB id a season or episode card needs looked up, or null when the card carries what it needs. */
        fun seriesToResolve(item: MediaItem): String? =
            if (item.tmdb == null && (item.kind == MediaItem.Kind.SEASON || item.kind == MediaItem.Kind.EPISODE)) item.seriesId else null

        /**
         * The clip for [item]: a movie's own, a show's first season, a season's or episode's own season.
         * [seriesTmdbId] is the TMDB id of the show a season or episode belongs to. Cards that come from
         * TMDB through Rawkoon rather than from the library have no clip.
         */
        fun of(item: MediaItem, seriesTmdbId: Int?): ClipKey? {
            if (item.tmdb != null) return null
            return when (item.kind) {
                MediaItem.Kind.MOVIE -> item.libraryTmdbId?.let { ClipKey(it, show = false, season = null) }
                MediaItem.Kind.SERIES -> item.libraryTmdbId?.let { ClipKey(it, show = true, season = 1) }
                MediaItem.Kind.SEASON, MediaItem.Kind.EPISODE ->
                    // Specials (season 0) have no clip
                    item.seasonNumber?.takeIf { it >= 1 }?.let { n -> seriesTmdbId?.let { ClipKey(it, show = true, season = n) } }
                MediaItem.Kind.OTHER -> null
            }
        }
    }
}

/**
 * What a card's focus asks of the clip player, as plain state: a clip plays once focus has rested
 * long enough and its file is on disk, for the card still focused; anything else cancels it.
 */
class ClipIntent {
    @Volatile
    var url: String? = null
        private set
    private var dwelled = false
    private var downloaded = false

    /** A new card has focus (or none, with null): everything pending for the last one is void. */
    fun focus(url: String?) {
        this.url = url
        dwelled = false
        downloaded = false
    }

    fun stop() = focus(null)

    /** Focus has rested; true when the file is ready too, so playback starts now. */
    fun dwellElapsed(): Boolean {
        if (url == null) return false
        dwelled = true
        return downloaded
    }

    /** The file of [url] is on disk; true when focus has rested too and it is still the wanted clip. */
    fun downloaded(url: String): Boolean {
        if (this.url != url) return false
        downloaded = true
        return dwelled
    }
}

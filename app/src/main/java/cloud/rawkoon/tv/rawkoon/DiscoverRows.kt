package cloud.rawkoon.tv.rawkoon

import cloud.rawkoon.tv.R

/** One genre row of Discover: the movies or the shows of [genre], titled with [titleRes]. */
data class GenreRow(val isMovie: Boolean, val genre: Genre, val titleRes: Int)

/** Which rows Discover shows. */
object DiscoverRows {
    /** Rawkoon explore category (also the row's id) and the row's title. */
    val FEATURED = listOf(
        "trending" to R.string.discover_trending,
        "popular_movies" to R.string.discover_popular_movies,
        "popular_shows" to R.string.discover_popular_shows,
        "upcoming_movies" to R.string.discover_upcoming_movies,
        "on_the_air" to R.string.discover_on_the_air,
    )

    /** TMDB's own genre ids: action, comedy, science fiction, horror. */
    val MOVIE_GENRES = setOf(28, 35, 878, 27)

    /** TMDB's own genre ids: action & adventure, comedy, sci-fi & fantasy, crime. */
    val SHOW_GENRES = setOf(10759, 35, 10765, 80)

    /** The genre rows to show out of the genres TMDB lists: the movie ones, then the show ones. */
    fun genreRows(movieGenres: List<Genre>, showGenres: List<Genre>): List<GenreRow> =
        movieGenres.filter { it.id in MOVIE_GENRES }.map { GenreRow(true, it, R.string.discover_genre_movies) } +
            showGenres.filter { it.id in SHOW_GENRES }.map { GenreRow(false, it, R.string.discover_genre_shows) }
}

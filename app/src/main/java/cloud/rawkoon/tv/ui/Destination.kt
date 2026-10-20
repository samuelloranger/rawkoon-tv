package cloud.rawkoon.tv.ui

import cloud.rawkoon.tv.R

/** Where the rail can take you. */
enum class Destination(val glyph: String, val label: Int) {
    PROFILE("", R.string.switch_profile),
    SEARCH(Icon.SEARCH, R.string.nav_search),
    HOME(Icon.HOME, R.string.nav_home),
    FAVORITES(Icon.HEART, R.string.nav_favorites),
    DISCOVER(Icon.DISCOVER, R.string.nav_discover),
    MOVIES(Icon.FILM, R.string.nav_movies),
    SHOWS(Icon.TV, R.string.nav_shows),
    COLLECTIONS(Icon.FOLDER, R.string.nav_collections),
    SETTINGS(Icon.GEAR, R.string.nav_settings),
}


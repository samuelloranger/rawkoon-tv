package cloud.rawkoon.tv.ui

import android.view.animation.PathInterpolator

/**
 * Design tokens. The UI is laid out in dp on a 960x540dp canvas (1080p at 320dpi), and every
 * motion uses one curve so the whole screen shares a rhythm.
 */
object Tokens {
    const val background = 0xFF1C1715.toInt()
    const val surface = 0xFF241E1B.toInt()
    const val surfaceHigh = 0xFF2B2420.toInt()
    const val surfaceFocused = 0xFF3A302A.toInt()
    const val line = 0xFF463A32.toInt()
    const val textPrimary = 0xFFF4ECE4.toInt()
    const val textSoft = 0xFFE8E2DB.toInt()
    const val textMuted = 0xFFAA9A8C.toInt()
    const val accent = 0xFFE8A06A.toInt()
    const val ring = 0xFFF0A35E.toInt()
    const val rail = 0xF0141010.toInt()

    // TV title-safe area.
    const val OVERSCAN_H = 48
    const val OVERSCAN_V = 27

    /** Where content starts, right of the nav rail. */
    const val CONTENT_START = 90
    const val RAIL_W = 60
    const val RAIL_OPEN_W = 210

    /** Where the home rows start; the header owns everything above. */
    const val ROWS_TOP = 235

    // Pages of rows without a home-style hero (favorites, search): header and rows tops, and the
    // room a search bar takes above both.
    const val PAGE_HEADER_TOP = 26
    const val PAGE_ROWS_TOP = ROWS_TOP - 6
    const val SEARCH_BAR_OFFSET = 58

    const val POSTER_W = 102
    const val POSTER_H = 153
    const val LAND_W = 192
    const val LAND_H = 108
    const val CARD_GAP = 16
    const val CARD_RADIUS = 7f

    /** Space around a card's art for the ring: a gap in the page colour, then the stroke. */
    const val RING_INSET = 4

    const val FOCUS_SCALE = 1.06f
    const val FOCUS_IN_MS = 150L
    const val FOCUS_OUT_MS = 90L
    const val PRESS_SCALE = 1.02f
    const val PRESS_MS = 80L

    /** Row and column glides; long enough to read as motion, short enough to keep up with key repeat. */
    const val GLIDE_MS = 260L

    /** The header only changes once focus has rested, the backdrop a little later. */
    const val HEADER_SETTLE_MS = 120L
    const val HEADER_OUT_MS = 120L
    const val HEADER_IN_MS = 260L
    const val BACKDROP_SETTLE_MS = 300L
    const val BACKDROP_FADE_MS = 600L

    /** How much every backdrop is darkened before the scrims, for readable text over any image. */
    const val BACKDROP_DIM = 0.35f

    const val DRAWER_MS = 220L
    const val SCREEN_FADE_MS = 160L

    /** Expo-out: fast start, long soft landing. */
    val ease = PathInterpolator(0.16f, 1f, 0.3f, 1f)
}

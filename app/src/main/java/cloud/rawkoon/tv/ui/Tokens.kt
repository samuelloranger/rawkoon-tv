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

    // Body copy on a page (a title's overview).
    const val textBody = 0xE6ECE3DB.toInt()

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

    /** Room under a poster's art for its title and subtitle. */
    const val CAPTION_H = 38

    /** Space between the art's ring and its caption, so the ring of a focused card, grown, stays off the title. */
    const val CAPTION_GAP = 8

    const val POSTER_W = 102
    const val POSTER_H = 153
    const val LAND_W = 192
    const val LAND_H = 108
    const val CARD_GAP = 16

    /** Horizontal gap between the rings of neighbouring posters in a grid. */
    const val POSTER_GAP = 14
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

    /** The width a backdrop is requested at, in px. */
    const val BACKDROP_W = 1920

    /** How much every backdrop is darkened before the scrims, for readable text over any image. */
    const val BACKDROP_DIM = 0.35f

    /**
     * Home's backdrop zooms this far in and back out, [DRIFT_MS] each way: a few pixels a second,
     * slow enough to read as stillness that breathes. It holds while keys are pressed and for
     * [DRIFT_REST_MS] after, so it never shares a frame with a glide, and breathes once per rest.
     */
    const val DRIFT_SCALE = 1.04f
    const val DRIFT_MS = 20_000L
    const val DRIFT_REST_MS = 1_500L

    /** Cards of a row that just arrived fade in one after another, each rising [ARRIVE_RISE] dp. */
    const val ARRIVE_MS = 320L
    const val ARRIVE_STAGGER_MS = 30L
    const val ARRIVE_ROW_MS = 70L
    const val ARRIVE_RISE = 10f

    /** The player's skip and up-next buttons rise this far into place as they appear. */
    const val RISE = 12f
    const val RISE_MS = 220L

    /**
     * A card that changed while the page was away (back from the player): its progress bar grows to
     * the new position and a new watched check pops in, a moment after the page settles.
     */
    const val CHANGE_DELAY_MS = 150L
    const val GROW_MS = 700L
    const val POP_MS = 260L
    const val POP_FROM = 0.6f

    const val DRAWER_MS = 220L
    const val SCREEN_FADE_MS = 160L

    /** A page opened slides in from the right this far while it fades in; going back, from the left. */
    const val SCREEN_SHIFT = 24f
    const val SCREEN_SLIDE_MS = 240L

    /** Expo-out: fast start, long soft landing. */
    val ease = PathInterpolator(0.16f, 1f, 0.3f, 1f)
}

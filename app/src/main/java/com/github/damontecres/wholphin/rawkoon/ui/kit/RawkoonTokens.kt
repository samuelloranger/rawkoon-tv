package com.github.damontecres.wholphin.rawkoon.ui.kit

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Colors and radii of the Rawkoon web app (its `index.css`). Every Rawkoon composable reads from here.
 */
object RawkoonTokens {
    // Primary
    val Primary200 = Color(0xFFF2CDAB)
    val Primary300 = Color(0xFFF0BF93)
    val Primary400 = Color(0xFFE8A06A)
    val Primary500 = Color(0xFFDF8753)
    val Primary600 = Color(0xFFCF6A4E)
    val Primary700 = Color(0xFFAD5440)
    val Primary950 = Color(0xFF4F2419)

    // Neutral
    val Neutral50 = Color(0xFFF4ECE4)
    val Neutral100 = Color(0xFFE3D8CF)
    val Neutral200 = Color(0xFFD4C6B8)
    val Neutral300 = Color(0xFFB9A89A)
    val Neutral400 = Color(0xFFAA9A8C)
    val Neutral500 = Color(0xFF9D8775)
    val Neutral600 = Color(0xFF463A32)
    val Neutral700 = Color(0xFF322A25)
    val Neutral800 = Color(0xFF241E1B)
    val Neutral900 = Color(0xFF1C1715)
    val Neutral950 = Color(0xFF141010)

    /** The border of cast photos */
    val Border2 = Color(0xFF3A2F27)

    // Status bar colors and the 500/300 pair of each status badge
    val Emerald400 = Color(0xFF34D399)
    val Emerald500 = Color(0xFF10B981)
    val Emerald300 = Color(0xFF6EE7B7)
    val Sky400 = Color(0xFF38BDF8)
    val Sky500 = Color(0xFF0EA5E9)
    val Sky300 = Color(0xFF7DD3FC)
    val Rose400 = Color(0xFFFB7185)
    val Rose500 = Color(0xFFF43F5E)
    val Rose300 = Color(0xFFFDA4AF)
    val Amber400 = Color(0xFFFBBF24)
    val Amber500 = Color(0xFFF59E0B)
    val Amber300 = Color(0xFFFCD34D)

    /** App background */
    val Background = Neutral900

    /** The TMDB rating number */
    val Rating = Amber400

    /** White at the given alpha, the web app's `white/10` style surfaces */
    fun white(alpha: Float): Color = Color.White.copy(alpha = alpha)

    // Radii
    val RadiusSmall: Dp = 5.dp
    val RadiusMedium: Dp = 8.dp
    val RadiusLarge: Dp = 12.dp
    val RadiusXLarge: Dp = 16.dp

    // Focus ring: a gap in the app background, then the apricot ring, outside the element's shape
    val FocusRingGap: Dp = 2.dp
    val FocusRingWidth: Dp = 2.dp
    val FocusRingColor = Primary400
    val FocusRingGapColor = Neutral900

    /** Height of buttons and of the Play pill */
    val ButtonHeight: Dp = 44.dp
    val ChipHeight: Dp = 36.dp
}

/**
 * Rawkoon motion: enter is a fade plus an 8dp rise over 220ms, exit a 120ms fade, colors change over 150ms.
 * No springs, no scale-in, no rotation and nothing that loops.
 */
object RawkoonMotion {
    const val ENTER_MILLIS = 220
    const val EXIT_MILLIS = 120
    const val COLOR_MILLIS = 150

    /** The focus ring fades in a little slower than it fades out, so the eye follows it to the new element */
    const val FOCUS_IN_MILLIS = 150
    const val FOCUS_OUT_MILLIS = 90

    /** A header that follows focus: the old content leaves fast, the new one settles in */
    const val HEADER_OUT_MILLIS = 120
    const val HEADER_IN_MILLIS = 260
    const val HEADER_IN_DELAY_MILLIS = 40

    val EnterEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val ExitEasing = FastOutLinearInEasing

    val EnterRise: Dp = 8.dp
    val ExitDrop: Dp = 4.dp

    fun <T> enterSpec() = tween<T>(durationMillis = ENTER_MILLIS, easing = EnterEasing)

    fun <T> exitSpec() = tween<T>(durationMillis = EXIT_MILLIS, easing = ExitEasing)

    fun <T> colorSpec() = tween<T>(durationMillis = COLOR_MILLIS)
}

/** Fade in while rising [rise] */
@Composable
fun rawkoonEnter(rise: Dp = RawkoonMotion.EnterRise): EnterTransition {
    val risePx = with(LocalDensity.current) { rise.roundToPx() }
    return fadeIn(RawkoonMotion.enterSpec()) +
        slideInVertically(RawkoonMotion.enterSpec()) { risePx }
}

/** Fade out, dropping [drop] when it is not zero */
@Composable
fun rawkoonExit(drop: Dp = 0.dp): ExitTransition {
    val dropPx = with(LocalDensity.current) { drop.roundToPx() }
    return if (dropPx == 0) {
        fadeOut(RawkoonMotion.exitSpec())
    } else {
        fadeOut(RawkoonMotion.exitSpec()) + slideOutVertically(RawkoonMotion.exitSpec()) { dropPx }
    }
}

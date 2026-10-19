package com.github.damontecres.wholphin.rawkoon.ui.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate

/**
 * The one Rawkoon focus indicator: a 2dp gap in the app background, then a 2dp apricot ring, both outside the
 * element's [shape]. Nothing scales and nothing moves, so it is safe on rows and grids. The ring fades in over
 * [RawkoonMotion.FOCUS_IN_MILLIS] and out over [RawkoonMotion.FOCUS_OUT_MILLIS], so focus glides from one element to
 * the next instead of jumping. The fade is read in the draw phase, so it only redraws.
 */
@Composable
fun Modifier.rawkoonFocusRing(
    shape: Shape,
    focused: Boolean,
): Modifier {
    val alpha =
        animateFloatAsState(
            targetValue = if (focused) 1f else 0f,
            animationSpec =
                if (focused) {
                    tween(RawkoonMotion.FOCUS_IN_MILLIS, easing = RawkoonMotion.EnterEasing)
                } else {
                    tween(RawkoonMotion.FOCUS_OUT_MILLIS, easing = RawkoonMotion.ExitEasing)
                },
            label = "rawkoon_focus_ring",
        )
    // An element that is not focused and not fading out adds no draw node, which keeps large grids cheap. The check
    // goes through derivedStateOf so composition only reruns when the ring appears or disappears, not on every frame
    // of the fade.
    val visible by remember { derivedStateOf { alpha.value > 0f } }
    if (!focused && !visible) return this
    return drawWithContent {
        drawContent()
        val a = alpha.value
        if (a > 0f) drawRing(shape, a)
    }
}

/** [rawkoonFocusRing] that follows the focus state of [interactionSource] */
@Composable
fun Modifier.rawkoonFocusRing(
    shape: Shape,
    interactionSource: InteractionSource,
): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    return rawkoonFocusRing(shape, focused)
}

private fun DrawScope.drawRing(
    shape: Shape,
    alpha: Float,
) {
    val gap = RawkoonTokens.FocusRingGap.toPx()
    val ring = RawkoonTokens.FocusRingWidth.toPx()
    // A stroke is centered on its path, so each path sits half a stroke width out from its band's inner edge
    drawStrokeAt(shape, gap / 2, gap, RawkoonTokens.FocusRingGapColor.copy(alpha = alpha))
    drawStrokeAt(shape, gap + ring / 2, ring, RawkoonTokens.FocusRingColor.copy(alpha = alpha))
}

private fun DrawScope.drawStrokeAt(
    shape: Shape,
    expand: Float,
    width: Float,
    color: androidx.compose.ui.graphics.Color,
) {
    val grown =
        if (shape is CornerBasedShape) {
            // Corner radii grow with the ring so it stays concentric with the element
            shape.copy(CornerSize(shape.topStart.toPx(size, this) + expand))
        } else {
            shape
        }
    val outline = grown.createOutline(Size(size.width + 2 * expand, size.height + 2 * expand), layoutDirection, this)
    translate(-expand, -expand) {
        drawOutline(outline, color, style = Stroke(width))
    }
}

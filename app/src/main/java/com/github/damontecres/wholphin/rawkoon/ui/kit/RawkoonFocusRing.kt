package com.github.damontecres.wholphin.rawkoon.ui.kit

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * element's [shape]. Nothing scales and nothing moves, so it is safe on rows and grids.
 */
fun Modifier.rawkoonFocusRing(
    shape: Shape,
    focused: Boolean,
): Modifier =
    if (!focused) {
        this
    } else {
        drawWithContent {
            drawContent()
            drawRing(shape)
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

private fun DrawScope.drawRing(shape: Shape) {
    val gap = RawkoonTokens.FocusRingGap.toPx()
    val ring = RawkoonTokens.FocusRingWidth.toPx()
    // A stroke is centered on its path, so each path sits half a stroke width out from its band's inner edge
    drawStrokeAt(shape, gap / 2, gap, RawkoonTokens.FocusRingGapColor)
    drawStrokeAt(shape, gap + ring / 2, ring, RawkoonTokens.FocusRingColor)
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

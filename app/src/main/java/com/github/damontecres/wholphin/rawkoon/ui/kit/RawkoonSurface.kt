package com.github.damontecres.wholphin.rawkoon.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface

/**
 * A focusable TV [Surface] that never scales and draws the Rawkoon focus ring. The ring is drawn by a wrapper so the
 * surface's own clipping does not cut it off.
 */
@Composable
internal fun RawkoonSurface(
    onClick: () -> Unit,
    shape: Shape,
    container: Color,
    focusedContainer: Color,
    contentColor: Color,
    focusedContentColor: Color,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    border: BorderStroke? = null,
    focusedBorder: BorderStroke? = border,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.rawkoonFocusRing(shape, interactionSource)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            enabled = enabled,
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = container,
                    contentColor = contentColor,
                    focusedContainerColor = focusedContainer,
                    focusedContentColor = focusedContentColor,
                    pressedContainerColor = focusedContainer,
                    pressedContentColor = focusedContentColor,
                    disabledContainerColor = container.copy(alpha = container.alpha * DISABLED_ALPHA),
                    disabledContentColor = contentColor.copy(alpha = contentColor.alpha * DISABLED_ALPHA),
                ),
            border =
                ClickableSurfaceDefaults.border(
                    border = border?.let { Border(it, shape = shape) } ?: Border.None,
                    focusedBorder = focusedBorder?.let { Border(it, shape = shape) } ?: Border.None,
                    pressedBorder = focusedBorder?.let { Border(it, shape = shape) } ?: Border.None,
                ),
            interactionSource = interactionSource,
            content = content,
        )
    }
}

private const val DISABLED_ALPHA = 0.4f

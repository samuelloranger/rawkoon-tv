package com.github.damontecres.wholphin.rawkoon.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.PreviewTvSpec

enum class RawkoonButtonVariant {
    /** Terracotta fill, dark content */
    Primary,

    /** White at 10%, light content */
    Secondary,

    /** Transparent until focused */
    Ghost,
}

/** The container and content colors of a [RawkoonButtonVariant] */
internal data class RawkoonButtonColors(
    val container: Color,
    val focusedContainer: Color,
    val content: Color,
    val focusedContent: Color,
)

internal fun RawkoonButtonVariant.colors(): RawkoonButtonColors =
    when (this) {
        RawkoonButtonVariant.Primary -> {
            RawkoonButtonColors(
                container = RawkoonTokens.Primary600,
                focusedContainer = RawkoonTokens.Primary500,
                content = RawkoonTokens.Neutral950,
                focusedContent = RawkoonTokens.Neutral950,
            )
        }

        RawkoonButtonVariant.Secondary -> {
            RawkoonButtonColors(
                container = RawkoonTokens.white(0.10f),
                focusedContainer = RawkoonTokens.white(0.14f),
                content = RawkoonTokens.Neutral100,
                focusedContent = RawkoonTokens.Neutral100,
            )
        }

        RawkoonButtonVariant.Ghost -> {
            RawkoonButtonColors(
                container = Color.Transparent,
                focusedContainer = RawkoonTokens.white(0.14f),
                content = RawkoonTokens.Neutral100,
                focusedContent = RawkoonTokens.Neutral100,
            )
        }
    }

/**
 * A Rawkoon button: at least 44dp tall, 12dp radius, the label always visible, the Rawkoon focus ring and no scale.
 * [meta] is a trailing mono text such as `1:12:04` after "Resume".
 *
 * Replaces Wholphin's `ui.components.Button` and the expanding `ExpandablePlayButton`.
 */
@Composable
fun RawkoonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: RawkoonButtonVariant = RawkoonButtonVariant.Secondary,
    icon: ImageVector? = null,
    meta: String? = null,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = variant.colors()
    RawkoonSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        shape = RoundedCornerShape(RawkoonTokens.RadiusLarge),
        container = colors.container,
        focusedContainer = colors.focusedContainer,
        contentColor = colors.content,
        focusedContentColor = colors.focusedContent,
        interactionSource = interactionSource,
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .defaultMinSize(minHeight = RawkoonTokens.ButtonHeight)
                    .padding(horizontal = 16.dp),
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (meta != null) {
                RawkoonMonoText(
                    text = meta,
                    fontSize = 11.5.sp,
                    color = colors.content.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/**
 * A square icon-only button, 44dp with an 8dp radius. Ghost by default, for the transport controls.
 */
@Composable
fun RawkoonIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: RawkoonButtonVariant = RawkoonButtonVariant.Ghost,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = variant.colors()
    RawkoonSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        shape = RoundedCornerShape(RawkoonTokens.RadiusMedium),
        container = colors.container,
        focusedContainer = colors.focusedContainer,
        contentColor = colors.content,
        focusedContentColor = colors.focusedContent,
        interactionSource = interactionSource,
        modifier = modifier,
    ) {
        Box(Modifier.size(RawkoonTokens.ButtonHeight), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * The Play pill of the Rawkoon Listen page: 44dp tall, about 60dp wide, 8dp radius, terracotta.
 */
@Composable
fun RawkoonPlayButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.PlayArrow,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = RawkoonButtonVariant.Primary.colors()
    RawkoonSurface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(RawkoonTokens.RadiusMedium),
        container = colors.container,
        focusedContainer = colors.focusedContainer,
        contentColor = colors.content,
        focusedContentColor = colors.focusedContent,
        interactionSource = interactionSource,
        modifier = modifier,
    ) {
        Box(Modifier.size(width = PLAY_BUTTON_WIDTH, height = RawkoonTokens.ButtonHeight), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
        }
    }
}

private val PLAY_BUTTON_WIDTH = 60.dp

@PreviewTvSpec
@Composable
private fun RawkoonButtonsPreview() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(RawkoonTokens.Background).padding(24.dp),
    ) {
        RawkoonButton("Resume", {}, variant = RawkoonButtonVariant.Primary, icon = Icons.Filled.PlayArrow, meta = "1:12:04")
        RawkoonButton("Restart", {})
        RawkoonButton("More", {}, variant = RawkoonButtonVariant.Ghost)
        RawkoonIconButton(Icons.Filled.Settings, "Settings", {})
        RawkoonPlayButton({}, "Play")
    }
}

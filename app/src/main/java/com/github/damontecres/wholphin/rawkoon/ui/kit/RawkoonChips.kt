package com.github.damontecres.wholphin.rawkoon.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import java.util.Locale

private val ChipSelectedBg = RawkoonTokens.Primary500.copy(alpha = 0.15f)
private val ChipSelectedBorder = RawkoonTokens.Primary500.copy(alpha = 0.30f)

/**
 * A rounded-full, 36dp filter chip with an optional current [value] next to the label. Selected and focused chips
 * share the apricot look, and focus adds the ring.
 *
 * @param plainBackground use the plain border (neutral 700) instead of white at 14%, for chips on a flat surface
 */
@Composable
fun RawkoonChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    selected: Boolean = false,
    plainBackground: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    leading: (@Composable () -> Unit)? = null,
) {
    val idleBorder = BorderStroke(1.dp, if (plainBackground) RawkoonTokens.Neutral700 else RawkoonTokens.white(0.14f))
    val activeBorder = BorderStroke(1.dp, ChipSelectedBorder)
    val activeText = RawkoonTokens.Primary400
    RawkoonSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        shape = CircleShape,
        container = if (selected) ChipSelectedBg else Color.Transparent,
        focusedContainer = ChipSelectedBg,
        contentColor = if (selected) activeText else RawkoonTokens.Neutral100,
        focusedContentColor = activeText,
        border = if (selected) activeBorder else idleBorder,
        focusedBorder = activeBorder,
        interactionSource = interactionSource,
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .defaultMinSize(minHeight = RawkoonTokens.ChipHeight)
                    .padding(horizontal = 14.dp),
        ) {
            leading?.invoke()
            Text(
                text = label,
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            if (value != null) {
                Text(
                    text = value,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = RawkoonTokens.Neutral400,
                )
            }
        }
    }
}

/** The "Movie" / "Show" / "Episode" tag: 5dp radius, uppercase 10.5sp bold, apricot tint */
@Composable
fun TypeChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(RawkoonTokens.RadiusSmall)
    Text(
        text = text.uppercase(Locale.getDefault()),
        maxLines = 1,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.05.em,
        color = RawkoonTokens.Primary200,
        modifier =
            modifier
                .background(ChipSelectedBg, shape)
                .border(1.dp, ChipSelectedBorder, shape)
                .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** A genre or technical tag (resolution, HDR, audio): 5dp radius, 11sp medium, white tint */
@Composable
fun GenreChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(RawkoonTokens.RadiusSmall)
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = RawkoonTokens.white(0.75f),
        modifier =
            modifier
                .background(RawkoonTokens.white(0.10f), shape)
                .border(1.dp, RawkoonTokens.white(0.12f), shape)
                .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@PreviewTvSpec
@Composable
private fun RawkoonChipsPreview() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(RawkoonTokens.Background).padding(24.dp),
    ) {
        RawkoonChip("Audio", {}, value = "English 5.1")
        RawkoonChip("All", {}, selected = true)
        RawkoonChip("Genre", {}, plainBackground = true)
        TypeChip("Movie")
        GenreChip("Drama")
        GenreChip("4K")
        StatusPill(RawkoonStatus.Downloaded)
    }
}

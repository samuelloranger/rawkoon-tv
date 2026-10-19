package com.github.damontecres.wholphin.rawkoon.ui.preferences

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonMotion
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTokens
import com.github.damontecres.wholphin.rawkoon.ui.kit.SectionHeader
import com.github.damontecres.wholphin.rawkoon.ui.kit.rawkoonFocusRing
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.preferences.PreferenceSummary
import com.github.damontecres.wholphin.ui.preferences.PreferenceTitle

/** Sizes and colors of the Rawkoon settings list and switch */
object RawkoonPreferenceStyle {
    val ItemShape = RoundedCornerShape(RawkoonTokens.RadiusLarge)

    /** Space around a row so the 4dp focus ring of one row is not covered by its neighbours */
    val ItemSpacing = 4.dp

    val SwitchWidth = 44.dp
    val SwitchHeight = 24.dp
    val ThumbSize = 18.dp

    /** Gap between the switch edge and its thumb */
    val ThumbPadding: Dp = (SwitchHeight - ThumbSize) / 2

    /** Track color: terracotta when on, `#322A25` when off */
    fun trackColor(checked: Boolean): Color = if (checked) RawkoonTokens.Primary600 else RawkoonTokens.Neutral700

    /** How far the thumb sits from the start of the track */
    fun thumbOffset(checked: Boolean): Dp = if (checked) SwitchWidth - ThumbSize - ThumbPadding else ThumbPadding
}

/** The Rawkoon switch: a 44x24dp full-radius track (terracotta on, `#322A25` off) with a white thumb. */
@Composable
fun RawkoonSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val track by animateColorAsState(RawkoonPreferenceStyle.trackColor(checked), RawkoonMotion.colorSpec(), label = "track")
    val thumb by animateDpAsState(RawkoonPreferenceStyle.thumbOffset(checked), RawkoonMotion.colorSpec(), label = "thumb")
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier =
            modifier
                .size(RawkoonPreferenceStyle.SwitchWidth, RawkoonPreferenceStyle.SwitchHeight)
                .background(track, CircleShape),
    ) {
        Box(
            Modifier
                .offset(x = thumb)
                .size(RawkoonPreferenceStyle.ThumbSize)
                .background(Color.White, CircleShape),
        )
    }
}

/**
 * Rawkoon replacement for the row of [com.github.damontecres.wholphin.ui.preferences.ClickPreference]: a 12dp radius
 * row that gets a `#241E1B` background and the focus ring when focused, without scaling. The summary (the selected
 * value) is muted. [trailingContent] is an optional end slot.
 */
@Composable
fun RawkoonPreferenceRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onLongClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val shape = RawkoonPreferenceStyle.ItemShape
    ListItem(
        selected = false,
        onClick = onClick,
        onLongClick = onLongClick,
        headlineContent = { PreferenceTitle(title) },
        supportingContent = { PreferenceSummary(summary, color = RawkoonTokens.Neutral400) },
        trailingContent = trailingContent,
        colors =
            ListItemDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = RawkoonTokens.Neutral100,
                focusedContainerColor = RawkoonTokens.Neutral800,
                focusedContentColor = RawkoonTokens.Neutral50,
                pressedContainerColor = RawkoonTokens.Neutral800,
                pressedContentColor = RawkoonTokens.Neutral50,
            ),
        shape = ListItemDefaults.shape(shape = shape),
        scale = ListItemDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f),
        border =
            ListItemDefaults.border(
                border = Border.None,
                focusedBorder = Border.None,
                pressedBorder = Border.None,
            ),
        interactionSource = interactionSource,
        modifier =
            modifier
                .padding(vertical = RawkoonPreferenceStyle.ItemSpacing)
                .rawkoonFocusRing(shape, interactionSource),
    )
}

/** Rawkoon replacement for [com.github.damontecres.wholphin.ui.preferences.ClickPreference] */
@Composable
fun RawkoonClickPreference(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) = RawkoonPreferenceRow(
    title = title,
    onClick = onClick,
    modifier = modifier,
    summary = summary,
    onLongClick = onLongClick,
    interactionSource = interactionSource,
)

/** Rawkoon replacement for the switch overload of [com.github.damontecres.wholphin.ui.preferences.SwitchPreference] */
@Composable
fun RawkoonSwitchPreference(
    title: String,
    value: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) = RawkoonPreferenceRow(
    title = title,
    onClick = onClick,
    modifier = modifier,
    summary = summary,
    onLongClick = onLongClick,
    trailingContent = { RawkoonSwitch(checked = value) },
    interactionSource = interactionSource,
)

/** Rawkoon replacement for the summaryOn/summaryOff overload of [com.github.damontecres.wholphin.ui.preferences.SwitchPreference] */
@Composable
fun RawkoonSwitchPreference(
    title: String,
    value: Boolean,
    onClick: () -> Unit,
    summaryOn: String?,
    summaryOff: String?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) = RawkoonSwitchPreference(
    title = title,
    value = value,
    onClick = onClick,
    modifier = modifier,
    summary = if (value) summaryOn else summaryOff,
    onLongClick = onLongClick,
    interactionSource = interactionSource,
)

/** Rawkoon replacement for the group title `Text` in `PreferencesContent`: a [SectionHeader] */
@Composable
fun RawkoonPreferenceGroupTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    SectionHeader(
        title = title,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
    )
}

@PreviewTvSpec
@Composable
private fun RawkoonPreferencesPreview() {
    Box(Modifier.background(RawkoonTokens.Background).padding(24.dp).fillMaxWidth()) {
        androidx.compose.foundation.layout.Column {
            RawkoonPreferenceGroupTitle("Playback")
            RawkoonSwitchPreference(title = "Autoplay next", value = true, onClick = {}, summary = "On")
            RawkoonSwitchPreference(title = "Skip intros", value = false, onClick = {}, summary = "Off")
            RawkoonClickPreference(title = "Subtitle language", onClick = {}, summary = "English")
        }
    }
}

package com.github.damontecres.wholphin.rawkoon.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.PreviewTvSpec

/** One tab of [UnderlineTabs]: a [label] and an optional [count] shown in a badge */
data class RawkoonTab(
    val label: String,
    val count: Int? = null,
)

/**
 * Tabs on a 1dp baseline. The selected tab is light with a 2dp apricot indicator, the others are muted, and a
 * focused tab gets the Rawkoon focus ring. [onSelect] is called with the index of the clicked tab.
 */
@Composable
fun UnderlineTabs(
    tabs: List<RawkoonTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(RawkoonTokens.Neutral800),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEachIndexed { index, tab ->
                RawkoonTabItem(tab, index == selected, { onSelect(index) })
            }
        }
    }
}

/** One tab of [UnderlineTabs], also used by tab rows that bring their own focus handling */
@Composable
internal fun RawkoonTabItem(
    tab: RawkoonTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(RawkoonTokens.RadiusMedium)
    Box(
        modifier =
            modifier
                .rawkoonFocusRing(shape, focused)
                .clip(shape)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            Text(
                text = tab.label,
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) RawkoonTokens.Neutral100 else RawkoonTokens.Neutral400,
            )
            if (tab.count != null) {
                Text(
                    text = tab.count.toString(),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) RawkoonTokens.Primary300 else RawkoonTokens.Neutral400,
                    modifier =
                        Modifier
                            .background(
                                if (selected) RawkoonTokens.Primary500.copy(alpha = 0.2f) else RawkoonTokens.Neutral700,
                                CircleShape,
                            ).padding(horizontal = 6.dp),
                )
            }
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(RawkoonTokens.Primary500, RoundedCornerShape(topStart = 99.dp, topEnd = 99.dp)),
            )
        }
    }
}

@PreviewTvSpec
@Composable
private fun UnderlineTabsPreview() {
    Box(Modifier.background(RawkoonTokens.Background).padding(24.dp).width(480.dp)) {
        UnderlineTabs(
            tabs = listOf(RawkoonTab("Season 1", 10), RawkoonTab("Season 2", 8), RawkoonTab("Extras")),
            selected = 0,
            onSelect = {},
        )
    }
}

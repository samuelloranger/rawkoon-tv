package com.github.damontecres.wholphin.rawkoon.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTab
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTabItem
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTokens
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.StringStringProvider

/**
 * Underline tabs that keep Wholphin's tab row focus behavior: each tab uses its own `tabFocusRequester`, entering
 * the row from the left or right lands on the last or first tab and otherwise on the selected one, and the selected
 * tab scrolls into view.
 *
 * Replaces Wholphin's `ui.components.TabRow` (same parameters).
 */
@Composable
fun RawkoonTabRow(
    selectedTabIndex: Int,
    tabs: List<TabDetails>,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex in tabs.indices) {
            state.animateScrollToItem(selectedTabIndex, -(state.layoutInfo.viewportSize.width / 3.5).toInt())
        }
    }
    val currentSelectedTabIndex by rememberUpdatedState(selectedTabIndex)
    val currentOnClick by rememberUpdatedState(onClick)
    val titles = tabs.map { it.title.getString() }

    Box(modifier = modifier) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(RawkoonTokens.Neutral800),
        )
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier =
                Modifier
                    .focusGroup()
                    .focusProperties {
                        onEnter = {
                            // If entering from left or right, use last or first tab, otherwise the selected tab
                            val tab =
                                when (requestedFocusDirection) {
                                    FocusDirection.Left -> tabs.lastOrNull()
                                    FocusDirection.Right -> tabs.firstOrNull()
                                    else -> tabs.getOrNull(currentSelectedTabIndex)
                                }
                            (tab?.tabFocusRequester ?: FocusRequester.Default).tryRequestFocus()
                        }
                    },
        ) {
            itemsIndexed(tabs) { index, tab ->
                RawkoonTabItem(
                    tab = RawkoonTab(titles[index]),
                    selected = index == selectedTabIndex,
                    onClick = { currentOnClick(index) },
                    modifier = Modifier.focusRequester(tab.tabFocusRequester),
                )
            }
        }
    }
}

@PreviewTvSpec
@Composable
private fun RawkoonTabRowPreview() {
    Box(Modifier.background(RawkoonTokens.Background).padding(24.dp)) {
        RawkoonTabRow(
            selectedTabIndex = 0,
            tabs = remember { listOf("Season 1", "Season 2", "Extras").map { TabDetails(StringStringProvider(it)) } },
            onClick = {},
        )
    }
}

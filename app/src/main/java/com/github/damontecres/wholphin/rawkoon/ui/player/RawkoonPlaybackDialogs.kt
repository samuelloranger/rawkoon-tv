package com.github.damontecres.wholphin.rawkoon.ui.player

import android.view.Gravity
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonDialogItem
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonDialogPanel
import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import com.github.damontecres.wholphin.ui.playback.SimpleMediaStream
import com.github.damontecres.wholphin.ui.playback.isDown
import com.github.damontecres.wholphin.ui.playback.isUp
import com.github.damontecres.wholphin.ui.playback.overlay.BottomDialogItem
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.launch

/** The most a dialog list grows before it scrolls */
private val DialogListPadding = PaddingValues(vertical = 6.dp)

private val DialogListMaxHeight = 420.dp

/**
 * A Rawkoon dialog docked to the bottom of the screen on the side given by [gravity], with no dimming so the video
 * stays visible. The window is a plain [Dialog]; the panel is the kit's [RawkoonDialogPanel].
 */
@Composable
private fun RawkoonDockedDialog(
    title: String,
    gravity: Int,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.let { window ->
            window.setGravity(Gravity.BOTTOM or gravity)
            window.setDimAmount(0f)
        }
        RawkoonDialogPanel(
            title = title,
            subtitle = null,
            modifier = Modifier.padding(16.dp),
        ) {
            content()
        }
    }
}

/**
 * A list of choices, one of them current. Replaces Wholphin's `BottomDialog`; same parameters plus a [title].
 */
@Composable
fun <T> RawkoonBottomDialog(
    choices: List<BottomDialogItem<T>>,
    onDismissRequest: () -> Unit,
    onSelectChoice: (Int, BottomDialogItem<T>) -> Unit,
    gravity: Int,
    currentChoice: BottomDialogItem<T>? = null,
    title: String = "",
) {
    val focusRequesters = remember(choices.size) { List(choices.size) { FocusRequester() } }
    if (currentChoice != null) {
        LaunchedEffect(Unit) {
            choices.indexOfFirstOrNull { it == currentChoice }?.let {
                focusRequesters.getOrNull(it)?.tryRequestFocus()
            }
        }
    }
    RawkoonDockedDialog(title = title, gravity = gravity, onDismissRequest = onDismissRequest) {
        LazyColumn(
            // Room for the focus ring, which draws outside the rows and would be clipped by the list
            contentPadding = DialogListPadding,
            modifier = Modifier.heightIn(max = DialogListMaxHeight),
        ) {
            itemsIndexed(choices) { index, choice ->
                RawkoonDialogItem(
                    text = choice.headline,
                    meta = choice.supporting,
                    selected = choice == currentChoice,
                    enabled = choice.enabled,
                    onClick = {
                        onDismissRequest()
                        onSelectChoice(index, choice)
                    },
                    modifier = Modifier.focusRequester(focusRequesters[index]),
                )
            }
        }
    }
}

/**
 * The subtitle picker: off, only forced, every track, and search. Focus wraps from the first row to the last and back.
 * Replaces Wholphin's `SubtitleChoiceBottomDialog`; same parameters plus a [title].
 */
@Composable
fun RawkoonSubtitleChoiceBottomDialog(
    choices: List<SimpleMediaStream>,
    onDismissRequest: () -> Unit,
    onSelectChoice: (Int) -> Unit,
    onSelectSearch: () -> Unit,
    gravity: Int,
    hasDownloadPermission: Boolean,
    currentChoice: Int? = null,
    title: String = "",
) {
    val numberBefore = 2
    val initialChoice = remember(choices) { choices.indexOfFirstOrNull { it.index == currentChoice } }
    val scope = rememberCoroutineScope()
    val listState =
        rememberLazyListState(initialFirstVisibleItemIndex = initialChoice?.plus(numberBefore) ?: 0)
    val firstFocusRequester = remember { FocusRequester() }
    val lastFocusRequester = remember { FocusRequester() }
    val choiceFocusRequesters = remember(choices.size) { List(choices.size) { FocusRequester() } }
    LaunchedEffect(Unit) {
        val focusRequester = initialChoice?.let { choiceFocusRequesters.getOrNull(it) } ?: firstFocusRequester
        focusRequester.tryRequestFocus()
    }
    RawkoonDockedDialog(title = title, gravity = gravity, onDismissRequest = onDismissRequest) {
        LazyColumn(
            // Room for the focus ring, which draws outside the rows and would be clipped by the list
            contentPadding = DialogListPadding,
            state = listState,
            modifier = Modifier.heightIn(max = DialogListMaxHeight),
        ) {
            item {
                val interactionSource = remember { MutableInteractionSource() }
                val focused by interactionSource.collectIsFocusedAsState()
                RawkoonDialogItem(
                    text = stringResource(R.string.none),
                    selected = currentChoice == TrackIndex.DISABLED,
                    onClick = { onSelectChoice(TrackIndex.DISABLED) },
                    interactionSource = interactionSource,
                    modifier =
                        Modifier
                            .focusRequester(firstFocusRequester)
                            .onKeyEvent {
                                if (focused && isUp(it) && it.type == KeyEventType.KeyDown) {
                                    scope.launch {
                                        listState.animateScrollToItem(Int.MAX_VALUE)
                                        lastFocusRequester.tryRequestFocus()
                                    }
                                    return@onKeyEvent true
                                }
                                false
                            },
                )
            }
            item {
                RawkoonDialogItem(
                    text = stringResource(R.string.only_forced_subtitles),
                    selected = currentChoice == TrackIndex.ONLY_FORCED,
                    onClick = { onSelectChoice(TrackIndex.ONLY_FORCED) },
                )
            }
            streamItems(choices, currentChoice, choiceFocusRequesters) { onSelectChoice(it.index) }
            item {
                val interactionSource = remember { MutableInteractionSource() }
                val focused by interactionSource.collectIsFocusedAsState()
                RawkoonDialogItem(
                    text = stringResource(R.string.search_and_download),
                    enabled = hasDownloadPermission,
                    onClick = onSelectSearch,
                    interactionSource = interactionSource,
                    modifier =
                        Modifier
                            .focusRequester(lastFocusRequester)
                            .onKeyEvent {
                                if (focused && isDown(it) && it.type == KeyEventType.KeyDown) {
                                    scope.launch {
                                        listState.animateScrollToItem(0)
                                        firstFocusRequester.tryRequestFocus()
                                    }
                                    return@onKeyEvent true
                                }
                                false
                            },
                )
            }
        }
    }
}

/**
 * The audio picker. Replaces Wholphin's `StreamChoiceBottomDialog`; same parameters plus a [title].
 */
@Composable
fun RawkoonStreamChoiceBottomDialog(
    choices: List<SimpleMediaStream>,
    onDismissRequest: () -> Unit,
    onSelectChoice: (Int, SimpleMediaStream) -> Unit,
    gravity: Int,
    currentChoice: Int? = null,
    title: String = "",
) {
    val focusRequesters = remember(choices.size) { List(choices.size) { FocusRequester() } }
    if (currentChoice != null) {
        LaunchedEffect(Unit) {
            choices.indexOfFirstOrNull { it.index == currentChoice }?.let {
                focusRequesters.getOrNull(it)?.tryRequestFocus()
            }
        }
    }
    RawkoonDockedDialog(title = title, gravity = gravity, onDismissRequest = onDismissRequest) {
        LazyColumn(
            // Room for the focus ring, which draws outside the rows and would be clipped by the list
            contentPadding = DialogListPadding,
            modifier = Modifier.heightIn(max = DialogListMaxHeight),
        ) {
            streamItems(choices, currentChoice, focusRequesters) { stream ->
                onDismissRequest()
                onSelectChoice(choices.indexOf(stream), stream)
            }
        }
    }
}

private fun LazyListScope.streamItems(
    choices: List<SimpleMediaStream>,
    currentChoice: Int?,
    focusRequesters: List<FocusRequester>,
    onSelect: (SimpleMediaStream) -> Unit,
) {
    itemsIndexed(choices) { index, choice ->
        RawkoonDialogItem(
            text = choice.streamTitle ?: choice.displayTitle,
            meta = if (choice.streamTitle != null) choice.displayTitle else null,
            selected = choice.index == currentChoice,
            onClick = { onSelect(choice) },
            modifier = Modifier.focusRequester(focusRequesters[index]),
        )
    }
}

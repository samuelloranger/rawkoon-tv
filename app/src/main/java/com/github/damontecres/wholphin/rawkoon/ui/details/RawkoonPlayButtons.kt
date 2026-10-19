package com.github.damontecres.wholphin.rawkoon.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonButton
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonButtonVariant
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTokens
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.ConfirmDeleteDialog
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.TrailerDialog
import com.github.damontecres.wholphin.ui.components.chooseVersionParams
import com.github.damontecres.wholphin.ui.data.ChooseVersionParams
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The action row of the details pages as always-labelled [RawkoonButton]s: a primary Play or Resume, then Restart,
 * Version, Watched, Favorite, Trailer, Delete and More, as offered by [playActions]. Nothing expands on focus.
 *
 * Replaces Wholphin's `ui.components.ExpandablePlayButtons` (same parameters).
 */
@Composable
fun RawkoonPlayButtons(
    title: String,
    resumePosition: Duration,
    watched: Boolean,
    favorite: Boolean,
    canDelete: Boolean,
    trailers: List<Trailer>?,
    playOnClick: (position: Duration) -> Unit,
    watchOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    trailerOnClick: (Trailer) -> Unit,
    onConfirmDelete: () -> Unit,
    chooseVersionParams: ChooseVersionParams?,
    buttonOnFocusChanged: (FocusState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val firstFocus = remember { FocusRequester() }
    val restartInteractionSource = remember { MutableInteractionSource() }
    val restartButtonFocused by restartInteractionSource.collectIsFocusedAsState()
    LaunchedEffect(resumePosition) {
        // Reset focus if the restart button was focused and it is going to disappear
        if (restartButtonFocused && resumePosition == Duration.ZERO) {
            firstFocus.tryRequestFocus()
        }
    }

    var chooseVersion by remember { mutableStateOf<DialogParams?>(null) }
    var showTrailers by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    val actions =
        playActions(
            resumePosition = resumePosition,
            mediaSourceCount = chooseVersionParams?.mediaSources?.size ?: 0,
            hasTrailerButton = !trailers.isNullOrEmpty(),
            canDelete = canDelete,
        )
    // The buttons sit in a focus wrapper, so report the wrapper's focus as the button's own
    val onFocus = Modifier.onFocusChanged { buttonOnFocusChanged(ChildFocusState(it)) }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(8.dp),
        modifier =
            modifier
                .focusGroup()
                .focusRestorer(firstFocus),
    ) {
        items(actions, key = { it.name }) { action ->
            when (action) {
                RawkoonPlayAction.Play -> {
                    val resuming = resumePosition > Duration.ZERO
                    RawkoonButton(
                        text = stringResource(if (resuming) R.string.resume else R.string.play),
                        onClick = { playOnClick.invoke(resumePosition) },
                        variant = RawkoonButtonVariant.Primary,
                        icon = Icons.Filled.PlayArrow,
                        meta = if (resuming) formatResumeTime(resumePosition) else null,
                        modifier = onFocus.focusRequester(firstFocus),
                    )
                }

                RawkoonPlayAction.Restart -> {
                    RawkoonButton(
                        text = stringResource(R.string.restart),
                        onClick = { playOnClick.invoke(Duration.ZERO) },
                        interactionSource = restartInteractionSource,
                        modifier = onFocus,
                    )
                }

                RawkoonPlayAction.Version -> {
                    RawkoonButton(
                        text = stringResource(R.string.version),
                        onClick = {
                            if (chooseVersionParams != null) {
                                chooseVersion =
                                    chooseVersionParams(
                                        resources,
                                        chooseVersionParams.mediaSources,
                                        chooseVersionParams.chosenStreams
                                            ?.source
                                            ?.id
                                            ?.toUUIDOrNull(),
                                    ) { idx ->
                                        chooseVersionParams.onChooseVersion.invoke(chooseVersionParams.mediaSources[idx])
                                    }
                            }
                        },
                        modifier = onFocus,
                    )
                }

                RawkoonPlayAction.Watched -> {
                    RawkoonButton(
                        text = stringResource(if (watched) R.string.mark_unwatched else R.string.mark_watched),
                        onClick = watchOnClick,
                        modifier = onFocus,
                    )
                }

                RawkoonPlayAction.Favorite -> {
                    RawkoonButton(
                        text = stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite),
                        onClick = favoriteOnClick,
                        modifier = onFocus,
                    )
                }

                RawkoonPlayAction.Trailer -> {
                    val list = trailers.orEmpty()
                    RawkoonButton(
                        text =
                            stringResource(
                                when {
                                    list.size == 1 -> R.string.play_trailer
                                    else -> R.string.trailers_title
                                },
                            ),
                        onClick = {
                            if (list.size == 1) {
                                trailerOnClick.invoke(list.first())
                            } else {
                                showTrailers = true
                            }
                        },
                        modifier = onFocus,
                    )
                }

                RawkoonPlayAction.Delete -> {
                    RawkoonButton(
                        text = stringResource(R.string.delete),
                        onClick = { showDelete = true },
                        modifier = onFocus,
                    )
                }

                RawkoonPlayAction.More -> {
                    RawkoonButton(
                        text = stringResource(R.string.more),
                        onClick = { moreOnClick.invoke() },
                        modifier = onFocus,
                    )
                }
            }
        }
    }
    chooseVersion?.let { params ->
        DialogPopup(
            showDialog = true,
            title = params.title,
            dialogItems = params.items,
            onDismissRequest = { chooseVersion = null },
            dismissOnClick = true,
            waitToLoad = params.fromLongClick,
        )
    }
    if (showTrailers && trailers != null) {
        TrailerDialog(
            onDismissRequest = { showTrailers = false },
            trailers = trailers,
            onClick = trailerOnClick,
        )
    }
    if (showDelete) {
        ConfirmDeleteDialog(
            itemTitle = title,
            onCancel = { showDelete = false },
            onConfirm = {
                onConfirmDelete.invoke()
                showDelete = false
            },
        )
    }
}

/** A [FocusState] that reports a focused child as the element's own focus */
private class ChildFocusState(
    private val state: FocusState,
) : FocusState {
    override val isFocused: Boolean get() = state.hasFocus
    override val hasFocus: Boolean get() = state.hasFocus
    override val isCaptured: Boolean get() = state.isCaptured
}

@PreviewTvSpec
@Composable
private fun RawkoonPlayButtonsPreview() {
    Box(Modifier.background(RawkoonTokens.Background).padding(16.dp)) {
        RawkoonPlayButtons(
            title = "Movie",
            resumePosition = 72.minutes + 4.seconds,
            watched = false,
            favorite = false,
            canDelete = true,
            trailers = emptyList(),
            playOnClick = {},
            watchOnClick = {},
            favoriteOnClick = {},
            moreOnClick = {},
            trailerOnClick = {},
            onConfirmDelete = {},
            chooseVersionParams = null,
            buttonOnFocusChanged = {},
        )
    }
}

package com.github.damontecres.wholphin.rawkoon.ui.details

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonButton
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonButtonVariant
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonSurface
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTokens
import com.github.damontecres.wholphin.ui.FontAwesome
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
 * The action row of the details pages as [RawkoonButton]s: a labelled primary Play or Resume and labelled
 * Trailer, Version and Delete buttons, then icon-only Restart, Watched, Favorite and More, as offered by [playActions].
 * A fixed caption line under the row names the focused icon button. Nothing expands or moves on focus.
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

    var focusedIconAction by remember { mutableStateOf<RawkoonPlayAction?>(null) }

    // Icon buttons also report which action holds focus, for the caption line
    fun iconFocus(action: RawkoonPlayAction) =
        onFocus.onFocusChanged {
            if (it.hasFocus) {
                focusedIconAction = action
            } else if (focusedIconAction == action) {
                focusedIconAction = null
            }
        }
    val restartLabel = stringResource(R.string.restart)
    val watchedLabel = stringResource(if (watched) R.string.mark_unwatched else R.string.mark_watched)
    val favoriteLabel = stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite)
    val moreLabel = stringResource(R.string.more)
    val caption =
        when (focusedIconAction) {
            RawkoonPlayAction.Restart -> restartLabel
            RawkoonPlayAction.Watched -> watchedLabel
            RawkoonPlayAction.Favorite -> favoriteLabel
            RawkoonPlayAction.More -> moreLabel
            else -> ""
        }

    Column(modifier = modifier) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(8.dp),
            modifier =
                Modifier
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
                        RawkoonActionIconButton(
                            contentDescription = restartLabel,
                            onClick = { playOnClick.invoke(Duration.ZERO) },
                            interactionSource = restartInteractionSource,
                            modifier = iconFocus(action),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(ICON_SIZE).graphicsLayer { scaleX = -1f },
                            )
                        }
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
                        RawkoonActionIconButton(
                            contentDescription = watchedLabel,
                            onClick = watchOnClick,
                            modifier = iconFocus(action),
                        ) {
                            FaGlyph(stringResource(if (watched) R.string.fa_eye else R.string.fa_eye_slash), RawkoonTokens.Neutral100)
                        }
                    }

                    RawkoonPlayAction.Favorite -> {
                        RawkoonActionIconButton(
                            contentDescription = favoriteLabel,
                            onClick = favoriteOnClick,
                            modifier = iconFocus(action),
                        ) {
                            FaGlyph(
                                stringResource(R.string.fa_heart),
                                if (favorite) RawkoonTokens.Rose400 else RawkoonTokens.Neutral100,
                            )
                        }
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
                        RawkoonActionIconButton(
                            contentDescription = moreLabel,
                            onClick = { moreOnClick.invoke() },
                            modifier = iconFocus(action),
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = null,
                                modifier = Modifier.size(ICON_SIZE),
                            )
                        }
                    }
                }
            }
        }
        // One fixed line, always reserved, so nothing shifts when it fills
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.height(CAPTION_HEIGHT).padding(horizontal = 8.dp),
        ) {
            Crossfade(targetState = caption, animationSpec = tween(150), label = "action-caption") { text ->
                Text(
                    text = text,
                    maxLines = 1,
                    color = RawkoonTokens.Neutral400,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                )
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

private val ICON_SIZE = 20.dp
private val CAPTION_HEIGHT = 20.dp

/** A 44dp square, 12dp radius Secondary icon-only button; the glyph is [content], the label is [contentDescription] */
@Composable
private fun RawkoonActionIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    RawkoonSurface(
        onClick = onClick,
        shape = RoundedCornerShape(RawkoonTokens.RadiusLarge),
        container = RawkoonTokens.white(0.10f),
        focusedContainer = RawkoonTokens.white(0.14f),
        contentColor = RawkoonTokens.Neutral100,
        focusedContentColor = RawkoonTokens.Neutral100,
        interactionSource = interactionSource,
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(RawkoonTokens.ButtonHeight).semantics { this.contentDescription = contentDescription },
        ) {
            content()
        }
    }
}

/** A Font Awesome glyph at icon size in [color] */
@Composable
private fun FaGlyph(
    glyph: String,
    color: Color,
) {
    Text(
        text = glyph,
        color = color,
        fontSize = 16.sp,
        fontFamily = FontAwesome,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleSmall,
    )
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

package com.github.damontecres.wholphin.rawkoon.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.data.model.PlaylistItem
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.rawkoon.RawkoonDisplay
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonButton
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonButtonVariant
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonChip
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonIconButton
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonMonoText
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonMotion
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonPlayButton
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTokens
import com.github.damontecres.wholphin.rawkoon.ui.kit.TypeChip
import com.github.damontecres.wholphin.rawkoon.ui.kit.rawkoonEnter
import com.github.damontecres.wholphin.rawkoon.ui.kit.rawkoonExit
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.HiddenFocusBox
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.formatTime
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.playback.AnalyticsState
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.playback.CurrentPlayback
import com.github.damontecres.wholphin.ui.playback.PlaybackDialogType
import com.github.damontecres.wholphin.ui.playback.overlay.OverlayViewState
import com.github.damontecres.wholphin.ui.playback.overlay.PlaybackAction
import com.github.damontecres.wholphin.ui.playback.overlay.PlaybackDebugOverlay
import com.github.damontecres.wholphin.ui.playback.overlay.offsetByPercent
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.seekBack
import com.github.damontecres.wholphin.ui.seekForward
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.LocalClock
import com.github.damontecres.wholphin.util.languageName
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.TrickplayInfo
import org.jellyfin.sdk.model.extensions.ticks
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val ScrimColor = RawkoonTokens.Neutral900

/** The tray slides between its panes in 260ms with the Rawkoon easing */
private const val PANE_SLIDE_MILLIS = 260

/**
 * The Rawkoon playback overlay: a warm scrim, the title strip on top and a control tray at the bottom with the seek
 * bar and a `[left | transport | right]` control row. Pressing down slides the whole tray up to the chapters, and the
 * queue under them. Nothing else moves while seeking.
 *
 * Replaces Wholphin's `PlaybackOverlay` (and through it `PlaybackController`, `PlaybackControls`, `SeekBar`,
 * `TrickplayPreview`, `ChapterRowOverlay` and `QueueRowOverlay`). Same parameters.
 */
@Composable
fun RawkoonPlaybackOverlay(
    item: BaseItem?,
    chapters: List<Chapter>,
    player: Player,
    controllerViewState: ControllerViewState,
    showPlay: Boolean,
    showClock: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekEnabled: Boolean,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    onSeekBarChange: (Long) -> Unit,
    showDebugInfo: Boolean,
    currentPlayback: CurrentPlayback?,
    currentSegment: MediaSegmentDto?,
    analyticsState: AnalyticsState,
    queue: List<PlaylistItem>,
    modifier: Modifier = Modifier,
    trickplayInfo: TrickplayInfo? = null,
    trickplayUrlFor: (Int) -> String? = { null },
    onClickPlaylist: (BaseItem) -> Unit = {},
    seekBarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val controlsVisible = controllerViewState.controlsVisible
    val seekBarFocused by seekBarInteractionSource.collectIsFocusedAsState()
    // The seek position, and where the seek started, for the trickplay preview
    var seekProgressMs by remember(seekBarFocused) { mutableLongStateOf(player.currentPosition) }
    val seekOriginMs = remember(seekBarFocused) { player.currentPosition }

    // Which pane the tray shows. It goes back to the controls once the tray has faded out.
    var pane by remember { mutableStateOf(OverlayViewState.CONTROLLER) }
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) {
            delay((RawkoonMotion.EXIT_MILLIS + 50).toLong())
            pane = OverlayViewState.CONTROLLER
        }
    }
    val onChangeState = { newState: OverlayViewState -> pane = newState }

    Box(modifier = modifier) {
        // Warm scrim: dark at the bottom for the tray, a lighter one at the top for the title
        AnimatedVisibility(
            visible = controlsVisible && !showDebugInfo,
            enter = fadeIn(RawkoonMotion.enterSpec()),
            exit = fadeOut(RawkoonMotion.exitSpec()),
            modifier = Modifier.matchParentSize(),
        ) {
            val bottomScrim =
                remember {
                    Brush.verticalGradient(
                        0.50f to Color.Transparent,
                        0.78f to ScrimColor.copy(alpha = 0.75f),
                        1.00f to ScrimColor.copy(alpha = 0.97f),
                    )
                }
            val topScrim =
                remember {
                    Brush.verticalGradient(
                        0.00f to ScrimColor.copy(alpha = 0.70f),
                        0.22f to Color.Transparent,
                    )
                }
            Box(Modifier.fillMaxSize().background(bottomScrim).background(topScrim))
        }

        // Top strip: title on the left, clock and end time on the right
        val logoImageUrl = LocalImageUrlService.current.rememberImageUrl(item, ImageType.LOGO)
        val endsAt = rememberEndsAt(player)
        val dropPx = with(LocalDensity.current) { RawkoonMotion.ExitDrop.roundToPx() }
        AnimatedVisibility(
            visible = !showDebugInfo && controlsVisible,
            enter =
                fadeIn(RawkoonMotion.enterSpec()) +
                    slideInVertically(RawkoonMotion.enterSpec()) { -dropPx },
            exit =
                fadeOut(RawkoonMotion.exitSpec()) +
                    slideOutVertically(RawkoonMotion.exitSpec()) { -dropPx },
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
        ) {
            RawkoonTopStrip(item = item, logoUrl = logoImageUrl, showClock = showClock, endsAt = endsAt)
        }

        // Trickplay: follows the playhead linearly, fades in 120ms
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            val fraction =
                animateFloatAsState(
                    targetValue = fractionOf(seekProgressMs, player.duration),
                    animationSpec = tween(120, easing = LinearEasing),
                    label = "RawkoonTrickplayX",
                )
            AnimatedVisibility(
                visible = controlsVisible && !showDebugInfo && seekBarFocused,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(120)),
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = RawkoonOverlayInset)
                        .padding(bottom = RawkoonTrayHeight - 28.dp),
            ) {
                Box(Modifier.fillMaxWidth()) {
                    RawkoonTrickplayThumb(
                        seekProgressMs = seekProgressMs,
                        deltaMs = seekProgressMs - seekOriginMs,
                        trickplayInfo = trickplayInfo,
                        trickplayUrlFor = trickplayUrlFor,
                        modifier = Modifier.offsetByPercent(fraction.value),
                    )
                }
            }
        }

        // The tray: fades and rises on show, and slides between the controls, chapters and queue
        AnimatedVisibility(
            visible = controlsVisible,
            enter = rawkoonEnter(),
            exit = rawkoonExit(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(Modifier.fillMaxWidth().height(RawkoonTrayHeight).clipToBounds()) {
                val slideSpec = tween<IntOffset>(PANE_SLIDE_MILLIS, easing = RawkoonMotion.EnterEasing)
                AnimatedContent(
                    targetState = pane,
                    label = "RawkoonTray",
                    transitionSpec = {
                        if (targetState.ordinal > initialState.ordinal) {
                            // Down: the tray moves up by its height
                            slideInVertically(slideSpec) { it } togetherWith slideOutVertically(slideSpec) { -it }
                        } else {
                            slideInVertically(slideSpec) { -it } togetherWith slideOutVertically(slideSpec) { it }
                        }
                    },
                ) { target ->
                    val nextState =
                        remember(chapters, nextEnabled) {
                            if (chapters.isNotEmpty()) {
                                OverlayViewState.CHAPTERS
                            } else if (nextEnabled) {
                                OverlayViewState.QUEUE
                            } else {
                                null
                            }
                        }
                    when (target) {
                        OverlayViewState.HIDDEN -> {
                            Box(Modifier.fillMaxWidth())
                        }

                        OverlayViewState.CONTROLLER -> {
                            RawkoonControlPane(
                                player = player,
                                controllerViewState = controllerViewState,
                                currentPlayback = currentPlayback,
                                showPlay = showPlay,
                                previousEnabled = previousEnabled,
                                nextEnabled = nextEnabled,
                                seekEnabled = seekEnabled,
                                seekBack = seekBack,
                                skipBackOnResume = skipBackOnResume,
                                seekForward = seekForward,
                                currentSegment = currentSegment,
                                nextState = nextState,
                                onPlaybackActionClick = onPlaybackActionClick,
                                onClickPlaybackDialogType = onClickPlaybackDialogType,
                                onSeekBarChange = {
                                    onSeekBarChange(it)
                                    seekProgressMs = it
                                },
                                onChangeState = onChangeState,
                                seekBarInteractionSource = seekBarInteractionSource,
                            )
                        }

                        OverlayViewState.CHAPTERS -> {
                            if (chapters.isNotEmpty()) {
                                RawkoonChapterPane(
                                    player = player,
                                    controllerViewState = controllerViewState,
                                    chapters = chapters,
                                    hasNext = nextEnabled,
                                    onChangeState = onChangeState,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        OverlayViewState.QUEUE -> {
                            if (nextEnabled) {
                                RawkoonQueuePane(
                                    queue = queue,
                                    controllerViewState = controllerViewState,
                                    nextState = if (chapters.isNotEmpty()) OverlayViewState.CHAPTERS else OverlayViewState.CONTROLLER,
                                    onChangeState = onChangeState,
                                    onClickPlaylist = onClickPlaylist,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Debug overlay
        AnimatedVisibility(
            visible = showDebugInfo && controlsVisible,
            enter = fadeIn(RawkoonMotion.enterSpec()),
            exit = fadeOut(RawkoonMotion.exitSpec()),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            val configuration = LocalConfiguration.current
            val height = configuration.screenHeightDp.dp - RawkoonTrayHeight
            PlaybackDebugOverlay(
                analyticsState = analyticsState,
                currentPlayback = currentPlayback,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .heightIn(max = height)
                        .padding(bottom = 16.dp)
                        .background(AppColors.TransparentBlack50)
                        .padding(8.dp)
                        .onFocusChanged {
                            if (it.hasFocus) {
                                // If Debug overlay gains focus, do not hide the controls
                                controllerViewState.pulseControls(Long.MAX_VALUE)
                            } else {
                                controllerViewState.pulseControls()
                            }
                        },
            )
        }
    }
}

/** "Ends at 9:41 PM" text, refreshed every second from the position and playback speed */
@Composable
internal fun rememberEndsAt(player: Player): String {
    val context = LocalContext.current
    var text by remember { mutableStateOf("...") }
    LaunchedEffect(player, context) {
        while (isActive) {
            val remaining = remainingWallMillis(player.duration, player.currentPosition, player.playbackParameters.speed)
            text = formatTime(context, LocalTime.now().plusSeconds(remaining.milliseconds.inWholeSeconds))
            delay(1.seconds)
        }
    }
    return text
}

/**
 * The strip above the video: the series, the title in Fraunces (or the logo when the item has one), the type chip with
 * season, episode and runtime, and on the right the clock in mono and the end time.
 */
@Composable
private fun RawkoonTopStrip(
    item: BaseItem?,
    logoUrl: String?,
    showClock: Boolean,
    endsAt: String,
    modifier: Modifier = Modifier,
) {
    val resources = LocalContext.current.resources
    val isEpisode = item?.type == BaseItemKind.EPISODE
    val seriesName = item?.data?.seriesName?.takeIf { it.isNotBlank() }
    val runtime =
        item
            ?.data
            ?.runTimeTicks
            ?.ticks
            ?.roundMinutes
            ?.let { resources.formatDuration(it) }
    val seasonEpisode =
        if (isEpisode) formatSeasonEpisode(item?.data?.parentIndexNumber, item?.data?.indexNumber) else null
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
        modifier = modifier.fillMaxWidth().padding(start = RawkoonOverlayInset, end = RawkoonOverlayInset, top = 28.dp),
    ) {
        Column(Modifier.weight(1f, fill = false).padding(end = 24.dp)) {
            if (seriesName != null) {
                Text(
                    text = "‹ $seriesName",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = RawkoonTokens.white(0.55f),
                )
            }
            if (logoUrl.isNotNullOrBlank()) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = stringResource(R.string.rawkoon_player_logo),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier.padding(top = 6.dp).widthIn(max = 360.dp).heightIn(max = 64.dp),
                )
            } else if (item?.name.isNotNullOrBlank()) {
                Text(
                    text = item?.name.orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = RawkoonDisplay,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 30.sp,
                    lineHeight = 33.sp,
                    color = RawkoonTokens.Neutral50,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (item != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    TypeChip(
                        stringResource(if (isEpisode) R.string.rawkoon_player_type_episode else R.string.rawkoon_player_type_movie),
                    )
                    listOfNotNull(seasonEpisode, runtime).forEach {
                        Text(
                            text = it,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 13.sp,
                            color = RawkoonTokens.white(0.60f),
                        )
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (showClock) {
                val clock by LocalClock.current.timeString
                RawkoonMonoText(text = clock, fontSize = 22.sp, color = RawkoonTokens.Neutral50)
            }
            Text(
                text = stringResource(R.string.ends_at, endsAt),
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = RawkoonTokens.white(0.55f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * The controls pane of the tray: the seek bar and the three-column control row. Down moves on to [nextState].
 */
@Composable
private fun RawkoonControlPane(
    player: Player,
    controllerViewState: ControllerViewState,
    currentPlayback: CurrentPlayback?,
    showPlay: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekEnabled: Boolean,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    currentSegment: MediaSegmentDto?,
    nextState: OverlayViewState?,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    onSeekBarChange: (Long) -> Unit,
    onChangeState: (OverlayViewState) -> Unit,
    seekBarInteractionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val initialFocusRequester = remember { FocusRequester() }
    val downRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { initialFocusRequester.tryRequestFocus() }
    val onControllerInteraction = { controllerViewState.pulseControls() }
    val columns = remember(previousEnabled, nextEnabled) { controlOrder(previousEnabled, nextEnabled) }
    val audioValue =
        remember(currentPlayback) {
            currentPlayback?.let { playback ->
                playback.mediaSourceInfo.mediaStreams
                    ?.firstOrNull { it.type == MediaStreamType.AUDIO && it.index == playback.audioIndex }
                    ?.let { trackChipValue(it.language?.let(::languageName), it.title, it.displayTitle, it.channelLayout) }
            }
        }
    val subtitleOff = stringResource(R.string.rawkoon_player_off)
    val onlyForced = stringResource(R.string.only_forced_subtitles)
    val subtitleValue =
        remember(currentPlayback, subtitleOff, onlyForced) {
            currentPlayback?.let { playback ->
                if (playback.subtitleIndex == TrackIndex.ONLY_FORCED) {
                    onlyForced
                } else if (playback.subtitleIndexEnabled) {
                    playback.mediaSourceInfo.mediaStreams
                        ?.firstOrNull { it.type == MediaStreamType.SUBTITLE && it.index == playback.subtitleIndex }
                        ?.let { trackChipValue(it.language?.let(::languageName), it.title, it.displayTitle) }
                        ?: subtitleOff
                } else {
                    subtitleOff
                }
            } ?: subtitleOff
        }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = RawkoonOverlayInset).padding(top = 40.dp)) {
        RawkoonSeekSection(
            player = player,
            controllerViewState = controllerViewState,
            onSeekProgress = onSeekBarChange,
            enabled = seekEnabled,
            seekBack = seekBack,
            seekForward = seekForward,
            segment = currentSegment,
            interactionSource = seekBarInteractionSource,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        ) {
            // [1fr | auto | 1fr]: the transport group stays centered on the screen whatever the side groups hold
            columns.forEachIndexed { column, controls ->
                val groupModifier =
                    when (column) {
                        0 -> Modifier.weight(1f)
                        2 -> Modifier.weight(1f)
                        else -> Modifier
                    }
                Box(
                    modifier = groupModifier,
                    contentAlignment =
                        when (column) {
                            0 -> Alignment.CenterStart
                            2 -> Alignment.CenterEnd
                            else -> Alignment.Center
                        },
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .focusGroup()
                                .focusProperties { if (nextState != null) down = downRequester },
                    ) {
                        controls.forEach { control ->
                            RawkoonControl(
                                control = control,
                                player = player,
                                showPlay = showPlay,
                                audioValue = audioValue,
                                subtitleValue = subtitleValue,
                                seekBack = seekBack,
                                seekForward = seekForward,
                                skipBackOnResume = skipBackOnResume,
                                onControllerInteraction = onControllerInteraction,
                                onPlaybackActionClick = onPlaybackActionClick,
                                onClickPlaybackDialogType = onClickPlaybackDialogType,
                                modifier =
                                    Modifier
                                        .onFocusChanged { if (it.hasFocus) onControllerInteraction() }
                                        .let {
                                            if (control == RawkoonPlayerControl.PlayPause) {
                                                it.focusRequester(initialFocusRequester)
                                            } else {
                                                it
                                            }
                                        },
                            )
                        }
                    }
                }
            }
        }
        if (nextState != null) {
            // Down from the controls lands here and slides the tray to the next pane
            HiddenFocusBox(downRequester) { onChangeState(nextState) }
        }
    }
}

@Composable
private fun RawkoonControl(
    control: RawkoonPlayerControl,
    player: Player,
    showPlay: Boolean,
    audioValue: String?,
    subtitleValue: String,
    seekBack: Duration,
    seekForward: Duration,
    skipBackOnResume: Duration?,
    onControllerInteraction: () -> Unit,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (control) {
        RawkoonPlayerControl.Audio -> {
            RawkoonChip(
                label = stringResource(R.string.audio),
                value = audioValue,
                onClick = {
                    onControllerInteraction()
                    onClickPlaybackDialogType(PlaybackDialogType.AUDIO)
                },
                modifier = modifier,
            )
        }

        RawkoonPlayerControl.Subtitles -> {
            RawkoonChip(
                label = stringResource(R.string.subtitles),
                value = subtitleValue,
                onClick = {
                    onControllerInteraction()
                    onClickPlaybackDialogType(PlaybackDialogType.CAPTIONS)
                },
                modifier = modifier,
            )
        }

        RawkoonPlayerControl.Previous -> {
            TransportButton(R.drawable.baseline_skip_previous_24, R.string.rawkoon_player_previous, modifier) {
                onControllerInteraction()
                onPlaybackActionClick(PlaybackAction.Previous)
            }
        }

        RawkoonPlayerControl.SeekBack -> {
            TransportButton(R.drawable.baseline_fast_rewind_24, R.string.rawkoon_player_seek_back, modifier) {
                onControllerInteraction()
                player.seekBack(seekBack)
            }
        }

        RawkoonPlayerControl.PlayPause -> {
            RawkoonPlayButton(
                onClick = {
                    onControllerInteraction()
                    if (showPlay) {
                        player.play()
                        skipBackOnResume?.let { player.seekBack(it) }
                    } else {
                        player.pause()
                    }
                },
                contentDescription = stringResource(if (showPlay) R.string.play else R.string.rawkoon_player_pause),
                icon = ImageVector.vectorResource(if (showPlay) R.drawable.baseline_play_arrow_24 else R.drawable.baseline_pause_24),
                modifier = modifier,
            )
        }

        RawkoonPlayerControl.SeekForward -> {
            TransportButton(R.drawable.baseline_fast_forward_24, R.string.rawkoon_player_seek_forward, modifier) {
                onControllerInteraction()
                player.seekForward(seekForward)
            }
        }

        RawkoonPlayerControl.Next -> {
            RawkoonButton(
                text = stringResource(R.string.rawkoon_player_next_episode),
                variant = RawkoonButtonVariant.Secondary,
                onClick = {
                    onControllerInteraction()
                    onPlaybackActionClick(PlaybackAction.Next)
                },
                modifier = modifier,
            )
        }

        RawkoonPlayerControl.Settings -> {
            TransportButton(R.drawable.vector_settings, R.string.settings, modifier) {
                onControllerInteraction()
                onClickPlaybackDialogType(PlaybackDialogType.SETTINGS)
            }
        }
    }
}

@Composable
private fun TransportButton(
    iconRes: Int,
    descriptionRes: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    RawkoonIconButton(
        icon = ImageVector.vectorResource(iconRes),
        contentDescription = stringResource(descriptionRes),
        onClick = onClick,
        modifier = modifier,
    )
}

package com.github.damontecres.wholphin.rawkoon.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.data.model.PlaylistItem
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonMonoText
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonMotion
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonSurface
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTokens
import com.github.damontecres.wholphin.rawkoon.ui.kit.SectionHeader
import com.github.damontecres.wholphin.rawkoon.ui.kit.rawkoonFocusRing
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.HiddenFocusBox
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.playback.calculateSeekAccelerationMultiplier
import com.github.damontecres.wholphin.ui.playback.isDpadLeft
import com.github.damontecres.wholphin.ui.playback.isDpadRight
import com.github.damontecres.wholphin.ui.playback.overlay.OverlayViewState
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.TrickplayInfo
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Height of the control tray: the pane that slides, so chapters and the queue replace the controls exactly */
internal val RawkoonTrayHeight: Dp = 156.dp

/** Side inset of the tray and the top strip */
internal val RawkoonOverlayInset: Dp = 48.dp

private val TrackHeight = 6.dp
private val KnobRadius = 8.dp

/**
 * The seek bar with the elapsed and remaining times under it. The bar is a 6dp track that never changes height; when
 * focused it gets the Rawkoon ring and a knob at the playhead. Seeking is by [seekBack] and [seekForward] per press,
 * accelerating while a direction is held, as in Wholphin's `IntervalSeekBarImpl`.
 *
 * Replaces Wholphin's `SeekBar`, `IntervalSeekBarImpl` and `SeekTimecodes`.
 */
@Composable
internal fun RawkoonSeekSection(
    player: Player,
    controllerViewState: ControllerViewState,
    onSeekProgress: (Long) -> Unit,
    enabled: Boolean,
    seekBack: Duration,
    seekForward: Duration,
    segment: MediaSegmentDto?,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    var bufferedMs by remember(player) { mutableLongStateOf(player.bufferedPosition) }
    var positionMs by remember(player) { mutableLongStateOf(player.currentPosition) }
    var durationMs by remember(player) { mutableLongStateOf(player.duration) }
    LaunchedEffect(player) {
        while (isActive) {
            bufferedMs = player.bufferedPosition
            positionMs = player.currentPosition
            durationMs = player.duration
            delay(250L)
        }
    }
    val focused by interactionSource.collectIsFocusedAsState()
    var hasSeeked by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableLongStateOf(positionMs) }
    LaunchedEffect(focused) {
        if (!focused) hasSeeked = false
    }
    val shownMs = if (focused && hasSeeked) seekPositionMs else positionMs
    val duration = durationMs.coerceAtLeast(0L)
    val span =
        remember(segment, duration) {
            segment?.let {
                segmentSpan(it.startTicks.ticks.inWholeMilliseconds, it.endTicks.ticks.inWholeMilliseconds, duration)
            }
        }
    val knobScale by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(150, easing = RawkoonMotion.EnterEasing),
        label = "RawkoonKnob",
    )
    val density = LocalDensity.current

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier = modifier) {
            Canvas(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(TrackHeight)
                        .rawkoonFocusRing(CircleShape, focused)
                        .rawkoonSeekKeys(
                            durationMs = duration,
                            onLeft = { multiplier ->
                                controllerViewState.pulseControls()
                                seekPositionMs = (shownMs - seekBack.inWholeMilliseconds * multiplier).coerceAtLeast(0L)
                                hasSeeked = true
                                onSeekProgress(seekPositionMs)
                            },
                            onRight = { multiplier ->
                                controllerViewState.pulseControls()
                                seekPositionMs =
                                    (shownMs + seekForward.inWholeMilliseconds * multiplier).coerceAtMost(duration)
                                hasSeeked = true
                                onSeekProgress(seekPositionMs)
                            },
                        ).focusable(enabled = enabled, interactionSource = interactionSource),
            ) {
                val corner = CornerRadius(size.height / 2)
                drawRoundRect(RawkoonTokens.white(0.14f), size = size, cornerRadius = corner)
                val buffered = fractionOf(bufferedMs, duration)
                drawRoundRect(RawkoonTokens.white(0.22f), size = Size(size.width * buffered, size.height), cornerRadius = corner)
                span?.let {
                    drawRoundRect(
                        RawkoonTokens.Primary300.copy(alpha = 0.35f),
                        topLeft = Offset(size.width * it.start, 0f),
                        size = Size(size.width * (it.end - it.start), size.height),
                        cornerRadius = corner,
                    )
                }
                val progress = fractionOf(shownMs, duration)
                drawRoundRect(RawkoonTokens.Primary500, size = Size(size.width * progress, size.height), cornerRadius = corner)
                if (knobScale > 0f) {
                    drawCircle(
                        color = RawkoonTokens.Neutral50,
                        radius = with(density) { KnobRadius.toPx() } * knobScale,
                        center = Offset(size.width * progress, size.height / 2),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RawkoonMonoText(text = formatClock(shownMs), color = RawkoonTokens.Neutral400)
                RawkoonMonoText(text = "-" + formatClock(duration - shownMs), color = RawkoonTokens.Neutral400)
            }
        }
    }
}

/**
 * D-pad left and right on the seek bar: a press seeks one step, holding repeats with the Wholphin acceleration. Other
 * keys pass through.
 */
private fun Modifier.rawkoonSeekKeys(
    durationMs: Long,
    onLeft: (Int) -> Unit,
    onRight: (Int) -> Unit,
): Modifier =
    composed {
        var leftHandledByRepeat by remember { mutableStateOf(false) }
        var rightHandledByRepeat by remember { mutableStateOf(false) }
        onPreviewKeyEvent { event ->
            val left = isDpadLeft(event)
            if (!left && !isDpadRight(event)) return@onPreviewKeyEvent false
            val step = if (left) onLeft else onRight
            when (event.type) {
                KeyEventType.KeyDown -> {
                    val repeatCount = event.nativeKeyEvent.repeatCount
                    if (repeatCount > 0) {
                        if (left) leftHandledByRepeat = true else rightHandledByRepeat = true
                        step(calculateSeekAccelerationMultiplier(repeatCount = repeatCount, durationMs = durationMs))
                    } else {
                        if (left) leftHandledByRepeat = false else rightHandledByRepeat = false
                    }
                }

                KeyEventType.KeyUp -> {
                    if (!(if (left) leftHandledByRepeat else rightHandledByRepeat)) step(1)
                    if (left) leftHandledByRepeat = false else rightHandledByRepeat = false
                }

                else -> {
                    return@onPreviewKeyEvent false
                }
            }
            true
        }
    }

/**
 * The trickplay preview over the playhead: a 12dp-radius thumbnail with a 1dp ring and shadow, then the time in mono
 * and the distance from where the seek started in apricot. Without trickplay data only the time shows.
 *
 * Replaces Wholphin's `TrickplayPreview` and `SeekPreviewImage`.
 */
@Composable
internal fun RawkoonTrickplayThumb(
    seekProgressMs: Long,
    deltaMs: Long,
    trickplayInfo: TrickplayInfo?,
    trickplayUrlFor: (Int) -> String?,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        if (trickplayInfo != null && trickplayInfo.height > 0 && trickplayInfo.interval > 0) {
            val tilesPerImage = trickplayInfo.tileWidth * trickplayInfo.tileHeight
            val index = (seekProgressMs / trickplayInfo.interval).toInt() / tilesPerImage
            val imageUrl = remember(index) { trickplayUrlFor(index) }
            if (imageUrl != null) {
                TrickplayTile(imageUrl, seekProgressMs, trickplayInfo)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RawkoonMonoText(formatClock(seekProgressMs), fontSize = 13.sp, color = RawkoonTokens.Neutral50)
            seekDeltaLabel(deltaMs)?.let { RawkoonMonoText(it, fontSize = 13.sp, color = RawkoonTokens.Primary300) }
        }
    }
}

private val ThumbHeight = 108.dp

@Composable
private fun TrickplayTile(
    imageUrl: String,
    seekProgressMs: Long,
    info: TrickplayInfo,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(RawkoonTokens.RadiusLarge)
    val width = ThumbHeight * (info.width.toFloat() / info.height)
    val scale = with(LocalDensity.current) { width.toPx() / info.width }
    val model =
        remember(imageUrl) {
            ImageRequest
                .Builder(context)
                .data(imageUrl)
                .size(coil3.size.Size.ORIGINAL)
                .build()
        }
    val painter = rememberAsyncImagePainter(model = model, contentScale = ContentScale.None)
    val tileIndex = (seekProgressMs / info.interval).toInt() % (info.tileHeight * info.tileWidth)
    val x = tileIndex % info.tileWidth
    val y = tileIndex / info.tileWidth
    Canvas(
        modifier =
            Modifier
                .shadow(20.dp, shape)
                .clip(shape)
                .background(Color.Black)
                .border(1.dp, RawkoonTokens.white(0.15f), shape)
                .size(width, ThumbHeight),
    ) {
        with(painter) {
            scale(scale, scale, pivot = Offset.Zero) {
                translate(left = -x.toFloat() * info.width, top = -y.toFloat() * info.height) {
                    draw(size = Size(info.width * info.tileWidth.toFloat(), info.height * info.tileHeight.toFloat()))
                }
            }
        }
    }
}

private val ThumbCardWidth = 150.dp
private val ThumbCardHeight = 84.dp

/** A 16:9 thumbnail with a title and a mono detail under it, the card of the chapter and queue panes */
@Composable
private fun RawkoonThumbCard(
    imageUrl: String?,
    title: String,
    detail: String?,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(RawkoonTokens.RadiusLarge)
    Column(modifier = modifier.width(ThumbCardWidth)) {
        RawkoonSurface(
            onClick = onClick,
            shape = shape,
            container = RawkoonTokens.Neutral700,
            focusedContainer = RawkoonTokens.Neutral700,
            contentColor = RawkoonTokens.Neutral100,
            focusedContentColor = RawkoonTokens.Neutral100,
            border = BorderStroke(1.dp, RawkoonTokens.white(0.10f)),
            interactionSource = interactionSource,
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(ThumbCardWidth, ThumbCardHeight),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Normal,
                color = if (focused) RawkoonTokens.Neutral50 else RawkoonTokens.Neutral300,
                modifier = Modifier.weight(1f),
            )
            if (detail != null) {
                RawkoonMonoText(detail, fontSize = 11.sp, color = RawkoonTokens.Neutral500)
            }
        }
    }
}

/**
 * The chapters pane under the controls: a [SectionHeader] and a row of chapter thumbnails, the one for the current
 * position focused first. Up goes back to the controls, down on to the queue when [hasNext].
 *
 * Replaces Wholphin's `ChapterRowOverlay` and `ChapterCard`.
 */
@Composable
internal fun RawkoonChapterPane(
    player: Player,
    controllerViewState: ControllerViewState,
    chapters: List<Chapter>,
    hasNext: Boolean,
    onChangeState: (OverlayViewState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val interactionSources = remember(chapters.size) { List(chapters.size) { MutableInteractionSource() } }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val chapterIndex =
        remember {
            val position = player.currentPosition.milliseconds
            chapters
                .indexOfFirst { it.position > position }
                .minus(1)
                .let {
                    if (it < 0) {
                        if (position < chapters.first().position) 0 else chapters.lastIndex
                    } else {
                        it
                    }
                }.coerceIn(0, chapters.lastIndex)
        }
    val listState = rememberLazyListState(chapterIndex)
    val focusRequester = remember { FocusRequester() }
    val upRequester = remember { FocusRequester() }
    val downRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        bringIntoViewRequester.bringIntoView()
        focusRequester.tryRequestFocus()
    }
    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    val resources = LocalContext.current.resources
    Column(modifier = modifier.padding(horizontal = RawkoonOverlayInset).padding(top = 4.dp)) {
        HiddenFocusBox(upRequester) { onChangeState(OverlayViewState.CONTROLLER) }
        SectionHeader(title = stringResource(R.string.chapters), trailing = chapters.size.toString())
        Spacer(Modifier.height(8.dp))
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRestorer(focusRequester),
        ) {
            itemsIndexed(chapters) { index, chapter ->
                val interactionSource = interactionSources[index]
                val isFocused by interactionSource.collectIsFocusedAsState()
                LaunchedEffect(isFocused) {
                    if (isFocused) controllerViewState.pulseControls()
                }
                val imageUrl =
                    remember(chapter, density) {
                        imageUrlService.getItemImageUrl(
                            itemId = chapter.itemId,
                            imageType = ImageType.CHAPTER,
                            tag = chapter.tag,
                            imageIndex = chapter.index,
                            fillHeight = with(density) { ThumbCardHeight.roundToPx() },
                        )
                    }
                RawkoonThumbCard(
                    imageUrl = imageUrl,
                    title = chapter.name ?: resources.getString(R.string.rawkoon_player_chapter_fallback, index + 1),
                    detail = formatClock(chapter.position.inWholeMilliseconds),
                    onClick = {
                        player.seekTo(chapter.position.inWholeMilliseconds)
                        controllerViewState.hideControls()
                    },
                    interactionSource = interactionSource,
                    modifier =
                        Modifier
                            .focusProperties {
                                up = upRequester
                                if (hasNext) down = downRequester
                            }.ifElse(
                                index == chapterIndex,
                                Modifier.focusRequester(focusRequester).bringIntoViewRequester(bringIntoViewRequester),
                            ).ifElse(
                                index == 0,
                                Modifier.focusProperties {
                                    left = if (isLtr) FocusRequester.Cancel else FocusRequester.Default
                                    right = if (isLtr) FocusRequester.Default else FocusRequester.Cancel
                                },
                            ),
                )
            }
        }
        if (hasNext) {
            HiddenFocusBox(downRequester) { onChangeState(OverlayViewState.QUEUE) }
        }
    }
}

/**
 * The queue pane: a [SectionHeader] and a row of the upcoming items. Up goes to [nextState].
 *
 * Replaces Wholphin's `QueueRowOverlay` and the `SeasonCard` it shows.
 */
@Composable
internal fun RawkoonQueuePane(
    queue: List<PlaylistItem>,
    controllerViewState: ControllerViewState,
    nextState: OverlayViewState,
    onChangeState: (OverlayViewState) -> Unit,
    onClickPlaylist: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val upRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    Column(modifier = modifier.padding(horizontal = RawkoonOverlayInset).padding(top = 4.dp)) {
        HiddenFocusBox(upRequester) { onChangeState(nextState) }
        SectionHeader(title = stringResource(R.string.queue), trailing = queue.size.toString())
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().focusRestorer(focusRequester),
        ) {
            itemsIndexed(queue) { index, entry ->
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()
                LaunchedEffect(isFocused) {
                    if (isFocused) controllerViewState.pulseControls()
                }
                val item = entry.item
                val imageUrl =
                    remember(item, density) {
                        imageUrlService.getItemImageUrl(
                            item = item,
                            imageType = if (item.type == BaseItemKind.EPISODE) ImageType.PRIMARY else ImageType.THUMB,
                            fillHeight = with(density) { ThumbCardHeight.roundToPx() },
                        )
                    }
                val runtime =
                    item.data.runTimeTicks
                        ?.ticks
                        ?.inWholeMilliseconds
                RawkoonThumbCard(
                    imageUrl = imageUrl,
                    title = item.name ?: item.title.orEmpty(),
                    detail = runtime?.let { formatClock(it) },
                    onClick = {
                        onClickPlaylist(item)
                        controllerViewState.hideControls()
                    },
                    interactionSource = interactionSource,
                    modifier =
                        Modifier
                            .focusProperties { up = upRequester }
                            .ifElse(index == 0, Modifier.focusRequester(focusRequester)),
                )
            }
        }
    }
}

@PreviewTvSpec
@Composable
private fun RawkoonPlayerPartsPreview() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.background(RawkoonTokens.Background).padding(24.dp),
    ) {
        RawkoonTrickplayThumb(seekProgressMs = 3_725_000, deltaMs = 90_000, trickplayInfo = null, trickplayUrlFor = { null })
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RawkoonThumbCard(null, "Opening", "0:00", {}, remember { MutableInteractionSource() })
            RawkoonThumbCard(null, "The harbor", "12:40", {}, remember { MutableInteractionSource() })
        }
    }
}

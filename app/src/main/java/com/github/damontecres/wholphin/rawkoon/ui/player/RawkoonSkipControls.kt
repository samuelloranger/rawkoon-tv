package com.github.damontecres.wholphin.rawkoon.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonMonoText
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonMotion
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonSurface
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTokens
import com.github.damontecres.wholphin.rawkoon.ui.kit.rawkoonEnter
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.skipStringRes
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.MediaSegmentType
import java.util.Locale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

private val SkipShape = RoundedCornerShape(RawkoonTokens.RadiusLarge)

/**
 * The "Skip intro" / "Skip credits" button: a Primary button with a 3dp bar along its bottom edge that shrinks
 * linearly over [windowMillis], the time the page waits before it dismisses itself. Nothing pulses.
 *
 * With [controlsVisible] the button rises above the control tray, animating its offset.
 *
 * Replaces Wholphin's `SkipSegmentButton`. Same parameters, plus [controlsVisible] and [windowMillis].
 */
private val SkipBarHeight = 3.dp
private val SkipBarColor = RawkoonTokens.Neutral950.copy(alpha = 0.35f)

@Composable
fun RawkoonSkipSegmentButton(
    type: MediaSegmentType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    controlsVisible: Boolean = false,
    windowMillis: Long = SKIP_SEGMENT_WINDOW_MILLIS,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val remaining = remember { Animatable(1f) }
    LaunchedEffect(windowMillis) {
        remaining.snapTo(1f)
        remaining.animateTo(0f, tween(windowMillis.toInt(), easing = LinearEasing))
    }
    val lift by animateDpAsState(
        targetValue = if (controlsVisible) RawkoonTrayHeight + 12.dp else 0.dp,
        animationSpec = RawkoonMotion.enterSpec(),
        label = "RawkoonSkipLift",
    )
    RawkoonSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = SkipShape,
        container = RawkoonTokens.Primary600,
        focusedContainer = RawkoonTokens.Primary500,
        contentColor = RawkoonTokens.Neutral950,
        focusedContentColor = RawkoonTokens.Neutral950,
        interactionSource = source,
        modifier = modifier.offset { IntOffset(0, -lift.roundToPx()) },
    ) {
        // The countdown bar is drawn, not laid out: a child that fills the width would make the button as wide as
        // the screen and then shrink with the countdown
        Box(
            Modifier
                .defaultMinSize(minHeight = RawkoonTokens.ButtonHeight)
                .drawWithContent {
                    drawContent()
                    val barHeight = SkipBarHeight.toPx()
                    drawRect(
                        color = SkipBarColor,
                        topLeft = Offset(0f, size.height - barHeight),
                        size = Size(size.width * remaining.value, barHeight),
                    )
                },
        ) {
            Text(
                text = stringResource(type.skipStringRes),
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * The quick-skip chip shown while the controls are hidden: a "Skipped" kicker over the accumulated amount in mono,
 * on the side that was skipped to. It stays 1.5s after the last press and fades out in 120ms, then calls [onFinish].
 *
 * Replaces Wholphin's `SkipIndicator`. Same parameters. The thin progress line along the bottom edge of the minimal
 * D-pad mode stays with the page.
 */
@Composable
fun RawkoonSkipIndicator(
    durationMs: Long,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backward = durationMs < 0
    val finish by rememberUpdatedState(onFinish)
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(durationMs) {
        // Every press restarts the timer, so repeated presses keep the chip up and the total growing
        visible = true
        delay(SKIP_INDICATOR_MILLIS)
        visible = false
        delay(RawkoonMotion.EXIT_MILLIS.toLong())
        finish()
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier = modifier.fillMaxWidth().padding(horizontal = RawkoonOverlayInset)) {
            AnimatedVisibility(
                visible = visible,
                enter = rawkoonEnter(),
                exit = fadeOut(RawkoonMotion.exitSpec()),
                modifier = Modifier.align(if (backward) Alignment.CenterStart else Alignment.CenterEnd),
            ) {
                SkipChip(formatSkipAmount(durationMs))
            }
        }
    }
}

/** The look of a Rawkoon toast with the amount in 17sp mono */
@Composable
private fun SkipChip(amount: String) {
    Column(
        modifier =
            Modifier
                .defaultMinSize(minWidth = 120.dp)
                .shadow(12.dp, SkipShape)
                .clip(SkipShape)
                .background(RawkoonTokens.Neutral800)
                .border(1.dp, RawkoonTokens.Neutral700.copy(alpha = 0.9f), SkipShape),
    ) {
        Text(
            text = stringResource(R.string.rawkoon_player_skipped).uppercase(Locale.getDefault()),
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.15.em,
            color = RawkoonTokens.Neutral500,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 7.dp),
        )
        RawkoonMonoText(
            text = amount,
            fontSize = 17.sp,
            color = RawkoonTokens.Neutral50,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        )
    }
}

@PreviewTvSpec
@Composable
private fun RawkoonSkipPreview() {
    Column(Modifier.background(RawkoonTokens.Background).padding(24.dp)) {
        RawkoonSkipSegmentButton(type = MediaSegmentType.INTRO, onClick = {})
        SkipChip("+30s")
    }
}

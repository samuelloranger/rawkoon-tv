package com.github.damontecres.wholphin.rawkoon.ui.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ListItemBorder
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.ListItemShape
import androidx.tv.material3.NavigationDrawerItemColors
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.rawkoon.RawkoonText
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTokens
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import kotlinx.coroutines.launch

/**
 * The Rawkoon look of the navigation rail: a 64dp `#1C1715` rail with a 1dp right border, 36dp tall rail items with an
 * 8dp radius, muted idle icons, an apricot selected icon on a white@8% background, and the Rawkoon focus ring.
 *
 * It restyles the items of [com.github.damontecres.wholphin.ui.nav.NavDrawer] through the arguments of the drawer item
 * (colors, shape, border), so the drawer structure is untouched.
 */
object RawkoonNav {
    val ItemShape = RoundedCornerShape(RawkoonTokens.RadiusMedium)
    val ItemHeight = 36.dp

    /** Space kept around a rail item so the 4dp focus ring is not clipped by the rail */
    val ItemInset = 4.dp

    val IconSize = 20.dp
    val AvatarSize = 30.dp
    val LogoSize = 28.dp

    /** Label type of an open rail item: 13.5sp medium */
    fun labelStyle(base: TextStyle): TextStyle = base.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium)

    /** Icon color: apricot when selected, light when focused, muted otherwise */
    fun iconColor(
        selected: Boolean,
        focused: Boolean,
    ): Color =
        when {
            selected -> RawkoonTokens.Primary400
            focused -> RawkoonTokens.Neutral50
            else -> RawkoonTokens.Neutral400
        }

    /** Label color of the open rail: light when selected or focused, muted otherwise */
    fun labelColor(
        selected: Boolean,
        focused: Boolean,
    ): Color = if (selected || focused) RawkoonTokens.Neutral50 else RawkoonTokens.Neutral400

    /** Background of an item: white@8% when selected, none otherwise */
    fun containerColor(selected: Boolean): Color = if (selected) RawkoonTokens.white(0.08f) else Color.Transparent

    /** The letter shown in the profile avatar */
    fun avatarInitial(name: String?): String =
        name
            ?.trim()
            ?.firstOrNull { it.isLetterOrDigit() }
            ?.uppercaseChar()
            ?.toString() ?: "?"

    /** Whether the avatar shows the user's image rather than the initial */
    fun showAvatarImage(
        imageUrl: String?,
        loadFailed: Boolean,
    ): Boolean = !imageUrl.isNullOrBlank() && !loadFailed

    @Composable
    fun itemColors(selected: Boolean): NavigationDrawerItemColors {
        val container = containerColor(selected)
        val label = labelColor(selected, focused = false)
        val focusedLabel = labelColor(selected, focused = true)
        return NavigationDrawerItemDefaults.colors(
            containerColor = container,
            contentColor = label,
            inactiveContentColor = label,
            focusedContainerColor = container,
            focusedContentColor = focusedLabel,
            pressedContainerColor = container,
            pressedContentColor = focusedLabel,
        )
    }

    @Composable
    fun itemShape(): ListItemShape = ListItemDefaults.shape(shape = ItemShape)

    /** No built-in focus border: the Rawkoon focus ring is drawn instead */
    @Composable
    fun itemBorder(): ListItemBorder =
        ListItemDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
            pressedBorder = Border.None,
            selectedBorder = Border.None,
            disabledBorder = Border.None,
            focusedSelectedBorder = Border.None,
            focusedDisabledBorder = Border.None,
            pressedSelectedBorder = Border.None,
        )
}

/** The rail surface: app background with a 1dp `#241E1B` border on its right edge */
fun Modifier.rawkoonRailSurface(): Modifier =
    this
        .background(RawkoonTokens.Neutral900)
        .drawBehind {
            val width = 1.dp.toPx()
            drawRect(
                color = RawkoonTokens.Neutral800,
                topLeft = Offset(size.width - width, 0f),
                size = Size(width, size.height),
            )
        }

/** Timing and geometry of the logo row: values of the open/close animation, kept pure so they can be unit tested */
object RawkoonLogoMotion {
    val Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    const val RotationDegrees = 120f
    const val RotationMs = 600
    const val RevealMs = 320
    const val RevealDelayMs = 80
    const val FadeOutMs = 120

    /** Horizontal start of the mark: its center sits on the center of the 20dp item icons (32dp from the rail edge) */
    val MarkStart = 18.dp
    val RowHeight = 56.dp
    val WordmarkGap = 10.dp

    /** The ring angle after one more opening: each opening turns the ring by [RotationDegrees] */
    fun nextRotation(current: Float): Float = current + RotationDegrees

    /** Right edge of the wordmark clip for a reveal [fraction] in 0..1 */
    fun clipRight(
        width: Float,
        fraction: Float,
    ): Float = width * fraction.coerceIn(0f, 1f)
}

// Logo geometry in the 512 unit viewBox of the web icon, cropped to the ring (radius 150 + half the 44 stroke)
private const val LOGO_ORIGIN = 84f
private const val LOGO_EXTENT = 344f
private const val RING_CENTER = 256f
private const val RING_RADIUS = 150f
private const val RING_STROKE = 44f
private const val HOLE_RADIUS = 13f
private val LogoGradientStops =
    arrayOf(
        0f to Color(0xFFF6C88E),
        0.5f to Color(0xFFE8A06A),
        1f to Color(0xFFC45A2F),
    )

/**
 * The Rawkoon logo mark (the web icon without its rounded background): a gradient reel ring with four punched holes
 * and a gradient play triangle. [ringRotation] turns the holes only, as the ring is round and the triangle stays still.
 */
@Composable
fun RawkoonLogoMark(
    modifier: Modifier = Modifier,
    ringRotation: Float = 0f,
) {
    Canvas(modifier.size(RawkoonNav.LogoSize)) {
        val ringBrush =
            Brush.linearGradient(
                colorStops = LogoGradientStops,
                start = Offset(106f + 0.12f * 300f, 106f + 0.08f * 300f),
                end = Offset(106f + 0.88f * 300f, 106f + 0.96f * 300f),
            )
        val playBrush =
            Brush.linearGradient(
                colorStops = LogoGradientStops,
                start = Offset(214f + 0.12f * 128f, 188f + 0.08f * 136f),
                end = Offset(214f + 0.88f * 128f, 188f + 0.96f * 136f),
            )
        val center = Offset(RING_CENTER, RING_CENTER)
        withTransform({
            scale(size.minDimension / LOGO_EXTENT, size.minDimension / LOGO_EXTENT, pivot = Offset.Zero)
            translate(-LOGO_ORIGIN, -LOGO_ORIGIN)
        }) {
            drawCircle(brush = ringBrush, radius = RING_RADIUS, center = center, style = Stroke(RING_STROKE))
            rotate(ringRotation, pivot = center) {
                listOf(
                    Offset(RING_CENTER, RING_CENTER - RING_RADIUS),
                    Offset(RING_CENTER + RING_RADIUS, RING_CENTER),
                    Offset(RING_CENTER, RING_CENTER + RING_RADIUS),
                    Offset(RING_CENTER - RING_RADIUS, RING_CENTER),
                ).forEach { drawCircle(RawkoonTokens.Neutral900, HOLE_RADIUS, it) }
            }
            val triangle =
                Path().apply {
                    moveTo(214f, 188f)
                    lineTo(342f, 256f)
                    lineTo(214f, 324f)
                    close()
                }
            drawPath(triangle, playBrush, style = Fill)
            drawPath(triangle, playBrush, style = Stroke(20f, join = StrokeJoin.Round))
        }
    }
}

/**
 * The logo row at the top of the rail: the mark alone while closed, the mark and the "Rawkoon" wordmark once open. The
 * mark keeps one position in both states. Opening turns the ring and reveals the wordmark left to right; closing only
 * fades the wordmark out.
 */
@Composable
fun RawkoonLogoRow(
    open: Boolean,
    modifier: Modifier = Modifier,
) {
    val rotation = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(open) {
        if (open) {
            launch {
                rotation.animateTo(
                    RawkoonLogoMotion.nextRotation(rotation.value),
                    tween(RawkoonLogoMotion.RotationMs, easing = RawkoonLogoMotion.Easing),
                )
            }
            reveal.snapTo(0f)
            alpha.snapTo(0f)
            launch {
                reveal.animateTo(
                    1f,
                    tween(RawkoonLogoMotion.RevealMs, RawkoonLogoMotion.RevealDelayMs, RawkoonLogoMotion.Easing),
                )
            }
            alpha.animateTo(
                1f,
                tween(RawkoonLogoMotion.RevealMs, RawkoonLogoMotion.RevealDelayMs, RawkoonLogoMotion.Easing),
            )
        } else {
            alpha.animateTo(0f, tween(RawkoonLogoMotion.FadeOutMs, easing = FastOutLinearInEasing))
            reveal.snapTo(0f)
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .height(RawkoonLogoMotion.RowHeight)
                .padding(start = RawkoonLogoMotion.MarkStart),
    ) {
        RawkoonLogoMark(ringRotation = rotation.value)
        Spacer(Modifier.width(RawkoonLogoMotion.WordmarkGap))
        Text(
            text = "Rawkoon",
            color = Color.White,
            fontFamily = RawkoonText,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            letterSpacing = (-0.01).em,
            maxLines = 1,
            softWrap = false,
            modifier =
                Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .graphicsLayer { this.alpha = alpha.value }
                    .drawWithContent {
                        clipRect(right = RawkoonLogoMotion.clipRight(size.width, reveal.value)) {
                            this@drawWithContent.drawContent()
                        }
                    },
        )
    }
}

/**
 * The profile avatar of the rail: 30dp with an 8dp radius. It shows the user's image when there is one, and otherwise
 * the user's initial in `#F0BF93` on `#4F2419`.
 */
@Composable
fun RawkoonProfileAvatar(
    name: String?,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
) {
    var imageError by remember(imageUrl) { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .requiredSize(RawkoonNav.AvatarSize)
                .clip(RawkoonNav.ItemShape)
                .background(RawkoonTokens.Primary950),
    ) {
        if (RawkoonNav.showAvatarImage(imageUrl, imageError)) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                onError = { imageError = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = RawkoonNav.avatarInitial(name),
                color = RawkoonTokens.Primary300,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@PreviewTvSpec
@Composable
private fun RawkoonRailPreview() {
    Box(
        Modifier
            .rawkoonRailSurface()
            .width(64.dp)
            .height(160.dp)
            .padding(top = 12.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        RawkoonLogoRow(open = false)
        RawkoonProfileAvatar("Sam", Modifier.padding(top = 64.dp))
    }
}

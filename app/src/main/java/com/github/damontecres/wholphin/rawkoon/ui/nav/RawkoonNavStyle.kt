package com.github.damontecres.wholphin.rawkoon.ui.nav

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ListItemBorder
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.ListItemShape
import androidx.tv.material3.NavigationDrawerItemColors
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTokens
import com.github.damontecres.wholphin.ui.PreviewTvSpec

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

/** The Rawkoon logo mark: a circle outline with a play triangle in apricot */
@Composable
fun RawkoonLogoMark(modifier: Modifier = Modifier) {
    Canvas(modifier.size(RawkoonNav.LogoSize)) {
        val color = RawkoonTokens.Primary400
        val stroke = 2.dp.toPx()
        drawCircle(color = color, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
        val w = size.width
        val h = size.height
        val triangle =
            Path().apply {
                moveTo(w * 0.40f, h * 0.32f)
                lineTo(w * 0.70f, h * 0.50f)
                lineTo(w * 0.40f, h * 0.68f)
                close()
            }
        drawPath(triangle, color, style = Fill)
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
        contentAlignment = Alignment.TopCenter,
    ) {
        RawkoonLogoMark()
        RawkoonProfileAvatar("Sam", Modifier.padding(top = 48.dp))
    }
}

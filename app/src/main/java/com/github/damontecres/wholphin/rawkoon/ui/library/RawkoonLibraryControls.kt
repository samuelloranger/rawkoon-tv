package com.github.damontecres.wholphin.rawkoon.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.rawkoon.RawkoonDisplay
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonChip
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTokens
import com.github.damontecres.wholphin.rawkoon.ui.kit.rawkoonFocusRing
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec

private val SearchShape = RoundedCornerShape(RawkoonTokens.RadiusLarge)

/**
 * Rawkoon replacement for the body of [com.github.damontecres.wholphin.ui.components.GridTitle]: the page title in
 * Fraunces 34sp, left aligned, with an optional [count] line in neutral 400 under it.
 */
@Composable
fun RawkoonGridTitle(
    title: String,
    modifier: Modifier = Modifier,
    count: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = RawkoonDisplay,
            fontWeight = FontWeight.SemiBold,
            fontSize = 34.sp,
            lineHeight = 38.sp,
            color = RawkoonTokens.Neutral50,
        )
        if (count != null) {
            Text(
                text = count,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp,
                color = RawkoonTokens.Neutral400,
            )
        }
    }
}

/**
 * Rawkoon replacement for [com.github.damontecres.wholphin.ui.components.ExpandableFaButton], with the same
 * parameters: a [RawkoonChip] with the glyph before its label that always shows the label instead of a button that expands when focused.
 */
@Composable
fun RawkoonFaButton(
    @StringRes title: Int,
    @StringRes iconStringRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
) {
    RawkoonChip(
        label = stringResource(title),
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier,
        leading = {
            // The icon string is a Font Awesome glyph, drawn before the label in the chip's own content color
            Text(
                text = stringResource(iconStringRes),
                fontFamily = FontAwesome,
                fontSize = 14.sp,
            )
        },
    )
}

/**
 * Rawkoon replacement for the string overload of [com.github.damontecres.wholphin.ui.components.SearchEditTextBox],
 * with the same parameters: a 12dp radius field on neutral 800 with a neutral 700 border and the Rawkoon focus ring.
 */
@Composable
fun RawkoonSearchEditTextBox(
    value: String,
    onValueChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    height: Dp = 44.dp,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = true,
        maxLines = 1,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = RawkoonTokens.Neutral50),
        cursorBrush = SolidColor(RawkoonTokens.Primary400),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
        keyboardActions =
            KeyboardActions(
                onSearch = {
                    onSearchClick()
                    defaultKeyboardAction(ImeAction.Search)
                },
            ),
        interactionSource = interactionSource,
        modifier =
            modifier
                .defaultMinSize(minWidth = 280.dp, minHeight = height)
                .height(height)
                .rawkoonFocusRing(SearchShape, interactionSource),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(height)
                        .clip(SearchShape)
                        .background(RawkoonTokens.Neutral800)
                        .border(BorderStroke(1.dp, RawkoonTokens.Neutral700), SearchShape)
                        .padding(horizontal = 14.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search),
                    tint = RawkoonTokens.Neutral400,
                    modifier = Modifier.size(20.dp),
                )
                Box(Modifier.weight(1f)) { innerTextField() }
            }
        },
    )
}

@PreviewTvSpec
@Composable
private fun RawkoonLibraryControlsPreview() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.background(RawkoonTokens.Background).padding(24.dp),
    ) {
        RawkoonGridTitle("Movies", count = "248 titles")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RawkoonChip("Name", {}, value = "↑")
            RawkoonChip("Filter", {}, value = "2", selected = true)
            RawkoonFaButton(R.string.view_options, R.string.fa_sliders, {})
        }
        RawkoonSearchEditTextBox(value = "north", onValueChange = {}, onSearchClick = {})
    }
}

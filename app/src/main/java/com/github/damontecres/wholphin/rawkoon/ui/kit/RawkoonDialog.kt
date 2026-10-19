package com.github.damontecres.wholphin.rawkoon.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.rawkoon.RawkoonDisplay
import com.github.damontecres.wholphin.ui.PreviewTvSpec

private val DialogShape = RoundedCornerShape(RawkoonTokens.RadiusXLarge)

/**
 * The Rawkoon dialog: a 16dp-radius panel on neutral 800 with a 1dp border, a Fraunces title and an optional
 * subtitle, holding [content] (usually [RawkoonDialogItem]s).
 *
 * Replaces Wholphin's `DialogPopup` / `DialogPopupContent` and the playback `BottomDialog` look.
 */
@Composable
fun RawkoonDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    properties: DialogProperties = DialogProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        RawkoonDialogPanel(title, subtitle, modifier, content)
    }
}

/** The panel of [RawkoonDialog] without the window, for previews and for dialogs hosted elsewhere */
@Composable
fun RawkoonDialogPanel(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .widthIn(max = 360.dp)
                .shadow(20.dp, DialogShape)
                .background(RawkoonTokens.Neutral800, DialogShape)
                .border(1.dp, RawkoonTokens.Neutral700, DialogShape)
                .padding(18.dp),
    ) {
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = RawkoonDisplay,
            fontWeight = FontWeight.SemiBold,
            fontSize = 19.sp,
            color = Color.White,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.5.sp,
                color = RawkoonTokens.Neutral400,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(top = 12.dp),
            content = content,
        )
    }
}

/**
 * One row of a [RawkoonDialog]: an 8dp-radius row with a check column, the [text] and, when present, a mono [meta] on a second line so the headline is never truncated by it. The
 * selected row is apricot with an orange check, the focused row is raised with the ring.
 */
@Composable
fun RawkoonDialogItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val contentColor = if (selected) RawkoonTokens.Primary400 else RawkoonTokens.Neutral100
    RawkoonSurface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(RawkoonTokens.RadiusMedium),
        container = Color.Transparent,
        focusedContainer = RawkoonTokens.Neutral700,
        contentColor = contentColor,
        focusedContentColor = contentColor,
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = RawkoonTokens.Primary500,
                    modifier = Modifier.width(16.dp),
                )
            } else {
                Spacer(Modifier.width(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                )
                if (meta != null) {
                    RawkoonMonoText(
                        text = meta,
                        fontSize = 11.5.sp,
                        color = RawkoonTokens.Neutral500,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@PreviewTvSpec
@Composable
private fun RawkoonDialogPreview() {
    Column(Modifier.background(RawkoonTokens.Background).padding(24.dp)) {
        RawkoonDialogPanel(title = "Subtitles", subtitle = "Low Tide · S1 E4") {
            RawkoonDialogItem("Off", {})
            RawkoonDialogItem("French", {}, meta = "SRT · forced", selected = true)
            RawkoonDialogItem("English", {}, meta = "PGS")
        }
    }
}

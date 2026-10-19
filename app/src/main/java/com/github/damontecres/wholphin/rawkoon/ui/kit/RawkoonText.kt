package com.github.damontecres.wholphin.rawkoon.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.rawkoon.RawkoonDisplay
import com.github.damontecres.wholphin.rawkoon.RawkoonMono
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import java.util.Locale

/** The small-caps label: uppercase, [fontSize] (10.5sp default) bold, wide tracking, neutral 400 */
@Composable
fun Kicker(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = RawkoonTokens.Neutral400,
    fontSize: TextUnit = 10.5.sp,
) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelSmall,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.15.em,
        color = color,
        modifier = modifier,
    )
}

/** Mono text (Fira Code) for times, timestamps and episode numbers */
@Composable
fun RawkoonMonoText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.5.sp,
    color: Color = RawkoonTokens.Neutral400,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        maxLines = 1,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = RawkoonMono,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
    )
}

/**
 * A section title: a 4x16dp apricot bar, then the title in Fraunces SemiBold. [trailing] is an optional kicker at
 * the end of the row.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(16.dp)
                .background(RawkoonTokens.Primary500, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = RawkoonDisplay,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = RawkoonTokens.Neutral100,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            Kicker(trailing)
        }
    }
}

@PreviewTvSpec
@Composable
private fun SectionHeaderPreview() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.background(RawkoonTokens.Background).padding(16.dp),
    ) {
        SectionHeader("Continue watching")
        SectionHeader("Chapters", trailing = "5")
        RawkoonMonoText("1:12:04")
    }
}

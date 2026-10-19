package com.github.damontecres.wholphin.rawkoon.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.PreviewTvSpec

private val ToastShape = RoundedCornerShape(RawkoonTokens.RadiusLarge)

/**
 * A Rawkoon notification: a [kicker] (such as "Rawkoon"), a [title], an optional [body] and a 2dp countdown bar along
 * the bottom that is [progress] (0 to 1) full. The caller owns the clock: it passes the remaining fraction.
 *
 * Replaces Wholphin's plain snackbar and toast look.
 */
@Composable
fun RawkoonToast(
    kicker: String,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    progress: Float? = null,
) {
    Column(
        modifier =
            modifier
                .shadow(12.dp, ToastShape)
                .clip(ToastShape)
                .background(RawkoonTokens.Neutral800)
                .border(1.dp, RawkoonTokens.Neutral700.copy(alpha = 0.9f), ToastShape),
    ) {
        Kicker(
            text = kicker,
            color = RawkoonTokens.Neutral500,
            fontSize = 9.5.sp,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
        )
        Text(
            text = title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = RawkoonTokens.Neutral50,
            modifier = Modifier.padding(horizontal = 12.dp).padding(top = 6.dp, bottom = if (body == null) 10.dp else 0.dp),
        )
        if (body != null) {
            Text(
                text = body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = RawkoonTokens.Neutral400,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            )
        }
        if (progress != null) {
            ProgressTrack(
                fraction = progress,
                modifier = Modifier.fillMaxWidth().height(2.dp),
                trackColor = RawkoonTokens.Neutral700.copy(alpha = 0.6f),
            )
        }
    }
}

@PreviewTvSpec
@Composable
private fun RawkoonToastPreview() {
    Column(Modifier.background(RawkoonTokens.Background).padding(24.dp)) {
        RawkoonToast(
            kicker = "Rawkoon",
            title = "North of Nine is ready",
            body = "Added to your library",
            progress = 0.62f,
            modifier = Modifier.width(300.dp),
        )
    }
}

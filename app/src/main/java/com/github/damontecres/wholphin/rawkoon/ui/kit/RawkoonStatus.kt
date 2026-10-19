package com.github.damontecres.wholphin.rawkoon.ui.kit

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.PreviewTvSpec

/** The six colors Rawkoon uses for a status: a bar, and a badge made of a 20% background and a 300 text */
enum class RawkoonTone(
    val barColor: Color,
    val badgeBg: Color,
    val badgeText: Color,
) {
    /** ok, downloaded, ready, watched */
    Ok(RawkoonTokens.Emerald400, RawkoonTokens.Emerald500.copy(alpha = BADGE_ALPHA), RawkoonTokens.Emerald300),

    /** progress, downloading, approved */
    Progress(RawkoonTokens.Sky400, RawkoonTokens.Sky500.copy(alpha = BADGE_ALPHA), RawkoonTokens.Sky300),

    /** attention, missing, failed */
    Attention(RawkoonTokens.Rose400, RawkoonTokens.Rose500.copy(alpha = BADGE_ALPHA), RawkoonTokens.Rose300),

    /** requested, pending, in production */
    Requested(RawkoonTokens.Amber400, RawkoonTokens.Amber500.copy(alpha = BADGE_ALPHA), RawkoonTokens.Amber300),

    /** returning series */
    Returning(RawkoonTokens.Primary400, RawkoonTokens.Primary500.copy(alpha = BADGE_ALPHA), RawkoonTokens.Primary300),

    /** neutral, planned */
    Neutral(RawkoonTokens.Neutral400, RawkoonTokens.Neutral700, RawkoonTokens.Neutral300),
}

private const val BADGE_ALPHA = 0.2f

/** What an item or a request is doing, mapped onto a [RawkoonTone] */
enum class RawkoonStatus(
    val tone: RawkoonTone,
    @param:StringRes val labelRes: Int,
) {
    Downloaded(RawkoonTone.Ok, R.string.rawkoon_status_downloaded),
    Ready(RawkoonTone.Ok, R.string.rawkoon_chip_ready),
    Watched(RawkoonTone.Ok, R.string.rawkoon_status_watched),
    Downloading(RawkoonTone.Progress, R.string.rawkoon_chip_downloading),
    Approved(RawkoonTone.Progress, R.string.rawkoon_chip_approved),
    Missing(RawkoonTone.Attention, R.string.rawkoon_status_missing),
    Failed(RawkoonTone.Attention, R.string.rawkoon_status_failed),
    Denied(RawkoonTone.Attention, R.string.rawkoon_chip_denied),
    Requested(RawkoonTone.Requested, R.string.rawkoon_status_requested),
    Pending(RawkoonTone.Requested, R.string.rawkoon_chip_pending),
    InProduction(RawkoonTone.Requested, R.string.rawkoon_status_in_production),
    Returning(RawkoonTone.Returning, R.string.rawkoon_status_returning),
    Planned(RawkoonTone.Neutral, R.string.rawkoon_status_planned),
    ;

    val barColor: Color get() = tone.barColor
    val badgeBg: Color get() = tone.badgeBg
    val badgeText: Color get() = tone.badgeText
}

/** A status badge, rounded full. [label] defaults to the status name. */
@Composable
fun StatusPill(
    status: RawkoonStatus,
    modifier: Modifier = Modifier,
    label: String = stringResource(status.labelRes),
) {
    Text(
        text = label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = status.badgeText,
        modifier =
            modifier
                .background(status.badgeBg, CircleShape)
                .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@PreviewTvSpec
@Composable
private fun StatusPillPreview() {
    Box(Modifier.background(RawkoonTokens.Background).padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                RawkoonStatus.Downloaded,
                RawkoonStatus.Downloading,
                RawkoonStatus.Missing,
                RawkoonStatus.Requested,
                RawkoonStatus.Returning,
                RawkoonStatus.Planned,
            ).forEach { StatusPill(it) }
        }
    }
}

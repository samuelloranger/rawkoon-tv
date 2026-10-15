package com.github.damontecres.wholphin.rawkoon.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.ItemCardImage
import com.github.damontecres.wholphin.ui.cards.ItemRow
import java.time.LocalDate
import java.util.Locale

@Composable
fun UpcomingRowContent(
    title: String,
    cards: List<UpcomingCard>,
    onClick: (UpcomingCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = remember { Locale.getDefault() }
    val today = remember { LocalDate.now() }
    val today1 = stringResource(R.string.rawkoon_today)
    val tomorrow = stringResource(R.string.rawkoon_tomorrow)
    ItemRow(
        title = title,
        items = cards,
        onClickItem = { _, card -> onClick(card) },
        onLongClickItem = { _, _ -> },
        modifier = modifier.fillMaxWidth().focusGroup(),
        cardContent = { _, card, cardModifier, onItemClick, onLongClick ->
            if (card != null) {
                val date =
                    when (val label = RawkoonHomeLogic.dateLabel(card.date, today, locale)) {
                        DateLabel.Today -> today1
                        DateLabel.Tomorrow -> tomorrow
                        is DateLabel.Text -> label.text
                    }
                RawkoonPosterCard(
                    title = card.title,
                    subtitle = listOfNotNull(date, card.episodeLabel).joinToString(" · "),
                    posterUrl = card.posterUrl,
                    chipText = null,
                    chipColor = Color.Transparent,
                    markText = if (card.inLibrary) stringResource(R.string.rawkoon_in_library) else null,
                    onClick = onItemClick,
                    onLongClick = onLongClick,
                    modifier = cardModifier,
                )
            }
        },
    )
}

@Composable
fun RequestsRowContent(
    title: String,
    cards: List<RequestCardData>,
    onClick: (RequestCardData) -> Unit,
    modifier: Modifier = Modifier,
) {
    ItemRow(
        title = title,
        items = cards,
        onClickItem = { _, card -> onClick(card) },
        onLongClickItem = { _, _ -> },
        modifier = modifier.fillMaxWidth().focusGroup(),
        cardContent = { _, card, cardModifier, onItemClick, onLongClick ->
            if (card != null) {
                RawkoonPosterCard(
                    title = card.request.title,
                    subtitle = card.request.year?.toString(),
                    posterUrl = card.request.posterUrl,
                    chipText = chipText(card.chip),
                    chipColor = chipColor(card.chip),
                    markText = null,
                    onClick = onItemClick,
                    onLongClick = onLongClick,
                    modifier = cardModifier,
                )
            }
        },
    )
}

@Composable
private fun chipText(chip: RequestChip): String =
    when (chip) {
        RequestChip.Pending -> stringResource(R.string.rawkoon_chip_pending)
        RequestChip.Approved -> stringResource(R.string.rawkoon_chip_approved)
        is RequestChip.Downloading ->
            chip.percent?.let { stringResource(R.string.rawkoon_chip_downloading_percent, it) }
                ?: stringResource(R.string.rawkoon_chip_downloading)
        RequestChip.Ready -> stringResource(R.string.rawkoon_chip_ready)
        RequestChip.Denied -> stringResource(R.string.rawkoon_chip_denied)
    }

@Composable
private fun chipColor(chip: RequestChip): Color =
    when (chip) {
        RequestChip.Pending -> AppColors.Discover.Yellow
        RequestChip.Approved, is RequestChip.Downloading -> AppColors.Discover.Blue
        RequestChip.Ready -> AppColors.Discover.Green
        RequestChip.Denied -> MaterialTheme.colorScheme.error
    }

/** A poster with the title and a line under it, an optional status chip over the bottom and a mark over the corner */
@Composable
private fun RawkoonPosterCard(
    title: String,
    subtitle: String?,
    posterUrl: String?,
    chipText: String?,
    chipColor: Color,
    markText: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val width = Cards.height2x3 * AspectRatios.TALL
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.size(width, androidx.compose.ui.unit.Dp.Unspecified),
    ) {
        Card(
            modifier = Modifier.size(width, androidx.compose.ui.unit.Dp.Unspecified).aspectRatio(AspectRatios.TALL),
            onClick = onClick,
            onLongClick = onLongClick,
            colors = CardDefaults.colors(containerColor = Color.Transparent),
        ) {
            Box(Modifier.fillMaxSize()) {
                ItemCardImage(
                    imageUrl = posterUrl,
                    name = title,
                    showOverlay = false,
                    favorite = false,
                    watched = false,
                    unwatchedCount = 0,
                    watchedPercent = null,
                    numberOfVersions = -1,
                    useFallbackText = false,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
                markText?.let { Pill(it, Color.Black.copy(alpha = .75f), Modifier.align(Alignment.TopEnd)) }
                chipText?.let { Pill(it, chipColor.copy(alpha = .92f), Modifier.align(Alignment.BottomStart)) }
            }
        }
        Text(
            text = title,
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )
        Text(
            text = subtitle.orEmpty(),
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun Pill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        maxLines = 1,
        style = MaterialTheme.typography.bodySmall,
        fontSize = 10.sp,
        color = Color.White,
        modifier =
            modifier
                .padding(4.dp)
                .background(color, RoundedCornerShape(50))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

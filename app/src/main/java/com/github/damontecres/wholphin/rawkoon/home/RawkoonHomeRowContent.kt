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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonStatus
import com.github.damontecres.wholphin.rawkoon.ui.kit.StatusPill
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
    onFocus: (UpcomingCard, details: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) { onDispose { RawkoonHomePreview.clear() } }
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
                val pill =
                    when (val p = card.pill) {
                        is EpisodePill.Numbers -> p.text
                        is EpisodePill.Count -> pluralStringResource(R.plurals.rawkoon_episodes, p.count, p.count)
                        null -> null
                    }
                val details = listOfNotNull(date, pill).joinToString(" · ")
                RawkoonPosterCard(
                    title = card.title,
                    subtitle = date,
                    posterUrl = card.posterUrl,
                    chipText = pill,
                    chipStatus = RawkoonStatus.Returning,
                    inLibrary = card.inLibrary,
                    onClick = onItemClick,
                    onLongClick = onLongClick,
                    modifier = cardModifier.onFocusChanged { if (it.isFocused) onFocus(card, details) },
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
    onFocus: (RequestCardData, details: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) { onDispose { RawkoonHomePreview.clear() } }
    ItemRow(
        title = title,
        items = cards,
        onClickItem = { _, card -> onClick(card) },
        onLongClickItem = { _, _ -> },
        modifier = modifier.fillMaxWidth().focusGroup(),
        cardContent = { _, card, cardModifier, onItemClick, onLongClick ->
            if (card != null) {
                val chip = chipText(card.chip)
                RawkoonPosterCard(
                    title = card.request.title,
                    subtitle = card.request.year?.toString(),
                    posterUrl = card.request.posterUrl,
                    chipText = chip,
                    chipStatus = card.chip.toStatus(),
                    inLibrary = false,
                    onClick = onItemClick,
                    onLongClick = onLongClick,
                    modifier =
                        cardModifier.onFocusChanged {
                            if (it.isFocused) onFocus(card, listOfNotNull(card.request.year?.toString(), chip).joinToString(" · "))
                        },
                )
            }
        },
    )
}

@Composable
private fun chipText(chip: RequestChip): String =
    when (chip) {
        RequestChip.Pending -> {
            stringResource(R.string.rawkoon_chip_pending)
        }

        RequestChip.Approved -> {
            stringResource(R.string.rawkoon_chip_approved)
        }

        is RequestChip.Downloading -> {
            chip.percent?.let { stringResource(R.string.rawkoon_chip_downloading_percent, it) }
                ?: stringResource(R.string.rawkoon_chip_downloading)
        }

        RequestChip.Ready -> {
            stringResource(R.string.rawkoon_chip_ready)
        }

        RequestChip.Denied -> {
            stringResource(R.string.rawkoon_chip_denied)
        }
    }

/** A poster with the title and a line under it, an optional status chip over the bottom and a mark over the corner */
@Composable
private fun RawkoonPosterCard(
    title: String,
    subtitle: String?,
    posterUrl: String?,
    chipText: String?,
    chipStatus: RawkoonStatus,
    inLibrary: Boolean,
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
                if (inLibrary) {
                    val description = stringResource(R.string.rawkoon_in_library)
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = description,
                        tint = Color.White,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = .65f), CircleShape)
                                .padding(3.dp)
                                .size(18.dp),
                    )
                }
                chipText?.let { StatusPill(chipStatus, Modifier.align(Alignment.BottomStart).padding(6.dp), label = it) }
            }
        }
        Text(
            text = title,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
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

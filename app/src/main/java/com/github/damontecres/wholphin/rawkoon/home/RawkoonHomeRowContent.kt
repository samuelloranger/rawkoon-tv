package com.github.damontecres.wholphin.rawkoon.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonPosterCard
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonStatus
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.ItemRow
import java.time.LocalDate
import java.util.Locale

/** The poster width of the Rawkoon rows, the same as a Jellyfin poster card */
private val POSTER_WIDTH = Cards.height2x3 * AspectRatios.TALL

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
                    imageUrl = card.posterUrl,
                    onClick = onItemClick,
                    onLongClick = onLongClick,
                    year = date,
                    status =
                        when {
                            pill != null -> RawkoonStatus.Returning
                            card.inLibrary -> RawkoonStatus.Downloaded
                            else -> null
                        },
                    statusLabel = pill,
                    modifier = cardModifier.width(POSTER_WIDTH).onFocusChanged { if (it.isFocused) onFocus(card, details) },
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
                    imageUrl = card.request.posterUrl,
                    onClick = onItemClick,
                    onLongClick = onLongClick,
                    year = card.request.year?.toString(),
                    status = card.chip.toStatus(),
                    statusLabel = chip,
                    modifier =
                        cardModifier.width(POSTER_WIDTH).onFocusChanged {
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

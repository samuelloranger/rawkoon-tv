package com.github.damontecres.wholphin.rawkoon.ui.details

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonSurface
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTokens
import com.github.damontecres.wholphin.rawkoon.ui.kit.SectionHeader
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.rememberInt
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.PersonKind

private val PersonCardWidth = 84.dp
private val PersonPhotoSize = 52.dp

/**
 * The cast and crew row: a [SectionHeader] over a row of [RawkoonPersonCard]s.
 *
 * Replaces Wholphin's `ui.cards.PersonRow` (same parameters).
 */
@Composable
fun RawkoonPersonRow(
    people: List<Person>,
    onClick: (Person) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes title: Int = R.string.people_title,
    onLongClick: ((Int, Person) -> Unit)? = null,
) {
    val firstFocus = remember { FocusRequester() }
    var position by rememberInt()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        SectionHeader(
            title = stringResource(title),
            modifier = Modifier.padding(start = 8.dp),
        )
        LazyRow(
            state = rememberLazyListState(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRestorer(firstFocus),
        ) {
            itemsIndexed(people) { index, person ->
                RawkoonPersonCard(
                    person = person,
                    onClick = {
                        position = index
                        onClick.invoke(person)
                    },
                    onLongClick = {
                        position = index
                        onLongClick?.invoke(index, person)
                    },
                    modifier =
                        Modifier
                            .width(PersonCardWidth)
                            .ifElse(index == position, Modifier.focusRequester(firstFocus))
                            .animateItem(),
                )
            }
        }
    }
}

/**
 * A person as a circular 52dp photo with a 1dp ring, the name in 12sp medium and the role in 11sp. The focus ring
 * follows the circle. Without a photo the initial is shown.
 *
 * Replaces Wholphin's `ui.cards.PersonCard(person, ...)`.
 */
@Composable
fun RawkoonPersonCard(
    person: Person,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        RawkoonSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = CircleShape,
            container = RawkoonTokens.Neutral700,
            focusedContainer = RawkoonTokens.Neutral700,
            contentColor = RawkoonTokens.Neutral500,
            focusedContentColor = RawkoonTokens.Neutral500,
            border = BorderStroke(1.dp, RawkoonTokens.Border2),
            interactionSource = interactionSource,
            modifier = Modifier.size(PersonPhotoSize),
        ) {
            Box(Modifier.fillMaxSize().clip(CircleShape), contentAlignment = Alignment.Center) {
                if (person.imageUrl.isNullOrBlank()) {
                    Text(
                        text = person.name?.trim()?.firstOrNull()?.uppercase() ?: "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RawkoonTokens.Neutral500,
                    )
                } else {
                    DetailsImage(person.imageUrl, person.name, Modifier.fillMaxSize())
                }
            }
        }
        Text(
            text = person.name ?: "",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = RawkoonTokens.Neutral300,
            modifier = Modifier.fillMaxWidth(),
        )
        person.role?.let {
            Text(
                text = it,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = RawkoonTokens.Neutral500,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@PreviewTvSpec
@Composable
private fun RawkoonPersonRowPreview() {
    Box(Modifier.background(RawkoonTokens.Background).padding(16.dp)) {
        RawkoonPersonRow(
            people =
                listOf(
                    Person(UUID.randomUUID(), "Jane Doe", "Lead", PersonKind.ACTOR, null, false),
                    Person(UUID.randomUUID(), "John Smith", "Director", PersonKind.DIRECTOR, null, false),
                ),
            onClick = {},
        )
    }
}

package com.github.damontecres.wholphin.rawkoon.ui.player

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.observeState
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.rawkoon.RawkoonDisplay
import com.github.damontecres.wholphin.rawkoon.ui.kit.Kicker
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTokens
import com.github.damontecres.wholphin.rawkoon.ui.kit.TypeChip
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.formatDuration
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import kotlin.time.Duration.Companion.minutes

private val Dim = RawkoonTokens.Neutral900

/**
 * The pause screen. There is no pause icon: once the player has been paused for 8 seconds with the controls hidden,
 * the video dims over 700ms and the detail block of [item] shows at the bottom left. The page removes it from
 * composition on any key press (it only composes this while the controls are hidden).
 *
 * Replaces Wholphin's `PauseIndicator`. Same parameters, minus the animation duration, plus the [item] to describe.
 */
@OptIn(UnstableApi::class)
@Composable
fun RawkoonPauseScreen(
    player: Player,
    modifier: Modifier = Modifier,
    item: BaseItem? = null,
) {
    var paused by remember(player) { mutableStateOf(player.isPausedNow()) }
    LaunchedEffect(player) {
        player
            .observeState(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
            ) {
                paused = player.isPausedNow()
            }.observe()
    }
    var showScreen by remember { mutableStateOf(false) }
    LaunchedEffect(paused) {
        if (paused) {
            delay(PAUSE_SCREEN_DELAY_MILLIS)
            showScreen = true
        } else {
            showScreen = false
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showScreen,
            enter = fadeIn(tween(700)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.00f to Dim.copy(alpha = 0.35f),
                            0.55f to Dim.copy(alpha = 0.75f),
                            1.00f to Dim,
                        ),
                    ).background(Brush.horizontalGradient(0f to Dim.copy(alpha = 0.6f), 1f to Color.Transparent)),
            )
        }
        AnimatedVisibility(
            visible = showScreen && item != null,
            enter = fadeIn(tween(300, delayMillis = 150)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            if (item != null) {
                PauseDetails(item = item, player = player)
            }
        }
    }
}

private fun Player.isPausedNow(): Boolean = !playWhenReady && (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING)

/** The detail hero: poster, kicker, title, type chip with series and episode, time left and the overview */
@Composable
private fun PauseDetails(
    item: BaseItem,
    player: Player,
    modifier: Modifier = Modifier,
) {
    val resources = LocalContext.current.resources
    val density = LocalDensity.current
    val imageUrlService = LocalImageUrlService.current
    val posterUrl =
        remember(item, density) {
            imageUrlService.getItemImageUrl(
                item = item,
                imageType = ImageType.PRIMARY,
                fillHeight = with(density) { PosterHeight.roundToPx() },
                useSeriesForPrimary = true,
            )
        }
    val isEpisode = item.type == BaseItemKind.EPISODE
    val endsAt = rememberEndsAt(player)
    val minutes = remember { minutesLeft(player.duration, player.currentPosition) }
    val detail =
        listOfNotNull(
            if (isEpisode) item.data.seriesName else item.data.productionYear?.toString(),
            if (isEpisode) formatSeasonEpisode(item.data.parentIndexNumber, item.data.indexNumber) else null,
        )
    val timeLeft =
        stringResource(R.string.time_left, resources.formatDuration(minutes.minutes)) +
            " · " + stringResource(R.string.ends_at, endsAt)
    val shape = RoundedCornerShape(RawkoonTokens.RadiusLarge)
    Row(
        horizontalArrangement = Arrangement.spacedBy(26.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.padding(start = RawkoonOverlayInset, end = RawkoonOverlayInset, bottom = 56.dp),
    ) {
        AsyncImage(
            model = posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(PosterWidth, PosterHeight)
                    .shadow(24.dp, shape)
                    .clip(shape)
                    .background(RawkoonTokens.Neutral700)
                    .border(1.dp, RawkoonTokens.white(0.15f), shape),
        )
        Column(Modifier.widthIn(max = 560.dp)) {
            Kicker(text = stringResource(R.string.rawkoon_player_paused), color = RawkoonTokens.Primary300)
            Text(
                text = item.name.orEmpty(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = RawkoonDisplay,
                fontWeight = FontWeight.SemiBold,
                fontSize = 34.sp,
                lineHeight = 36.sp,
                color = RawkoonTokens.Neutral50,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp),
            ) {
                TypeChip(
                    stringResource(if (isEpisode) R.string.rawkoon_player_type_episode else R.string.rawkoon_player_type_movie),
                )
                Text(
                    text = (detail + timeLeft).joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 13.sp,
                    color = RawkoonTokens.white(0.55f),
                )
            }
            item.data.overview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp,
                    color = RawkoonTokens.white(0.72f),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

private val PosterWidth = 116.dp
private val PosterHeight = 174.dp

package com.github.damontecres.wholphin.rawkoon.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import com.github.damontecres.wholphin.data.model.QuickDetailsData
import com.github.damontecres.wholphin.ui.main.HomePageHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the home header shows while a Rawkoon card has focus */
data class RawkoonPreview(
    val title: String,
    /** The date, and the episode line when there is one */
    val details: String,
    val overview: String?,
)

/**
 * The card of a Rawkoon row that has focus. Home's header shows it instead of the last focused Jellyfin item, and
 * the first Jellyfin card to get focus clears it.
 */
object RawkoonHomePreview {
    private val _state = MutableStateFlow<RawkoonPreview?>(null)
    val state: StateFlow<RawkoonPreview?> = _state

    fun show(preview: RawkoonPreview) {
        _state.value = preview
    }

    fun clear() {
        _state.value = null
    }
}

/** The home header: the focused Rawkoon card when there is one, else [default] */
@Composable
fun RawkoonHeaderOr(
    modifier: Modifier,
    default: @Composable () -> Unit,
) {
    val preview by RawkoonHomePreview.state.collectAsState()
    val current = preview
    if (current == null) {
        default()
    } else {
        HomePageHeader(
            title = current.title,
            subtitle = null,
            overview = current.overview,
            overviewTwoLines = false,
            quickDetails = QuickDetailsData(AnnotatedString(current.details)),
            timeRemaining = null,
            endsAt = null,
            showLogo = false,
            logoImageUrl = null,
            modifier = modifier,
        )
    }
}

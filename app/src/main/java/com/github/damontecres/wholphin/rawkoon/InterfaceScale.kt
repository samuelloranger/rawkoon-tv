package com.github.damontecres.wholphin.rawkoon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The interface size: a share of the size the TV reports, applied to dp and sp together
 */
object InterfaceScale {
    val OPTIONS_PERCENT = listOf(100, 90, 85, 80)
    const val DEFAULT_PERCENT = 90

    /** A stored value that is not one of the options (including "not chosen yet", 0) is the default */
    fun normalize(percent: Int): Int = if (percent in OPTIONS_PERCENT) percent else DEFAULT_PERCENT

    fun factor(percent: Int): Float = normalize(percent) / 100f
}

/** The density before the interface size was applied, for sizes that are not part of the interface (player subtitles) */
val LocalBaseDensity = staticCompositionLocalOf<Density?> { null }

/** The density to use for something that must not follow the interface size */
@Composable
fun unscaledDensity(): Density = LocalBaseDensity.current ?: LocalDensity.current

@HiltViewModel
class InterfaceScaleViewModel
    @Inject
    constructor(
        settings: RawkoonSettingsRepository,
    ) : ViewModel() {
        val factor: StateFlow<Float> =
            settings.config
                .map { InterfaceScale.factor(it.interfaceScalePercent) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, InterfaceScale.factor(InterfaceScale.DEFAULT_PERCENT))
    }

/**
 * The outermost composable of the app: everything inside, dialogs and popups included, is laid out at the chosen size
 */
@Composable
fun RawkoonDensity(
    viewModel: InterfaceScaleViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val factor by viewModel.factor.collectAsState()
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalBaseDensity provides base,
        LocalDensity provides Density(base.density * factor, base.fontScale),
        content = content,
    )
}

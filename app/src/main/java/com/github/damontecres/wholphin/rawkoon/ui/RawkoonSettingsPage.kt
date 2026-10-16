package com.github.damontecres.wholphin.rawkoon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.rawkoon.InterfaceScale
import com.github.damontecres.wholphin.rawkoon.RawkoonConfig
import com.github.damontecres.wholphin.ui.preferences.ChoicePreference
import com.github.damontecres.wholphin.ui.preferences.ClickPreference
import com.github.damontecres.wholphin.ui.tryRequestFocus

/**
 * The Rawkoon server and account this device signed in with, and the way to sign out
 */
@Composable
fun RawkoonSettingsPage(
    modifier: Modifier = Modifier,
    viewModel: RawkoonSettingsViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsState(RawkoonConfig())
    val focusRequester = remember { FocusRequester() }
    val notSet = stringResource(R.string.rawkoon_not_set)

    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.rawkoon_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        item {
            ClickPreference(
                title = stringResource(R.string.rawkoon_server),
                summary = config.url.ifBlank { notSet },
                onClick = {},
                modifier = Modifier.focusRequester(focusRequester),
            )
        }
        item {
            ClickPreference(
                title = stringResource(R.string.rawkoon_signed_in_as),
                summary = config.userName.ifBlank { notSet },
                onClick = {},
            )
        }
        item {
            val options = InterfaceScale.OPTIONS_PERCENT
            val selected = options.indexOf(config.interfaceScalePercent).coerceAtLeast(0)
            ChoicePreference(
                title = stringResource(R.string.rawkoon_interface_size),
                summary = "${options[selected]}%",
                possibleValues = options.map { "$it%" },
                selectedIndex = selected,
                onValueChange = { viewModel.setInterfaceScale(options[it]) },
            )
        }
        item {
            ClickPreference(
                title = stringResource(R.string.rawkoon_credits_title),
                summary = stringResource(R.string.rawkoon_credits),
                onClick = {},
            )
        }
        item {
            ClickPreference(
                title = stringResource(R.string.license_info),
                summary = stringResource(R.string.rawkoon_fonts),
                onClick = {},
            )
        }
        item {
            ClickPreference(
                title = stringResource(R.string.rawkoon_sign_out),
                summary = stringResource(R.string.rawkoon_sign_out_summary),
                onClick = viewModel::signOut,
            )
        }
    }
}

package com.github.damontecres.wholphin.rawkoon.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.rawkoon.OnboardingStep
import com.github.damontecres.wholphin.rawkoon.RawkoonStep
import com.github.damontecres.wholphin.rawkoon.ui.QrCode
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.tryRequestFocus

@Composable
private fun SetupColumn(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(0.6f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            content()
        }
    }
}

@Composable
private fun ErrorText(
    @StringRes res: Int?,
) {
    if (res != null) {
        Text(
            text = stringResource(res),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The Rawkoon-first start: server, sign-in, then setup, whichever the stored state calls for
 */
@Composable
fun RawkoonFlowContent(
    modifier: Modifier = Modifier,
    viewModel: RawkoonFlowViewModel = hiltViewModel(),
) {
    val step by viewModel.step.collectAsState()
    when (step) {
        RawkoonStep.URL -> RawkoonUrlContent(modifier)
        RawkoonStep.SIGN_IN -> RawkoonSignInContent(modifier)
        RawkoonStep.SETUP -> RawkoonSetupContent(modifier)
        null -> Box(modifier)
    }
}

/**
 * Which Rawkoon server
 */
@Composable
fun RawkoonUrlContent(
    modifier: Modifier = Modifier,
    viewModel: RawkoonUrlViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    SetupColumn(stringResource(R.string.rawkoon_url_title), modifier) {
        Text(
            text = stringResource(R.string.rawkoon_url_instructions),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        EditTextBox(
            value = state.url,
            onValueChange = viewModel::setUrl,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
            keyboardActions = KeyboardActions(onGo = { viewModel.submit() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
        ErrorText(state.error)
        if (state.checking) {
            CircularProgress(Modifier.size(24.dp))
        } else {
            TextButton(
                stringRes = R.string.rawkoon_continue,
                onClick = viewModel::submit,
                enabled = state.url.isNotBlank(),
            )
        }
    }
}

/**
 * Sign in to Rawkoon: a code to approve on a phone, or email and password
 */
@Composable
fun RawkoonSignInContent(
    modifier: Modifier = Modifier,
    viewModel: RawkoonSignInViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    DisposableEffect(Unit) {
        viewModel.startDeviceCode()
        onDispose { viewModel.stopDeviceCode() }
    }
    SetupColumn(stringResource(R.string.rawkoon_sign_in_title), modifier) {
        if (state.emailForm) {
            EmailForm(state, viewModel)
        } else {
            DeviceCodePanel(state)
            ErrorText(state.error)
            if (state.denied || state.error != null) {
                TextButton(stringRes = R.string.rawkoon_try_again, onClick = viewModel::startDeviceCode)
            }
            TextButton(stringRes = R.string.rawkoon_sign_in_with_email, onClick = { viewModel.showEmailForm(true) })
        }
        TextButton(stringRes = R.string.rawkoon_change_server, onClick = viewModel::changeServer)
    }
}

@Composable
private fun DeviceCodePanel(state: RawkoonSignInState) {
    val code = state.code
    if (state.denied) {
        Text(
            text = stringResource(R.string.rawkoon_sign_in_denied),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium,
        )
        return
    }
    if (code == null) {
        if (state.error == null) CircularProgress(Modifier.size(24.dp))
        return
    }
    Text(
        text = stringResource(R.string.rawkoon_sign_in_instructions),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        code.verificationUriComplete?.let { QrCode(it, size = 200.dp) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = code.displayCode,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = code.verificationUri,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun EmailForm(
    state: RawkoonSignInState,
    viewModel: RawkoonSignInViewModel,
) {
    val email = rememberTextFieldState()
    val password = rememberTextFieldState()
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { emailFocus.tryRequestFocus() }
    val submit = { viewModel.signInWithEmail(email.text.toString(), password.text.toString()) }
    Text(
        text = stringResource(R.string.rawkoon_email),
        color = MaterialTheme.colorScheme.onSurface,
    )
    EditTextBox(
        state = email,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
        onKeyboardAction = { passwordFocus.tryRequestFocus() },
        modifier = Modifier.fillMaxWidth().focusRequester(emailFocus),
    )
    Text(
        text = stringResource(R.string.password),
        color = MaterialTheme.colorScheme.onSurface,
    )
    EditTextBox(
        state = password,
        isPassword = true,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go,
            ),
        onKeyboardAction = { submit() },
        modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus),
    )
    ErrorText(state.error)
    if (state.busy) {
        CircularProgress(Modifier.size(24.dp))
    } else {
        TextButton(stringRes = R.string.login, onClick = { submit() })
    }
    TextButton(stringRes = R.string.rawkoon_sign_in_with_code, onClick = { viewModel.showEmailForm(false) })
}

/**
 * Progress while the app reads its setup from Rawkoon and signs in to Jellyfin, with the way out when that fails
 */
@Composable
fun RawkoonSetupContent(
    modifier: Modifier = Modifier,
    viewModel: RawkoonSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.start() }
    SetupColumn(stringResource(R.string.rawkoon_setup_title), modifier) {
        val error = state.error
        if (error == null) {
            CircularProgress(Modifier.size(32.dp))
            Text(
                text =
                    stringResource(
                        when (state.step) {
                            OnboardingStep.ADDING_SERVER -> R.string.rawkoon_setup_server
                            OnboardingStep.SIGNING_IN_JELLYFIN -> R.string.rawkoon_setup_jellyfin
                            else -> R.string.rawkoon_setup_account
                        },
                    ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            val retryFocus = remember { FocusRequester() }
            LaunchedEffect(error) { retryFocus.tryRequestFocus() }
            ErrorText(error)
            if (state.retryable) {
                TextButton(
                    stringRes = R.string.rawkoon_try_again,
                    onClick = viewModel::start,
                    modifier = Modifier.focusRequester(retryFocus),
                )
            }
            TextButton(
                stringRes = R.string.rawkoon_sign_out,
                onClick = viewModel::signOut,
                modifier = if (state.retryable) Modifier else Modifier.focusRequester(retryFocus),
            )
        }
    }
}

package com.github.damontecres.wholphin.rawkoon.ui.update

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.rawkoon.RawkoonDisplay
import com.github.damontecres.wholphin.rawkoon.RawkoonMono
import com.github.damontecres.wholphin.rawkoon.ui.kit.Kicker
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonButton
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonButtonVariant
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonMonoText
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTokens
import com.github.damontecres.wholphin.rawkoon.ui.kit.SectionHeader
import com.github.damontecres.wholphin.rawkoon.ui.kit.rawkoonEnter
import com.github.damontecres.wholphin.rawkoon.ui.kit.rawkoonExit
import com.github.damontecres.wholphin.services.Release
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.formatBytes
import com.github.damontecres.wholphin.ui.setup.UpdateViewModel
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.Version
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.coroutines.launch

/**
 * The in-app update page in the Rawkoon style: the version jump and the actions on the left, the release notes in a
 * scrollable panel on the right, and the download progress in place of the buttons instead of a dialog.
 *
 * Replaces Wholphin's `ui.setup.InstallUpdatePage` (same parameters, same [UpdateViewModel]).
 */
@Composable
fun RawkoonInstallUpdatePage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    OneTimeLaunchedEffect { viewModel.init() }

    val state by viewModel.state.collectAsState()
    var permissions by remember { mutableStateOf(viewModel.updater.hasPermissions()) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            permissions = isGranted
        }
    val onBack = { viewModel.navigationManager.goBack() }
    // Back during a download cancels it and stays on the page, like the old download dialog did
    BackHandler(enabled = state.downloading) { viewModel.cancelDownload() }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(RawkoonTokens.Background)
                .padding(horizontal = 56.dp, vertical = 40.dp),
    ) {
        when (val st = state.loading) {
            is LoadingState.Error -> {
                UpdateMessage(
                    kicker = stringResource(R.string.rawkoon_update_error_kicker),
                    title = stringResource(R.string.rawkoon_update_error_title),
                    body = st.localizedMessage.ifBlank { stringResource(R.string.rawkoon_update_error_unknown) },
                    onRetry = { viewModel.init() },
                    onBack = onBack,
                )
            }

            LoadingState.Loading, LoadingState.Pending -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Kicker(stringResource(R.string.rawkoon_update_checking))
                }
            }

            LoadingState.Success -> {
                val release = state.release
                if (release == null) {
                    UpdateMessage(
                        kicker = stringResource(R.string.rawkoon_update_error_kicker),
                        title = stringResource(R.string.rawkoon_update_none_title),
                        body = stringResource(R.string.rawkoon_update_none_body),
                        onRetry = { viewModel.init() },
                        onBack = onBack,
                    )
                } else {
                    val bytesDownloaded by viewModel.bytesDownloaded.collectAsState(0L)
                    RawkoonInstallUpdateContent(
                        installed = viewModel.currentVersion,
                        release = release,
                        downloading = state.downloading,
                        bytesDownloaded = bytesDownloaded,
                        contentLength = state.contentLength,
                        onInstall = {
                            if (!permissions) {
                                launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                viewModel.installRelease(release)
                            }
                        },
                        onCancelDownload = { viewModel.cancelDownload() },
                        onBack = onBack,
                    )
                }
            }
        }
    }
}

@Composable
fun RawkoonInstallUpdateContent(
    installed: Version,
    release: Release,
    downloading: Boolean,
    bytesDownloaded: Long,
    contentLength: Long,
    onInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = updateKind(installed, release)
    val notesState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Row(
        horizontalArrangement = Arrangement.spacedBy(40.dp),
        modifier =
            modifier
                .fillMaxSize()
                // The notes never take focus: focus stays on the buttons, and up and down scroll the notes
                .onPreviewKeyEvent {
                    if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val delta =
                        when (it.key) {
                            Key.DirectionDown -> NOTES_SCROLL_STEP
                            Key.DirectionUp -> -NOTES_SCROLL_STEP
                            else -> return@onPreviewKeyEvent false
                        }
                    if (delta < 0 && !notesState.canScrollBackward) return@onPreviewKeyEvent false
                    if (delta > 0 && !notesState.canScrollForward) return@onPreviewKeyEvent false
                    scope.launch(ExceptionHandler()) { notesState.animateScrollBy(delta) }
                    true
                },
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier =
                Modifier
                    .weight(0.44f)
                    .fillMaxHeight(),
        ) {
            Kicker(
                text =
                    stringResource(
                        if (kind == UpdateKind.AVAILABLE) R.string.update_available else R.string.rawkoon_update_up_to_date,
                    ),
                color = if (kind == UpdateKind.AVAILABLE) RawkoonTokens.Primary300 else RawkoonTokens.Emerald300,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.rawkoon_update_title, release.version.toString()),
                fontFamily = RawkoonDisplay,
                fontWeight = FontWeight.SemiBold,
                fontSize = 38.sp,
                lineHeight = 42.sp,
                color = RawkoonTokens.Neutral50,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            VersionJump(installed = installed, release = release, kind = kind)
            formatPublishedDate(release.publishedAt)?.let { date ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.rawkoon_update_published, date),
                    fontSize = 13.sp,
                    color = RawkoonTokens.Neutral400,
                )
            }
            Spacer(Modifier.height(28.dp))
            val enter = rawkoonEnter()
            val exit = rawkoonExit()
            AnimatedContent(
                targetState = downloading,
                transitionSpec = { enter togetherWith exit },
                label = "update_actions",
            ) { isDownloading ->
                if (isDownloading) {
                    DownloadProgress(
                        bytesDownloaded = bytesDownloaded,
                        contentLength = contentLength,
                        onCancel = onCancelDownload,
                    )
                } else {
                    UpdateActions(
                        kind = kind,
                        version = release.version,
                        canDownload = release.downloadUrl != null,
                        onInstall = onInstall,
                        onBack = onBack,
                    )
                }
            }
            if (release.downloadUrl == null && kind == UpdateKind.AVAILABLE) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.rawkoon_update_no_asset),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = RawkoonTokens.Rose300,
                    modifier = Modifier.widthIn(max = 380.dp),
                )
            } else if (kind == UpdateKind.AVAILABLE || downloading) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.rawkoon_update_hint),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = RawkoonTokens.Neutral500,
                    modifier = Modifier.widthIn(max = 380.dp),
                )
            }
        }
        // The panel hugs short notes and scrolls long ones, centered against the left column
        ReleaseNotesPanel(
            release = release,
            listState = notesState,
            modifier =
                Modifier
                    .weight(0.56f)
                    .align(Alignment.CenterVertically)
                    .heightIn(max = 460.dp),
        )
    }
}

/** `v2.5.0 → v2.6.0`: the installed version as a neutral pill, the release as an apricot one */
@Composable
private fun VersionJump(
    installed: Version,
    release: Release,
    kind: UpdateKind,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VersionPill(
            text = installed.toString(),
            background = RawkoonTokens.white(0.08f),
            border = RawkoonTokens.white(0.12f),
            color = RawkoonTokens.Neutral300,
        )
        if (kind == UpdateKind.AVAILABLE) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = RawkoonTokens.Neutral500,
                modifier = Modifier.size(16.dp),
            )
            VersionPill(
                text = release.version.toString(),
                background = RawkoonTokens.Primary500.copy(alpha = 0.15f),
                border = RawkoonTokens.Primary500.copy(alpha = 0.30f),
                color = RawkoonTokens.Primary200,
            )
        }
    }
}

@Composable
private fun VersionPill(
    text: String,
    background: Color,
    border: Color,
    color: Color,
) {
    val shape = RoundedCornerShape(RawkoonTokens.RadiusMedium)
    Box(
        Modifier
            .background(background, shape)
            .border(1.dp, border, shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        RawkoonMonoText(text = text, fontSize = 14.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun UpdateActions(
    kind: UpdateKind,
    version: Version,
    canDownload: Boolean,
    onInstall: () -> Unit,
    onBack: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (kind == UpdateKind.AVAILABLE) {
            RawkoonButton(
                text = stringResource(R.string.download_and_update),
                onClick = onInstall,
                variant = RawkoonButtonVariant.Primary,
                icon = DownloadIcon,
                enabled = canDownload,
                modifier = if (canDownload) Modifier.focusRequester(focusRequester) else Modifier,
            )
            RawkoonButton(
                text = stringResource(R.string.rawkoon_update_later),
                onClick = onBack,
                modifier = if (canDownload) Modifier else Modifier.focusRequester(focusRequester),
            )
        } else {
            RawkoonButton(
                text = stringResource(R.string.rawkoon_update_back),
                onClick = onBack,
                variant = RawkoonButtonVariant.Primary,
                modifier = Modifier.focusRequester(focusRequester),
            )
            if (canDownload) {
                RawkoonButton(
                    text = stringResource(R.string.rawkoon_update_reinstall),
                    onClick = onInstall,
                    icon = DownloadIcon,
                    meta = version.toString(),
                )
            }
        }
    }
}

/** The download in place of the buttons: a bar, the bytes and the percent in Fira Code, and Cancel */
@Composable
private fun DownloadProgress(
    bytesDownloaded: Long,
    contentLength: Long,
    onCancel: () -> Unit,
) {
    val fraction = downloadFraction(bytesDownloaded, contentLength)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.widthIn(max = 380.dp),
    ) {
        Kicker(
            text =
                stringResource(
                    if (fraction == null) R.string.rawkoon_update_starting else R.string.downloading,
                ),
            color = RawkoonTokens.Primary300,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(RawkoonTokens.white(0.10f), CircleShape),
        ) {
            if (fraction != null) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .background(RawkoonTokens.Primary500, CircleShape),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RawkoonMonoText(
                text =
                    if (fraction != null) {
                        "${formatBytes(bytesDownloaded)} / ${formatBytes(contentLength)}"
                    } else {
                        formatBytes(bytesDownloaded)
                    },
                color = RawkoonTokens.Neutral300,
            )
            if (fraction != null) {
                RawkoonMonoText(text = "${downloadPercent(fraction)}%", color = RawkoonTokens.Neutral100)
            }
        }
        Spacer(Modifier.height(6.dp))
        RawkoonButton(
            text = stringResource(R.string.cancel),
            onClick = onCancel,
            modifier = Modifier.focusRequester(focusRequester),
        )
    }
}

/**
 * The release notes in a bordered panel. The panel never takes focus: focus stays on the buttons, and the page scrolls
 * [listState] with up and down.
 */
@Composable
private fun ReleaseNotesPanel(
    release: Release,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(RawkoonTokens.RadiusLarge)
    val scrollable = listState.canScrollForward || listState.canScrollBackward
    Column(
        modifier =
            modifier
                .background(RawkoonTokens.Neutral800, shape)
                .border(1.dp, RawkoonTokens.Neutral700, shape),
    ) {
        SectionHeader(
            title = stringResource(R.string.rawkoon_update_whats_new),
            trailing = if (scrollable) stringResource(R.string.rawkoon_update_scroll_hint) else null,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(RawkoonTokens.Neutral700),
        )
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item { RawkoonReleaseNotes(release) }
        }
    }
}

@Composable
private fun RawkoonReleaseNotes(release: Release) {
    val body = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp)
    Markdown(
        content = releaseNotesBody(release),
        colors =
            markdownColor(
                text = RawkoonTokens.Neutral100,
                codeBackground = RawkoonTokens.Neutral900,
                inlineCodeBackground = RawkoonTokens.Neutral900,
                dividerColor = RawkoonTokens.Neutral700,
            ),
        typography =
            markdownTypography(
                h1 = body.copy(fontFamily = RawkoonDisplay, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
                h2 = body.copy(fontFamily = RawkoonDisplay, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                h3 = body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = RawkoonTokens.Neutral50),
                text = body,
                paragraph = body,
                bullet = body,
                list = body,
                ordered = body,
                code = body.copy(fontFamily = RawkoonMono, fontSize = 12.5.sp),
                inlineCode = body.copy(fontFamily = RawkoonMono, fontSize = 12.5.sp),
            ),
    )
}

/** The error and "no release" states: an icon, a Fraunces title, the reason, then Retry and Back */
@Composable
private fun UpdateMessage(
    kicker: String,
    title: String,
    body: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(56.dp)
                    .background(RawkoonTokens.Rose500.copy(alpha = 0.15f), CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = RawkoonTokens.Rose300,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Kicker(kicker)
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            fontFamily = RawkoonDisplay,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            color = RawkoonTokens.Neutral50,
        )
        if (!body.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = RawkoonTokens.Neutral400,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 520.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RawkoonButton(
                text = stringResource(R.string.rawkoon_update_retry),
                onClick = onRetry,
                variant = RawkoonButtonVariant.Primary,
                icon = Icons.Filled.Refresh,
                modifier = Modifier.focusRequester(focusRequester),
            )
            RawkoonButton(text = stringResource(R.string.rawkoon_update_back), onClick = onBack)
        }
    }
}

/** How far one press of up or down scrolls the release notes */
private const val NOTES_SCROLL_STEP = 140f

/** Material's "download" glyph, which is not in the core icon set */
private val DownloadIcon =
    materialIcon(name = "Filled.Download") {
        materialPath {
            moveTo(5f, 20f)
            horizontalLineToRelative(14f)
            verticalLineToRelative(-2f)
            horizontalLineTo(5f)
            verticalLineToRelative(2f)
            close()
            moveTo(19f, 9f)
            horizontalLineToRelative(-4f)
            verticalLineTo(3f)
            horizontalLineTo(9f)
            verticalLineToRelative(6f)
            horizontalLineTo(5f)
            lineToRelative(7f, 7f)
            lineToRelative(7f, -7f)
            close()
        }
    }

private val previewRelease =
    Release(
        version = Version.fromString("v2.6.0"),
        downloadUrl = "https://example.invalid/app.apk",
        publishedAt = "2026-10-18T14:00:00Z",
        body =
            "## Features\n" +
                "- Every screen in the Rawkoon style\n" +
                "- A quieter player\n\n" +
                "## Fixes\n" +
                "- The skip prompt no longer swallows Back",
        notes = listOf(),
    )

@PreviewTvSpec
@Composable
private fun RawkoonInstallUpdateContentPreview() {
    WholphinTheme {
        Box(Modifier.background(RawkoonTokens.Background).padding(40.dp)) {
            RawkoonInstallUpdateContent(
                installed = Version.fromString("v2.5.0"),
                release = previewRelease,
                downloading = false,
                bytesDownloaded = 0L,
                contentLength = -1L,
                onInstall = {},
                onCancelDownload = {},
                onBack = {},
            )
        }
    }
}

@PreviewTvSpec
@Composable
private fun RawkoonInstallUpdateDownloadingPreview() {
    WholphinTheme {
        Box(Modifier.background(RawkoonTokens.Background).padding(40.dp)) {
            RawkoonInstallUpdateContent(
                installed = Version.fromString("v2.5.0"),
                release = previewRelease,
                downloading = true,
                bytesDownloaded = 23_000_000L,
                contentLength = 54_000_000L,
                onInstall = {},
                onCancelDownload = {},
                onBack = {},
            )
        }
    }
}

package com.github.damontecres.wholphin.rawkoon.ui.update

import com.github.damontecres.wholphin.services.Release
import com.github.damontecres.wholphin.util.Version
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** What the update page offers for a release */
enum class UpdateKind {
    /** The release is newer than the installed version */
    AVAILABLE,

    /** The installed version is the release or newer, so the page offers a reinstall */
    UP_TO_DATE,
}

fun updateKind(
    installed: Version,
    release: Release,
): UpdateKind = if (release.version.isGreaterThan(installed)) UpdateKind.AVAILABLE else UpdateKind.UP_TO_DATE

/** Download progress between 0 and 1, or null while the size is unknown */
fun downloadFraction(
    bytesDownloaded: Long,
    contentLength: Long,
): Float? {
    if (contentLength <= 0L) return null
    return (bytesDownloaded.toFloat() / contentLength).coerceIn(0f, 1f)
}

/** Whole percent for [fraction], for the label next to the bar */
fun downloadPercent(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * 100).toInt()

/**
 * The release notes without the `# <version>` heading that [Release.content] starts with, since the page already
 * shows the version as its title.
 */
fun releaseNotesBody(release: Release): String =
    release.content
        .removePrefix("# ${release.version}")
        .trimStart('\n', ' ')

/** [publishedAt] (ISO-8601 from the release feed) as a medium localized date, or null when missing or unreadable */
fun formatPublishedDate(
    publishedAt: String?,
    locale: Locale = Locale.getDefault(),
): String? {
    if (publishedAt.isNullOrBlank()) return null
    return try {
        OffsetDateTime
            .parse(publishedAt)
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    } catch (_: Exception) {
        null
    }
}

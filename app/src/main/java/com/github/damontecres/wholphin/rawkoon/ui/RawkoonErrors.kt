package com.github.damontecres.wholphin.rawkoon.ui

import androidx.annotation.StringRes
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.rawkoon.RawkoonException

@StringRes
fun RawkoonException.messageRes(): Int =
    when (this) {
        is RawkoonException.NotConfigured -> R.string.rawkoon_error_not_configured
        is RawkoonException.Unauthorized -> R.string.rawkoon_error_unauthorized
        is RawkoonException.InvalidCredentials -> R.string.rawkoon_error_credentials
        is RawkoonException.RateLimited -> R.string.rawkoon_error_rate_limited
        is RawkoonException.NotLinked -> R.string.rawkoon_error_not_linked
        is RawkoonException.NoJellyfinIntegration -> R.string.rawkoon_error_no_integration
        is RawkoonException.QuickConnectExpired -> R.string.rawkoon_error_quick_connect
        is RawkoonException.JellyfinUnreachable -> R.string.rawkoon_error_jellyfin_unreachable
        is RawkoonException.InvalidSettings -> R.string.rawkoon_error_invalid_url
        is RawkoonException.Unreachable -> R.string.rawkoon_error_unreachable
        is RawkoonException.Http -> R.string.rawkoon_error_http
        is RawkoonException.InvalidResponse -> R.string.rawkoon_error_invalid
    }

/**
 * Trim, drop invisible characters and add https:// when no scheme was typed
 */
fun normalizeRawkoonUrl(value: String): String {
    val trimmed = value.filterNot { it.isISOControl() || it in ZERO_WIDTH }.trim()
    if (trimmed.isEmpty() || trimmed.contains("://")) return trimmed
    return "https://$trimmed"
}

private val ZERO_WIDTH = setOf('\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF')

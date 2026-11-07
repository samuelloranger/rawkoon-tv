package cloud.rawkoon.tv.screens

import android.content.Context
import android.os.Build
import android.view.Display
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import cloud.rawkoon.tv.BuildConfig
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.Screen
import cloud.rawkoon.tv.data.HdrOverride
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.playback.DisplayModeInfo
import cloud.rawkoon.tv.playback.HdrStatus
import cloud.rawkoon.tv.playback.ProfileSettings
import cloud.rawkoon.tv.playback.formatRate
import cloud.rawkoon.tv.playback.groupModes
import cloud.rawkoon.tv.playback.hdrTypeNames
import cloud.rawkoon.tv.playback.profile.MediaCodecCapabilitiesTest
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.SettingRow
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.applyClockSetting
import cloud.rawkoon.tv.ui.clock
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.label
import cloud.rawkoon.tv.ui.noClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.systemApi

/**
 * What the TV and the app report about themselves, for working out why a file transcodes or plays
 * without HDR. Read only: the rows take the focus so that Up and Down scroll the list. Back leaves.
 * The decoder probes and the server's version load after the page is up, and show "—" until then.
 */
class DeviceInfoScreen(activity: MainActivity, private val profileId: String?) : Screen(activity) {
    override val view = FrameLayout(context)
    private val rows = mutableListOf<SettingRow>()
    private val clockView = clock(context).apply { applyClockSetting() }
    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, context.dp(40))
    }.noClip()

    init {
        header(R.string.info_device)
        row(R.string.info_model, "${Build.MANUFACTURER} ${Build.MODEL}")
        row(R.string.info_android, string(R.string.info_android_value, Build.VERSION.RELEASE, Build.VERSION.SDK_INT))
        row(R.string.info_abis, Build.SUPPORTED_ABIS.joinToString(", "))
        row(R.string.info_app, string(R.string.info_app_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))

        addDisplay()

        header(R.string.info_decoders)
        val decoders = decoderRows()
        addPlayback()
        val server = profileId?.let {
            header(R.string.info_server)
            row(R.string.info_server_version, DASH)
        }

        val scroll = SettingsScroll(context).apply {
            isFocusable = false
            isVerticalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            val room = context.dp(SCROLL_ROOM)
            setPadding(room, room, room, 0)
        }
        scroll.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(Tokens.CONTENT_START), context.dp(40), context.dp(Tokens.OVERSCAN_H), 0)
            clipToPadding = false
        }
        page.addView(label(context, 34f, Tokens.textPrimary, Fonts.title(context)).apply { text = string(R.string.setting_device_info) })
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            marginStart = -context.dp(SCROLL_ROOM)
            topMargin = context.dp(8)
        })
        view.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP))
        view.addView(clockView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END).apply {
            topMargin = context.dp(22)
            marginEnd = context.dp(32)
        })

        loadDecoders(decoders)
        if (server != null) loadServerVersion(server)
    }

    private fun addDisplay() {
        header(R.string.settings_display)
        val display = currentDisplay()
        val mode = display.mode
        row(R.string.info_current_mode, "${mode.physicalWidth}x${mode.physicalHeight} @ ${formatRate(mode.refreshRate)} Hz")
        val modes = display.supportedModes.map { DisplayModeInfo(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) }
        for ((size, rates) in groupModes(modes)) row(size, string(R.string.info_rates, rates.joinToString(", ")))
        row(R.string.info_hdr_types, hdrTypeNames(hdrTypes(display)).joinToString(", ").ifEmpty { string(R.string.info_none) })
        luminance(display)?.let { row(R.string.info_peak_brightness, string(R.string.info_nits, it)) }
    }

    /** The HDR types the display reports: the current mode's list from Android 14, the display's own before. */
    @Suppress("DEPRECATION")
    private fun hdrTypes(display: Display): List<Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) display.mode.supportedHdrTypes.toList()
        else display.hdrCapabilities?.supportedHdrTypes?.toList().orEmpty()

    /** The brightest the display says it can go, in nits; null when it does not say. */
    @Suppress("DEPRECATION")
    private fun luminance(display: Display): Int? =
        display.hdrCapabilities?.desiredMaxLuminance?.takeIf { it > 0f }?.toInt()

    private fun currentDisplay(): Display {
        val own = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) runCatching { context.display }.getOrNull() else null
        @Suppress("DEPRECATION")
        return own ?: (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    }

    /** The decoder rows, in the order they load; each shows "—" until [loadDecoders] fills it. */
    private fun decoderRows(): List<Pair<SettingRow, (MediaCodecCapabilitiesTest) -> String>> {
        val dolbyVision = Prefs.hdrOverride(context, Prefs.DOLBY_VISION)
        val hdr10 = Prefs.hdrOverride(context, Prefs.HDR10)
        val hdr10Plus = Prefs.hdrOverride(context, Prefs.HDR10_PLUS)
        fun probe(title: Int, read: (MediaCodecCapabilitiesTest) -> String) = row(title, DASH) to read
        fun plain(title: Int, read: (MediaCodecCapabilitiesTest) -> Boolean) = probe(title) { yesNo(HdrStatus.of(read(it), HdrOverride.AUTO)) }
        fun hdr(title: Int, override: HdrOverride, read: (MediaCodecCapabilitiesTest) -> Boolean) =
            probe(title) { yesNo(HdrStatus.of(read(it), override)) }
        return listOf(
            plain(R.string.info_hevc) { it.supportsHevc() },
            plain(R.string.info_hevc_10) { it.supportsHevcMain10() },
            hdr(R.string.info_hevc_hdr10, hdr10) { it.supportsHevcHDR10() },
            hdr(R.string.info_hevc_hdr10_plus, hdr10Plus) { it.supportsHevcHDR10Plus() },
            hdr(R.string.info_hevc_dolby_vision, dolbyVision) { it.supportsHevcDolbyVision() },
            // Profile 7 is only ever allowed by the TV, never forced on (see the profile's range types)
            probe(R.string.info_dolby_vision_7) { it.supportsHevcDolbyVisionEL().let { detected -> yesNo(HdrStatus(detected, detected && dolbyVision != HdrOverride.NEVER)) } },
            plain(R.string.info_av1) { it.supportsAV1() },
            plain(R.string.info_av1_10) { it.supportsAV1Main10() },
            hdr(R.string.info_av1_hdr10, hdr10) { it.supportsAV1HDR10() },
            hdr(R.string.info_av1_hdr10_plus, hdr10Plus) { it.supportsAV1HDR10Plus() },
            hdr(R.string.info_av1_dolby_vision, dolbyVision) { it.supportsAV1DolbyVision() },
            plain(R.string.info_avc_high_10) { it.supportsAVCHigh10() },
            plain(R.string.info_vc1) { it.supportsVc1() },
        )
    }

    /** Probes the codec list off the main thread, which is slow on some TVs. */
    private fun loadDecoders(decoders: List<Pair<SettingRow, (MediaCodecCapabilitiesTest) -> String>>) {
        scope.launch {
            val values = withContext(Dispatchers.Default) {
                val test = MediaCodecCapabilitiesTest(context)
                decoders.map { (_, read) -> runCatching { read(test) }.getOrDefault(DASH) }
            }
            decoders.zip(values).forEach { (decoder, value) -> decoder.first.setValue(value) }
        }
    }

    private fun yesNo(status: HdrStatus): String {
        val answer = string(if (status.effective) R.string.info_yes else R.string.info_no)
        return if (status.forced) string(R.string.info_forced, answer) else answer
    }

    private fun addPlayback() {
        header(R.string.settings_playback)
        val settings = ProfileSettings.read(context)
        val onOff = { on: Boolean -> string(if (on) R.string.setting_on else R.string.setting_off) }
        row(R.string.setting_max_bitrate, Prefs.maxBitrateMbps(context).let { if (it == Prefs.AUTO_BITRATE_MBPS) string(R.string.setting_auto) else string(R.string.mbps, it) })
        row(R.string.setting_max_resolution, when (settings.maxHeight) {
            0 -> string(R.string.setting_auto)
            2160 -> string(R.string.resolution_4k)
            else -> string(R.string.resolution_p, settings.maxHeight)
        })
        row(R.string.setting_ac3_passthrough, onOff(settings.ac3Passthrough))
        row(R.string.setting_downmix, onOff(settings.downmixStereo))
        row(R.string.setting_pgs_direct_play, onOff(settings.pgsDirectPlay))
    }

    /** Asks the profile's server for its version; the row keeps "—" when it can't be reached. */
    private fun loadServerVersion(row: SettingRow) {
        val profile = profileId?.let { app.profiles.get(it) } ?: return
        scope.launch {
            val version = withContext(Dispatchers.Default) {
                runCatching { JellyfinSession(context, profile).api.systemApi.getPublicSystemInfo().content.version }.getOrNull()
            }
            row.setValue(version?.takeIf { it.isNotBlank() } ?: DASH)
        }
    }

    /** A section's name, small and muted above its rows. */
    private fun header(title: Int) {
        val text = label(context, 13f, Tokens.textMuted, Fonts.bold(context)).apply { text = string(title).uppercase() }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(if (column.childCount == 0) 0 else 28)
            bottomMargin = context.dp(4)
        }
        column.addView(text, params)
    }

    private fun row(title: Int, value: String) = row(string(title), value)

    private fun row(title: String, value: String): SettingRow {
        val row = SettingRow(context, title)
        row.setValue(value)
        rows += row
        column.addView(row, LinearLayout.LayoutParams(context.dp(INFO_ROW_WIDTH), ViewGroup.LayoutParams.WRAP_CONTENT))
        return row
    }

    override fun onShown(first: Boolean) {
        clockView.applyClockSetting()
        super.onShown(first)
    }

    override fun focusDefault() {
        rows.firstOrNull()?.requestFocus()
    }

    private companion object {
        // Wider than a setting row: a list of refresh rates would be cut off in 640
        const val INFO_ROW_WIDTH = 760
        const val DASH = "—"
    }
}

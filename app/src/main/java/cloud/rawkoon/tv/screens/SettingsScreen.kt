package cloud.rawkoon.tv.screens

import android.content.Context
import android.graphics.Rect
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import cloud.rawkoon.tv.BuildConfig
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.Screen
import cloud.rawkoon.tv.data.ContinueOpen
import cloud.rawkoon.tv.data.Fetcher
import cloud.rawkoon.tv.data.formatBytes
import cloud.rawkoon.tv.data.HdrOverride
import cloud.rawkoon.tv.data.MatchDisplay
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.data.SkipMode
import cloud.rawkoon.tv.data.SubtitleColor
import cloud.rawkoon.tv.data.SubtitleEdge
import cloud.rawkoon.tv.data.SubtitleSize
import cloud.rawkoon.tv.ui.ChoiceDialog
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.PillButton
import cloud.rawkoon.tv.ui.SettingRow
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.label
import cloud.rawkoon.tv.ui.noClip
import cloud.rawkoon.tv.update.InstallState
import cloud.rawkoon.tv.update.Installer
import cloud.rawkoon.tv.update.UpdateCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App settings, as a scrolling list of sections: home, playback, skipping, display, language,
 * updates, profiles and storage, then about (licences and the version). Each setting is a row with its current value; OK opens the choices.
 * Changes are saved at once and apply the next time the player opens.
 */
class SettingsScreen(activity: MainActivity, private val profileId: String? = null) : Screen(activity) {
    override val view = FrameLayout(context)
    private val rows = mutableListOf<SettingRow>()
    private var dialog: ChoiceDialog? = null
    private val updateCheck = UpdateCheck(context, Fetcher(context))
    private val installer = Installer(context, Fetcher(context))
    private val updateStatus = label(context, 13f, Tokens.textMuted, Fonts.medium(context))
    private val checkNow = PillButton(context, string(R.string.update_check_now))
    private val install = PillButton(context, "", primary = true)

    init {
        // The page clips it at its edges (a view is clipped by its parent), so the rows scroll under
        // the title rather than over it; the padding leaves room inside for the focus ring and lift
        val scroll = SettingsScroll(context).apply {
            isFocusable = false
            isVerticalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            val room = context.dp(SCROLL_ROOM)
            setPadding(room, room, room, 0)
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, context.dp(40))
        }.noClip()
        scroll.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        addHomeSection(column)

        header(column, R.string.settings_playback)
        choice(column, R.string.setting_skip_back, SKIP_BACK_SECONDS.map { it to seconds(it) },
            { Prefs.skipBackSeconds(context) }) { put(Prefs.SKIP_BACK_S, it) }
        choice(column, R.string.setting_skip_forward, SKIP_FORWARD_SECONDS.map { it to seconds(it) },
            { Prefs.skipForwardSeconds(context) }) { put(Prefs.SKIP_FORWARD_S, it) }
        choice(column, R.string.setting_resume_rewind, RESUME_REWIND_SECONDS.map { it to if (it == 0) string(R.string.setting_off) else seconds(it) },
            { Prefs.resumeRewindSeconds(context) }) { put(Prefs.RESUME_REWIND_S, it) }
        choice(column, R.string.setting_max_bitrate, BITRATES_MBPS.map { it to if (it == Prefs.AUTO_BITRATE_MBPS) string(R.string.setting_auto) else string(R.string.mbps, it) },
            { Prefs.maxBitrateMbps(context) }) { put(Prefs.MAX_BITRATE_MBPS, it) }
        choice(column, R.string.setting_max_resolution, RESOLUTIONS.map { it to if (it == 0) string(R.string.setting_auto) else resolutionName(it) },
            { Prefs.maxResolution(context) }) { put(Prefs.MAX_RESOLUTION, it) }

        header(column, R.string.settings_skipping)
        val skipModes = listOf(
            SkipMode.BUTTON to string(R.string.skip_mode_button),
            SkipMode.AUTO to string(R.string.skip_mode_auto),
            SkipMode.NONE to string(R.string.skip_mode_none),
        )
        for ((title, key) in listOf(
            R.string.setting_intros to Prefs.SKIP_INTRO,
            R.string.setting_recaps to Prefs.SKIP_RECAP,
            R.string.setting_previews to Prefs.SKIP_PREVIEW,
            R.string.setting_credits to Prefs.SKIP_CREDITS,
        )) {
            choice(column, title, skipModes, { Prefs.skipMode(context, key) }) { put(key, it.key) }
        }
        choice(column, R.string.setting_autoplay, listOf(true to string(R.string.setting_on), false to string(R.string.setting_off)),
            { Prefs.autoplayNext(context) }) { put(Prefs.AUTOPLAY_NEXT, it) }
        choice(column, R.string.setting_countdown, COUNTDOWN_SECONDS.map { it to seconds(it) },
            { Prefs.autoplayCountdownSeconds(context) }) { put(Prefs.AUTOPLAY_COUNTDOWN_S, it) }
        choice(column, R.string.setting_still_watching, STILL_WATCHING_EPISODES.map { it to if (it == 0) string(R.string.setting_off) else string(R.string.after_episodes, it) },
            { Prefs.stillWatchingEpisodes(context) }) { put(Prefs.STILL_WATCHING_EPISODES, it) }

        addSubtitleSection(column)
        addAudioSection(column)

        header(column, R.string.settings_display)
        choice(column, R.string.setting_match_display, listOf(
            MatchDisplay.OFF to string(R.string.setting_off),
            MatchDisplay.FRAME_RATE to string(R.string.match_frame_rate),
            MatchDisplay.FRAME_RATE_AND_RESOLUTION to string(R.string.match_frame_rate_resolution),
        ), { Prefs.matchDisplay(context) }) { put(Prefs.MATCH_DISPLAY, it.key) }
        addHdrRows(column)

        header(column, R.string.language)
        choice(column, R.string.language, listOf("system" to string(R.string.language_system), "fr" to string(R.string.language_fr), "en" to string(R.string.language_en)),
            { Prefs.language(context) }) { code ->
            put(Prefs.LANGUAGE, code)
            // Every screen is rebuilt in the new language, starting from "Who's watching?"
            activity.recreate()
        }

        header(column, R.string.update_title)
        // Previews (and anything else that cannot update) have no check to turn off
        if (updateCheck.enabled) {
            choice(column, R.string.setting_auto_update, listOf(true to string(R.string.setting_on), false to string(R.string.setting_off)),
                { Prefs.autoUpdateCheck(context) }) { put(Prefs.AUTO_UPDATE_CHECK, it) }
        }
        column.addView(updateStatus, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(8); bottomMargin = context.dp(4) })
        if (updateCheck.enabled) {
            val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }.noClip()
            buttons.addView(checkNow)
            buttons.addView(install)
            column.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            checkNow.setOnClickListener { checkForUpdate() }
            install.setOnClickListener { installUpdate() }
        }
        showUpdate()

        addProfilesSection(column)
        addStorageSection(column)

        header(column, R.string.settings_about)
        action(column, R.string.setting_licences, "›") { activity.push(LicencesScreen(activity)) }
        column.addView(label(context, 13f, Tokens.textMuted, Fonts.medium(context)).apply {
            text = string(R.string.version, BuildConfig.VERSION_NAME)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(8) })

        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(Tokens.CONTENT_START), context.dp(40), context.dp(Tokens.OVERSCAN_H), 0)
            // Its children are clipped to their own bounds, not to this padding: the list reaches past it
            clipToPadding = false
        }
        page.addView(label(context, 34f, Tokens.textPrimary, Fonts.title(context)).apply { text = string(R.string.nav_settings) })
        // Pulled back by its inner room, so the rows still line up with the title
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { marginStart = -context.dp(SCROLL_ROOM) })
        view.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP))
    }

    // Home

    private fun addHomeSection(column: LinearLayout) {
        header(column, R.string.settings_home)
        // The row order is per profile, so it needs to know whose settings were opened
        profileId?.let { id ->
            val row = SettingRow(context, string(R.string.setting_home_rows))
            row.setValue("›")
            row.setOnClickListener { activity.push(HomeRowsScreen(activity, id)) }
            rows += row
            column.addView(row, LinearLayout.LayoutParams(context.dp(ROW_WIDTH), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        choice(column, R.string.setting_home_row_limit, HOME_ROW_LIMITS.map { it to it.toString() },
            { Prefs.homeFetch(context).limit }) { put(Prefs.HOME_ROW_LIMIT, it) }
        choice(column, R.string.setting_next_up_cutoff, NEXT_UP_CUTOFF_DAYS.map { it to if (it == 0) string(R.string.setting_off) else string(R.string.days, it) },
            { Prefs.homeFetch(context).nextUpDays }) { put(Prefs.NEXT_UP_CUTOFF_DAYS, it) }
        choice(column, R.string.setting_next_up_rewatching, listOf(false to string(R.string.setting_off), true to string(R.string.setting_on)),
            { Prefs.homeFetch(context).rewatching }) { put(Prefs.NEXT_UP_REWATCHING, it) }
        choice(column, R.string.setting_continue_open, listOf(
            ContinueOpen.PAGE to string(R.string.continue_open_page),
            ContinueOpen.PLAY to string(R.string.continue_open_play),
        ), { Prefs.continueOpen(context) }) { put(Prefs.CONTINUE_OPEN, it.key) }
        choice(column, R.string.setting_clock, listOf(true to string(R.string.clock_shown), false to string(R.string.clock_hidden)),
            { Prefs.showClock(context) }) { put(Prefs.SHOW_CLOCK, it) }
    }

    // Profiles and storage

    private fun addProfilesSection(column: LinearLayout) {
        header(column, R.string.settings_profiles)
        choice(column, R.string.setting_skip_picker, listOf(false to string(R.string.setting_off), true to string(R.string.setting_on)),
            { Prefs.skipProfilePicker(context) }) { put(Prefs.SKIP_PROFILE_PICKER, it) }
    }

    private fun addStorageSection(column: LinearLayout) {
        header(column, R.string.settings_storage)
        action(column, R.string.setting_clear_images, "") { row ->
            dialog = ChoiceDialog(
                context,
                string(R.string.clear_images_confirm),
                listOf(
                    string(R.string.clear_images_action) to { clearImages(row) },
                    string(R.string.cancel) to {},
                ),
            ).also { it.open(view) }
        }.also(::showImageCacheSize)
    }

    /** Reads the disk cache's size off the main thread and shows it in [row]. */
    private fun showImageCacheSize(row: SettingRow) {
        scope.launch {
            val size = withContext(Dispatchers.IO) { app.images.disk.size() }
            row.setValue(formatBytes(size, units = string(R.string.byte_units).split(' ')))
        }
    }

    private fun clearImages(row: SettingRow) {
        scope.launch {
            withContext(Dispatchers.IO) { app.images.clearCache() }
            showImageCacheSize(row)
        }
    }

    // Subtitles, audio and HDR formats

    private fun addSubtitleSection(column: LinearLayout) {
        header(column, R.string.settings_subtitles)
        choice(column, R.string.setting_subtitle_size, listOf(
            SubtitleSize.SMALL to string(R.string.size_small),
            SubtitleSize.MEDIUM to string(R.string.size_medium),
            SubtitleSize.LARGE to string(R.string.size_large),
            SubtitleSize.EXTRA_LARGE to string(R.string.size_extra_large),
        ), { Prefs.subtitleSize(context) }) { put(Prefs.SUBTITLE_SIZE, it.key) }
        choice(column, R.string.setting_subtitle_color, listOf(
            SubtitleColor.WHITE to string(R.string.color_white),
            SubtitleColor.YELLOW to string(R.string.color_yellow),
            SubtitleColor.GREY to string(R.string.color_grey),
        ), { Prefs.subtitleColor(context) }) { put(Prefs.SUBTITLE_COLOR, it.key) }
        choice(column, R.string.setting_subtitle_background, listOf(
            false to string(R.string.background_none),
            true to string(R.string.background_box),
        ), { Prefs.subtitleBackground(context) }) { put(Prefs.SUBTITLE_BACKGROUND, it) }
        choice(column, R.string.setting_subtitle_edge, listOf(
            SubtitleEdge.OUTLINE to string(R.string.edge_outline),
            SubtitleEdge.SHADOW to string(R.string.edge_shadow),
            SubtitleEdge.NONE to string(R.string.edge_none),
        ), { Prefs.subtitleEdge(context) }) { put(Prefs.SUBTITLE_EDGE, it.key) }
        choice(column, R.string.setting_pgs_direct_play, listOf(true to string(R.string.setting_on), false to string(R.string.setting_off)),
            { Prefs.pgsDirectPlay(context) }) { put(Prefs.PGS_DIRECT_PLAY, it) }
    }

    private fun addAudioSection(column: LinearLayout) {
        val onOff = listOf(true to string(R.string.setting_on), false to string(R.string.setting_off))
        header(column, R.string.settings_audio)
        choice(column, R.string.setting_ac3_passthrough, onOff, { Prefs.ac3Passthrough(context) }) { put(Prefs.AC3_PASSTHROUGH, it) }
        choice(column, R.string.setting_downmix, onOff, { Prefs.downmixStereo(context) }) { put(Prefs.DOWNMIX_STEREO, it) }
    }

    private fun addHdrRows(column: LinearLayout) {
        val overrides = listOf(
            HdrOverride.AUTO to string(R.string.hdr_auto),
            HdrOverride.ALWAYS to string(R.string.hdr_always),
            HdrOverride.NEVER to string(R.string.hdr_never),
        )
        for ((title, key) in listOf(
            R.string.setting_hdr10 to Prefs.HDR10,
            R.string.setting_hdr10_plus to Prefs.HDR10_PLUS,
            R.string.setting_dolby_vision to Prefs.DOLBY_VISION,
        )) {
            choice(column, title, overrides, { Prefs.hdrOverride(context, key) }) { put(key, it.key) }
        }
    }

    /** A section's name, small and muted above its rows. */
    private fun header(parent: LinearLayout, title: Int) {
        parent.addView(label(context, 13f, Tokens.textMuted, Fonts.bold(context)).apply { text = string(title).uppercase() }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = context.dp(28); bottomMargin = context.dp(4) })
    }

    /**
     * Adds a row for a setting: its title, and its current value from [read]. OK opens a
     * dialog of [options] (value and label), with the current one marked and focused; a pick is
     * handed to [write] and shown in the row.
     */
    private fun <T> choice(parent: LinearLayout, title: Int, options: List<Pair<T, String>>, read: () -> T, write: (T) -> Unit) {
        val row = SettingRow(context, string(title))
        fun show() = row.setValue(options.firstOrNull { it.first == read() }?.second ?: read().toString())
        show()
        row.setOnClickListener {
            val current = options.indexOfFirst { it.first == read() }
            val choices = options.mapIndexed { i, (value, text) ->
                (if (i == current) string(R.string.choice_current, text) else text) to {
                    if (value != read()) write(value)
                    show()
                }
            }
            dialog = ChoiceDialog(context, string(title), choices, selected = current.coerceAtLeast(0)).also { it.open(view) }
        }
        rows += row
        parent.addView(row, LinearLayout.LayoutParams(context.dp(ROW_WIDTH), ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** Adds a row that runs [onClick] on OK, showing [value] where a setting shows its choice. */
    private fun action(parent: LinearLayout, title: Int, value: String, onClick: (SettingRow) -> Unit): SettingRow {
        val row = SettingRow(context, string(title))
        row.setValue(value)
        row.setOnClickListener { onClick(row) }
        rows += row
        parent.addView(row, LinearLayout.LayoutParams(context.dp(ROW_WIDTH), ViewGroup.LayoutParams.WRAP_CONTENT))
        return row
    }

    private fun put(key: String, value: Int) = Prefs.of(context).edit().putInt(key, value).apply()

    private fun put(key: String, value: String) = Prefs.of(context).edit().putString(key, value).apply()

    private fun put(key: String, value: Boolean) = Prefs.of(context).edit().putBoolean(key, value).apply()

    private fun seconds(value: Int) = string(R.string.seconds, value)

    private fun resolutionName(height: Int) = when (height) {
        2160 -> string(R.string.resolution_4k)
        else -> string(R.string.resolution_p, height)
    }

    /** What the last check found, and the install button when a newer release is waiting. */
    private fun showUpdate() {
        val pending = updateCheck.pending()
        val state = Installer.state
        updateStatus.text = when {
            !updateCheck.enabled -> string(R.string.update_disabled)
            state is InstallState.Downloading -> string(R.string.update_downloading, state.percent)
            state is InstallState.Ready -> string(R.string.update_confirm)
            state is InstallState.Failed -> string(state.messageRes)
            pending != null -> string(R.string.update_available, pending.version)
            updateCheck.checkedAt == 0L -> string(R.string.update_never_checked)
            updateCheck.failed -> string(R.string.update_check_failed, checkedAtText())
            else -> string(R.string.update_up_to_date, checkedAtText())
        }
        install.visibility = if (pending != null && !Installer.running && state !is InstallState.Ready) View.VISIBLE else View.GONE
        pending?.let { install.label.text = string(R.string.update_install, it.version) }
    }

    /** Back from the installer's confirmation (declined or not), the status says what is true now. */
    override fun onResume() {
        Installer.acknowledge()
        showUpdate()
    }

    override fun onShown(first: Boolean) {
        super.onShown(first)
        Installer.listen(this) { state ->
            if (state is InstallState.Failed) Toast.makeText(context, state.messageRes, Toast.LENGTH_LONG).show()
            showUpdate()
        }
        showUpdate()
    }

    override fun destroy() {
        Installer.unlisten(this)
        super.destroy()
    }

    private fun checkedAtText() = DateFormat.getMediumDateFormat(context).format(updateCheck.checkedAt)

    private fun checkForUpdate() {
        updateStatus.text = string(R.string.update_checking)
        updateCheck.refreshAsync(force = true) { if (scope.isActive) showUpdate() }
    }

    private fun installUpdate() {
        val release = updateCheck.pending() ?: return
        install.visibility = View.GONE
        // Focus would otherwise fall to whatever Android finds once the button is gone.
        checkNow.requestFocus()
        installer.install(release)
        showUpdate()
    }

    override fun focusDefault() {
        rows.firstOrNull()?.requestFocus()
    }

    override fun onBack(): Boolean {
        dialog?.takeIf { it.isAttachedToWindow }?.let {
            it.close()
            return true
        }
        return false
    }
}

/** Scrolls a focused row into view with some room around it, so its ring and the rows beside it show. */
internal class SettingsScroll(context: Context) : ScrollView(context) {
    private val margin = context.dp(60)

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        rect.top -= margin
        rect.bottom += margin
        return super.computeScrollDeltaToGetChildRectOnScreen(rect)
    }
}

internal const val ROW_WIDTH = 640

/** Room inside the clipped list for a focused row's ring and lift. */
internal const val SCROLL_ROOM = 16
private val HOME_ROW_LIMITS = listOf(10, 20, 30, 50)
private val NEXT_UP_CUTOFF_DAYS = listOf(0, 30, 60, 90, 180, 365)
private val SKIP_BACK_SECONDS = listOf(5, 10, 15, 30)
private val SKIP_FORWARD_SECONDS = listOf(10, 15, 30, 60)
private val RESUME_REWIND_SECONDS = listOf(0, 3, 5, 10, 15)
private val COUNTDOWN_SECONDS = listOf(5, 8, 10, 15, 30)
private val STILL_WATCHING_EPISODES = listOf(0, 2, 3, 4, 5)
private val BITRATES_MBPS = listOf(Prefs.AUTO_BITRATE_MBPS, 120, 80, 60, 40, 20, 10, 8, 4, 2)
private val RESOLUTIONS = listOf(0, 2160, 1080, 720, 480)

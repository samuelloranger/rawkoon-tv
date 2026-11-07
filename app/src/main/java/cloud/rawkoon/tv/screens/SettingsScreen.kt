package cloud.rawkoon.tv.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import cloud.rawkoon.tv.BuildConfig
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.Screen
import cloud.rawkoon.tv.data.ContinueOpen
import cloud.rawkoon.tv.data.Fetcher
import cloud.rawkoon.tv.data.HdrOverride
import cloud.rawkoon.tv.data.MatchDisplay
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.data.SkipMode
import cloud.rawkoon.tv.data.SubtitleColor
import cloud.rawkoon.tv.data.SubtitleEdge
import cloud.rawkoon.tv.data.SubtitleSize
import cloud.rawkoon.tv.data.formatBytes
import cloud.rawkoon.tv.ui.ChoiceDialog
import cloud.rawkoon.tv.ui.FlatView
import cloud.rawkoon.tv.ui.FocusMotion
import cloud.rawkoon.tv.ui.Focusable
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.Icon
import cloud.rawkoon.tv.ui.PillButton
import cloud.rawkoon.tv.ui.SettingRow
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.applyClockSetting
import cloud.rawkoon.tv.ui.clock
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.dpf
import cloud.rawkoon.tv.ui.icon
import cloud.rawkoon.tv.ui.isWithin
import cloud.rawkoon.tv.ui.label
import cloud.rawkoon.tv.ui.noClip
import cloud.rawkoon.tv.ui.ringDrawable
import cloud.rawkoon.tv.update.InstallState
import cloud.rawkoon.tv.update.Installer
import cloud.rawkoon.tv.update.UpdateCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App settings, split into two panes: a category column on the left and the active category's
 * options on the right. Focusing a category shows its pane at once; Right or OK steps into the
 * rows, and Left or Back returns to the category list.
 */
class SettingsScreen(activity: MainActivity, private val profileId: String? = null) : Screen(activity) {
    override val view = FrameLayout(context)
    private val categories = mutableListOf<CategoryTab>()
    private val panes = mutableListOf<LinearLayout>()
    private val paneFirstFocus = mutableListOf<View?>()
    private var activeCategory = 0
    private var dialog: ChoiceDialog? = null
    private val clockView = clock(context).apply { applyClockSetting() }
    private val updateCheck = UpdateCheck(context, Fetcher(context))
    private val installer = Installer(context, Fetcher(context))
    private val updateStatus = label(context, 13f, Tokens.textMuted, Fonts.medium(context))
    private val checkNow = PillButton(context, string(R.string.update_check_now))
    private val install = PillButton(context, "", primary = true)
    private val scroll: SettingsScroll

    init {
        val categoryColumn = CategoryColumn(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, context.dp(SCROLL_ROOM), 0, context.dp(24))
        }.noClip()

        scroll = PaneScroll(context).apply {
            isFocusable = false
            isVerticalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            val room = context.dp(SCROLL_ROOM)
            setPadding(room, room, room, 0)
        }
        val paneStack = FrameLayout(context).noClip()
        scroll.addView(paneStack, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        addCategory(categoryColumn, paneStack, Icon.HOME, R.string.settings_home) { addHomeSection(it) }
        addCategory(categoryColumn, paneStack, Icon.PLAY, R.string.settings_playback) { addPlaybackSection(it) }
        addCategory(categoryColumn, paneStack, Icon.SKIP, R.string.settings_skipping) { addSkippingSection(it) }
        addCategory(categoryColumn, paneStack, Icon.SUBTITLES, R.string.settings_subtitles) { addSubtitleSection(it) }
        addCategory(categoryColumn, paneStack, Icon.TV, R.string.settings_display_audio) { addDisplayAndAudioSection(it) }
        addCategory(categoryColumn, paneStack, Icon.GEAR, R.string.settings_system) { addSystemSection(it) }

        selectCategory(0, animate = false)

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        body.addView(categoryColumn, LinearLayout.LayoutParams(context.dp(CATEGORY_WIDTH), ViewGroup.LayoutParams.WRAP_CONTENT))
        body.addView(scroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            marginStart = context.dp(PANE_GAP - SCROLL_ROOM)
        })

        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(Tokens.CONTENT_START), context.dp(40), context.dp(Tokens.OVERSCAN_H), 0)
            clipToPadding = false
            clipChildren = false
        }
        page.addView(label(context, 34f, Tokens.textPrimary, Fonts.title(context)).apply { text = string(R.string.nav_settings) })
        page.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            marginStart = -context.dp(Tokens.RING_INSET)
            topMargin = context.dp(10)
        })
        view.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP))
        view.addView(clockView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END).apply {
            topMargin = context.dp(22)
            marginEnd = context.dp(32)
        })
    }

    private fun addCategory(
        categoryColumn: LinearLayout,
        paneStack: FrameLayout,
        glyph: String,
        titleRes: Int,
        buildPane: (LinearLayout) -> Unit,
    ) {
        val index = categories.size
        val pane = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, context.dp(40))
            visibility = View.GONE
        }.noClip()
        buildPane(pane)
        panes += pane
        paneFirstFocus += firstFocusable(pane)
        paneStack.addView(pane, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val tab = CategoryTab(context, glyph, string(titleRes)) { selectCategory(index, animate = true) }
        tab.setOnClickListener { focusActivePane() }
        categories += tab
        categoryColumn.addView(tab, LinearLayout.LayoutParams(context.dp(CATEGORY_WIDTH), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(2)
        })
    }

    private fun selectCategory(index: Int, animate: Boolean) {
        if (index == activeCategory && panes[index].visibility == View.VISIBLE) {
            categories.forEachIndexed { i, tab -> tab.setActive(i == index) }
            return
        }
        activeCategory = index
        categories.forEachIndexed { i, tab -> tab.setActive(i == index) }
        scroll.scrollTo(0, 0)
        panes.forEachIndexed { i, pane ->
            if (i == index) {
                pane.visibility = View.VISIBLE
                if (animate) {
                    pane.alpha = 0f
                    pane.translationY = context.dpf(6f)
                    pane.animate().alpha(1f).translationY(0f).setDuration(140L).setInterpolator(Tokens.ease).start()
                } else {
                    pane.alpha = 1f
                    pane.translationY = 0f
                }
            } else {
                pane.animate().cancel()
                pane.visibility = View.GONE
            }
        }
    }

    private fun focusActivePane(): View? {
        val target = paneFirstFocus.getOrNull(activeCategory)?.takeIf { it.isShown }
            ?: firstFocusable(panes[activeCategory])
        target?.requestFocus()
        return target
    }

    private fun firstFocusable(root: ViewGroup): View? {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (child.isFocusable) return child
            if (child is ViewGroup) firstFocusable(child)?.let { return it }
        }
        return null
    }

    // Categories

    private fun addHomeSection(column: LinearLayout) {
        header(column, R.string.settings_home, first = true)
        // The row order is per profile, so it needs to know whose settings were opened
        profileId?.let { id ->
            action(column, R.string.setting_home_rows, "›") { activity.push(HomeRowsScreen(activity, id)) }
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
            { Prefs.showClock(context) }) {
            put(Prefs.SHOW_CLOCK, it)
            clockView.applyClockSetting()
        }
    }

    private fun addPlaybackSection(column: LinearLayout) {
        header(column, R.string.settings_playback, first = true)
        choice(column, R.string.setting_skip_back, SKIP_BACK_SECONDS.map { it to seconds(it) },
            { Prefs.skipBackSeconds(context) }) { put(Prefs.SKIP_BACK_S, it) }
        choice(column, R.string.setting_skip_forward, SKIP_FORWARD_SECONDS.map { it to seconds(it) },
            { Prefs.skipForwardSeconds(context) }) { put(Prefs.SKIP_FORWARD_S, it) }
        choice(column, R.string.setting_resume_rewind, RESUME_REWIND_SECONDS.map { it to if (it == 0) string(R.string.setting_off) else seconds(it) },
            { Prefs.resumeRewindSeconds(context) }) { put(Prefs.RESUME_REWIND_S, it) }
        choice(column, R.string.setting_max_bitrate, Prefs.BITRATES_MBPS.map { it to if (it == Prefs.AUTO_BITRATE_MBPS) string(R.string.setting_auto) else string(R.string.mbps, it) },
            { Prefs.maxBitrateMbps(context) }) { put(Prefs.MAX_BITRATE_MBPS, it) }
        choice(column, R.string.setting_max_resolution, RESOLUTIONS.map { it to if (it == 0) string(R.string.setting_auto) else resolutionName(it) },
            { Prefs.maxResolution(context) }) { put(Prefs.MAX_RESOLUTION, it) }
    }

    private fun addSkippingSection(column: LinearLayout) {
        header(column, R.string.settings_skipping, first = true)
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
    }

    private fun addSubtitleSection(column: LinearLayout) {
        header(column, R.string.settings_subtitles, first = true)
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

    private fun addDisplayAndAudioSection(column: LinearLayout) {
        header(column, R.string.settings_display, first = true)
        choice(column, R.string.setting_match_display, listOf(
            MatchDisplay.OFF to string(R.string.setting_off),
            MatchDisplay.FRAME_RATE to string(R.string.match_frame_rate),
            MatchDisplay.FRAME_RATE_AND_RESOLUTION to string(R.string.match_frame_rate_resolution),
        ), { Prefs.matchDisplay(context) }) { put(Prefs.MATCH_DISPLAY, it.key) }
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

        val onOff = listOf(true to string(R.string.setting_on), false to string(R.string.setting_off))
        header(column, R.string.settings_audio)
        choice(column, R.string.setting_ac3_passthrough, onOff, { Prefs.ac3Passthrough(context) }) { put(Prefs.AC3_PASSTHROUGH, it) }
        choice(column, R.string.setting_downmix, onOff, { Prefs.downmixStereo(context) }) { put(Prefs.DOWNMIX_STEREO, it) }
    }

    private fun addSystemSection(column: LinearLayout) {
        header(column, R.string.settings_profiles, first = true)
        choice(column, R.string.language, listOf("system" to string(R.string.language_system), "fr" to string(R.string.language_fr), "en" to string(R.string.language_en)),
            { Prefs.language(context) }) { code ->
            put(Prefs.LANGUAGE, code)
            // Every screen is rebuilt in the new language, starting from "Who's watching?"
            activity.recreate()
        }
        choice(column, R.string.setting_skip_picker, listOf(false to string(R.string.setting_off), true to string(R.string.setting_on)),
            { Prefs.skipProfilePicker(context) }) { put(Prefs.SKIP_PROFILE_PICKER, it) }

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

        header(column, R.string.update_title)
        // Previews (and anything else that cannot update) have no check to turn off
        if (updateCheck.enabled) {
            choice(column, R.string.setting_auto_update, listOf(true to string(R.string.setting_on), false to string(R.string.setting_off)),
                { Prefs.autoUpdateCheck(context) }) { put(Prefs.AUTO_UPDATE_CHECK, it) }
        }
        column.addView(updateStatus, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(6); bottomMargin = context.dp(4) })
        if (updateCheck.enabled) {
            val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }.noClip()
            buttons.addView(checkNow)
            buttons.addView(install)
            column.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            checkNow.setOnClickListener { checkForUpdate() }
            install.setOnClickListener { installUpdate() }
        }
        showUpdate()

        header(column, R.string.settings_about)
        action(column, R.string.setting_device_info, "›") { activity.push(DeviceInfoScreen(activity, profileId)) }
        action(column, R.string.setting_licences, "›") { activity.push(LicencesScreen(activity)) }
        column.addView(label(context, 13f, Tokens.textMuted, Fonts.medium(context)).apply {
            text = string(R.string.version, BuildConfig.VERSION_NAME)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(6) })
    }

    /** Reads the disk cache's size off the main thread and shows it in [row]. */
    private fun showImageCacheSize(row: SettingRow) {
        scope.launch {
            val size = withContext(Dispatchers.IO) { app.images.disk.size() }
            row.setValue(formatBytes(size, units = context.resources.getStringArray(R.array.byte_units).asList()))
        }
    }

    private fun clearImages(row: SettingRow) {
        scope.launch {
            withContext(Dispatchers.IO) { app.images.clearCache() }
            showImageCacheSize(row)
        }
    }

    /** A section's name, small and muted above its rows. */
    private fun header(parent: LinearLayout, title: Int, first: Boolean = false) {
        parent.addView(label(context, 12f, Tokens.textMuted, Fonts.bold(context)).apply { text = string(title).uppercase() }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = context.dp(if (first) 2 else 18)
            bottomMargin = context.dp(4)
            marginStart = context.dp(Tokens.RING_INSET)
        })
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
        parent.addView(row, LinearLayout.LayoutParams(context.dp(PANE_ROW_WIDTH), ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** Adds a row that runs [onClick] on OK, showing [value] where a setting shows its choice. */
    private fun action(parent: LinearLayout, title: Int, value: String, onClick: (SettingRow) -> Unit): SettingRow {
        val row = SettingRow(context, string(title))
        row.setValue(value)
        row.setOnClickListener { onClick(row) }
        parent.addView(row, LinearLayout.LayoutParams(context.dp(PANE_ROW_WIDTH), ViewGroup.LayoutParams.WRAP_CONTENT))
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
        clockView.applyClockSetting()
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
        categories.getOrNull(activeCategory)?.requestFocus()
    }

    override fun onBack(): Boolean {
        dialog?.takeIf { it.isAttachedToWindow }?.let {
            it.close()
            return true
        }
        if (scroll.hasFocus()) {
            categories.getOrNull(activeCategory)?.requestFocus()
            return true
        }
        return false
    }

    /**
     * Left column of categories: Up and Down walk the tabs in order; Right steps into the active
     * category's first setting row.
     */
    private inner class CategoryColumn(context: Context) : LinearLayout(context) {
        override fun focusSearch(focused: View?, direction: Int): View? {
            if (direction == View.FOCUS_RIGHT) return focusActivePane() ?: focused
            if (direction == View.FOCUS_LEFT) return focused
            if (direction == View.FOCUS_UP || direction == View.FOCUS_DOWN) {
                val i = categories.indexOf(focused)
                if (i >= 0) return categories.getOrNull(if (direction == View.FOCUS_DOWN) i + 1 else i - 1) ?: focused
            }
            return super.focusSearch(focused, direction)
        }
    }

    /**
     * Right settings scroll pane: Left returns to the active category tab (unless moving sideways
     * between the update buttons), and Up/Down never escape into the category column.
     */
    private inner class PaneScroll(context: Context) : SettingsScroll(context) {
        override fun focusSearch(focused: View?, direction: Int): View? {
            if (direction == View.FOCUS_LEFT) {
                if (focused === install && checkNow.isShown) return checkNow
                return categories.getOrNull(activeCategory) ?: focused
            }
            if (direction == View.FOCUS_RIGHT) {
                if (focused === checkNow && install.isShown) return install
                return focused
            }
            val next = super.focusSearch(focused, direction)
            return if (next != null && next.isWithin(this)) next else focused
        }
    }
}

/**
 * One entry in the settings category sidebar: an icon and a label. Focusing it selects the
 * category right away; when focus moves into the right pane, the active category keeps a quiet
 * pill and an apricot accent bar on the left.
 */
@SuppressLint("ViewConstructor")
private class CategoryTab(
    context: Context,
    glyph: String,
    title: String,
    onFocused: () -> Unit,
) : Focusable(context) {
    private val radius = context.dpf(10f)
    private val ring = FlatView(context)
    override val motion = FocusMotion(this, ring, scale = 1.02f)
    private val content = LinearLayout(context)
    private val iconView: TextView = icon(context, glyph, 14f, Tokens.textMuted)
    private val titleView: TextView = label(context, 15f, Tokens.textMuted, Fonts.medium(context)).apply { text = title }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Tokens.accent }
    private val barRect = RectF()
    private val activeBg = GradientDrawable().apply {
        cornerRadius = radius
        setColor(Tokens.surfaceHigh)
    }
    private val focusedBg = GradientDrawable().apply {
        cornerRadius = radius
        setColor(Tokens.surfaceFocused)
    }
    private var active = false

    init {
        setWillNotDraw(false)
        val inset = context.dp(Tokens.RING_INSET)
        content.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(14), 0, context.dp(14), 0)
        }
        content.addView(iconView, LinearLayout.LayoutParams(context.dp(24), ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(10) })
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(HEIGHT)).apply { setMargins(inset, inset, inset, inset) })
        ring.background = ringDrawable(context, radius)
        ring.alpha = 0f
        addView(ring, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setOnFocusChangeListener { _, focused ->
            motion.focus(focused)
            if (focused) onFocused()
            paint()
        }
        paint()
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        paint()
    }

    private fun paint() {
        val focused = isFocused
        content.background = when {
            focused -> focusedBg
            active -> activeBg
            else -> null
        }
        iconView.setTextColor(if (active || focused) Tokens.accent else Tokens.textMuted)
        titleView.setTextColor(if (active || focused) Tokens.textPrimary else Tokens.textMuted)
        titleView.typeface = if (active || focused) Fonts.bold(context) else Fonts.medium(context)
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!active || isFocused) return
        val inset = context.dpf(Tokens.RING_INSET.toFloat())
        val barW = context.dpf(3f)
        val barH = context.dpf(18f)
        val left = inset + context.dpf(4f)
        val top = (height - barH) / 2f
        barRect.set(left, top, left + barW, top + barH)
        canvas.drawRoundRect(barRect, barW / 2f, barW / 2f, barPaint)
    }

    companion object {
        private const val HEIGHT = 42
    }
}

/** Scrolls a focused row into view with some room around it, so its ring and the rows beside it show. */
internal open class SettingsScroll(context: Context) : ScrollView(context) {
    private val margin = context.dp(60)

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        rect.top -= margin
        rect.bottom += margin
        return super.computeScrollDeltaToGetChildRectOnScreen(rect)
    }
}

internal const val ROW_WIDTH = 640
private const val CATEGORY_WIDTH = 220
private const val PANE_GAP = 28
private const val PANE_ROW_WIDTH = 560

/** Room inside the clipped list for a focused row's ring and lift. */
internal const val SCROLL_ROOM = 16
private val HOME_ROW_LIMITS = listOf(10, 20, 30, 50)
private val NEXT_UP_CUTOFF_DAYS = listOf(0, 30, 60, 90, 180, 365)
private val SKIP_BACK_SECONDS = listOf(5, 10, 15, 30)
private val SKIP_FORWARD_SECONDS = listOf(10, 15, 30, 60)
private val RESUME_REWIND_SECONDS = listOf(0, 3, 5, 10, 15)
private val COUNTDOWN_SECONDS = listOf(5, 8, 10, 15, 30)
private val STILL_WATCHING_EPISODES = listOf(0, 2, 3, 4, 5)
private val RESOLUTIONS = listOf(0, 2160, 1080, 720, 480)

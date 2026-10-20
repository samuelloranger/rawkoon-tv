package cloud.rawkoon.tv.screens

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.Screen
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.jellyfin.HomeRow
import cloud.rawkoon.tv.jellyfin.movedBy
import cloud.rawkoon.tv.jellyfin.orderedIds
import cloud.rawkoon.tv.ui.ChoiceDialog
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

/**
 * The rows of a profile's home, in the order they show, each shown or hidden. OK on a row opens
 * a small menu (Move up, Move down, Show or Hide); after a move the menu opens again on the row
 * at its new place, so a row can be walked up or down with OK, Down, OK, Down. Back leaves. The
 * rows listed are the ones the home last showed (it keeps them on disk), so this works offline
 * but only knows a library or a Rawkoon row after the home has loaded it once. Changes are saved
 * at once and show the next time Home is on screen.
 */
class HomeRowsScreen(activity: MainActivity, private val profileId: String) : Screen(activity) {
    override val view = FrameLayout(context)
    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, context.dp(40))
    }.noClip()
    private val clockView = clock(context).apply { applyClockSetting() }
    private val hint = label(context, 14f, Tokens.textMuted, Fonts.medium(context))
    private var dialog: ChoiceDialog? = null

    private val titles = mutableMapOf<String, String>()
    private val views = mutableMapOf<String, SettingRow>()
    private var ids = mutableListOf<String>()
    private var hidden = mutableSetOf<String>()

    init {
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
        page.addView(label(context, 34f, Tokens.textPrimary, Fonts.title(context)).apply { text = string(R.string.setting_home_rows) })
        page.addView(hint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(8) })
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { marginStart = -context.dp(SCROLL_ROOM) })
        view.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP))
        view.addView(clockView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END).apply {
            topMargin = context.dp(22)
            marginEnd = context.dp(32)
        })
        hint.text = string(R.string.home_rows_hint)
        load()
    }

    private fun load() {
        scope.launch {
            val rows = withContext(Dispatchers.IO) {
                runCatching { HomeRow.listFromJson(homeCacheFile(context, profileId).readText()) }.getOrNull().orEmpty()
            }
            if (rows.isEmpty()) {
                hint.text = string(R.string.home_rows_empty)
                return@launch
            }
            rows.forEach { titles[it.id] = it.title }
            ids = orderedIds(rows.map { it.id }, Prefs.homeOrder(context, profileId)).toMutableList()
            hidden = Prefs.homeHidden(context, profileId).toMutableSet()
            for (id in ids) {
                val row = SettingRow(context, titles.getValue(id))
                row.setOnClickListener { openMenu(id) }
                views[id] = row
                showState(id)
                column.addView(row, LinearLayout.LayoutParams(context.dp(ROW_WIDTH), ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            focusDefault()
        }
    }

    private fun showState(id: String) {
        views[id]?.setValue(string(if (id in hidden) R.string.home_row_hidden else R.string.home_row_shown))
    }

    private fun openMenu(id: String) {
        val index = ids.indexOf(id)
        val choices = buildList<Pair<String, () -> Unit>> {
            if (index > 0) add(string(R.string.home_row_up) to { move(id, -1) })
            if (index < ids.lastIndex) add(string(R.string.home_row_down) to { move(id, 1) })
            add(string(if (id in hidden) R.string.home_row_show else R.string.home_row_hide) to { toggle(id) })
        }
        dialog = ChoiceDialog(context, titles.getValue(id), choices).also { it.open(view) }
    }

    /** Moves the row in the list and on screen, then opens its menu again where it landed. */
    private fun move(id: String, by: Int) {
        ids = movedBy(ids, id, by).toMutableList()
        Prefs.setHomeOrder(context, profileId, ids)
        val row = views.getValue(id)
        column.removeView(row)
        column.addView(row, ids.indexOf(id), LinearLayout.LayoutParams(context.dp(ROW_WIDTH), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.requestFocus()
        openMenu(id)
    }

    private fun toggle(id: String) {
        if (!hidden.remove(id)) hidden.add(id)
        Prefs.setHomeHidden(context, profileId, hidden)
        showState(id)
    }

    override fun focusDefault() {
        ids.firstOrNull()?.let { views[it]?.requestFocus() }
    }

    override fun onShown(first: Boolean) {
        clockView.applyClockSetting()
        super.onShown(first)
    }

    override fun onBack(): Boolean {
        dialog?.takeIf { it.isAttachedToWindow }?.let {
            it.close()
            return true
        }
        return false
    }
}

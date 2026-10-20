package cloud.rawkoon.tv.screens

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.Screen
import cloud.rawkoon.tv.data.Licences
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.SettingRow
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.applyClockSetting
import cloud.rawkoon.tv.ui.clock
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.label
import cloud.rawkoon.tv.ui.noClip

/**
 * What the app is made of and under which licence, one row each. The rows take the focus only so
 * that Up and Down scroll the list; OK does nothing. Back leaves.
 */
class LicencesScreen(activity: MainActivity) : Screen(activity) {
    override val view = FrameLayout(context)
    private val rows = mutableListOf<SettingRow>()
    private val clockView = clock(context).apply { applyClockSetting() }

    init {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, context.dp(40))
        }.noClip()
        add(column, string(R.string.licence_app), Licences.APP)
        column.addView(label(context, 13f, Tokens.textMuted, Fonts.bold(context)).apply { text = string(R.string.licence_uses).uppercase() }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = context.dp(28); bottomMargin = context.dp(4) })
        for (credit in Licences.credits) add(column, credit.name, credit.licence)

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
        page.addView(label(context, 34f, Tokens.textPrimary, Fonts.title(context)).apply { text = string(R.string.setting_licences) })
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            marginStart = -context.dp(SCROLL_ROOM)
            topMargin = context.dp(8)
        })
        view.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP))
        view.addView(clockView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END).apply {
            topMargin = context.dp(22)
            marginEnd = context.dp(32)
        })
    }

    private fun add(column: LinearLayout, name: String, licence: String?) {
        val row = SettingRow(context, name)
        row.setValue(licence.orEmpty())
        rows += row
        column.addView(row, LinearLayout.LayoutParams(context.dp(LICENCE_ROW_WIDTH), ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    override fun onShown(first: Boolean) {
        clockView.applyClockSetting()
        super.onShown(first)
    }

    override fun focusDefault() {
        rows.firstOrNull()?.requestFocus()
    }

    private companion object {
        // Wider than a setting row: the longest names would wrap in 640
        const val LICENCE_ROW_WIDTH = 760
    }
}

package cloud.rawkoon.tv.screens

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.Screen
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.applyClockSetting
import cloud.rawkoon.tv.ui.clock
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.label

/**
 * The full text of one licence, as shipped in a raw resource. The scroll view itself takes the
 * focus, so Up and Down page through the text. Back leaves.
 */
class LicenceTextScreen(activity: MainActivity, title: String, text: Int) : Screen(activity) {
    override val view = FrameLayout(context)
    private val scroll = ScrollView(context).apply {
        isFocusable = true
        isVerticalScrollBarEnabled = false
    }
    private val clockView = clock(context).apply { applyClockSetting() }

    init {
        val body = label(context, 13f, Tokens.textPrimary, Fonts.medium(context), Int.MAX_VALUE).apply {
            ellipsize = null
            setLineSpacing(0f, 1.15f)
            this.text = runCatching {
                context.resources.openRawResource(text).bufferedReader().use { it.readText() }.trim()
            }.getOrDefault("")
            setPadding(0, 0, 0, context.dp(40))
        }
        scroll.addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(Tokens.CONTENT_START), context.dp(40), context.dp(Tokens.OVERSCAN_H), 0)
        }
        page.addView(label(context, 34f, Tokens.textPrimary, Fonts.title(context)).apply { this.text = title })
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = context.dp(16) })
        view.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP))
        view.addView(clockView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END).apply {
            topMargin = context.dp(22)
            marginEnd = context.dp(32)
        })
    }

    override fun onShown(first: Boolean) {
        clockView.applyClockSetting()
        super.onShown(first)
    }

    override fun focusDefault() {
        scroll.requestFocus()
    }
}

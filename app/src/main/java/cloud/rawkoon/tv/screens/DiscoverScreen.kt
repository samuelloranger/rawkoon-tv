package cloud.rawkoon.tv.screens

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.label

/** Discover and requests through Rawkoon. Placeholder until the discover work lands. */
class DiscoverScreen(activity: MainActivity, session: JellyfinSession) : RailScreen(activity, session, Destination.DISCOVER) {
    init {
        content.addView(label(context, 18f, Tokens.textSoft, Fonts.medium(context)).apply { text = string(R.string.coming_soon) },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    override fun contentFocus(): View? = null

    override fun focusDefault() {
        rail.activeItem()?.requestFocus()
    }
}

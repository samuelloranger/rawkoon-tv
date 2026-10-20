package cloud.rawkoon.tv.ui

import android.content.Context
import android.view.View
import android.widget.TextClock
import cloud.rawkoon.tv.data.Prefs

/** The clock in the top-right corner, in the TV's format. */
fun clock(context: Context) = TextClock(context).apply {
    textSize = 17f
    setTextColor(Tokens.textPrimary)
    typeface = Fonts.medium(context)
}

/** Shows or hides the [clock] as the Clock setting says; screens call it each time they come to the top. */
fun View.applyClockSetting() {
    visibility = if (Prefs.showClock(context)) View.VISIBLE else View.GONE
}

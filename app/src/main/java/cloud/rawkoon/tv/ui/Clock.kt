package cloud.rawkoon.tv.ui

import android.content.Context
import android.widget.TextClock

/** The clock in the top-right corner, in the TV's format. */
fun clock(context: Context) = TextClock(context).apply {
    textSize = 17f
    setTextColor(Tokens.textPrimary)
    typeface = Fonts.medium(context)
}

package cloud.rawkoon.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * One line of the settings list: the setting's title on the left and its current value on the
 * right. OK opens its choices. Focus lifts it and adds the ring, like the other focusable views.
 */
@SuppressLint("ViewConstructor")
class SettingRow(context: Context, title: String) : Focusable(context) {
    private val ring = FlatView(context)
    override val motion = FocusMotion(this, ring, scale = SCALE)
    private val value: TextView = label(context, 14f, Tokens.accent, Fonts.bold(context)).apply { gravity = Gravity.END }

    init {
        val radius = context.dpf(10f)
        val inset = context.dp(Tokens.RING_INSET)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Tokens.surface, radius)
            setPadding(context.dp(18), 0, context.dp(18), 0)
        }
        content.addView(label(context, 15f, Tokens.textPrimary, Fonts.medium(context)).apply { text = title }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(16) })
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(HEIGHT)).apply { setMargins(inset, inset, inset, inset) })
        ring.background = ringDrawable(context, radius)
        ring.alpha = 0f
        addView(ring, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        listenForFocus()
    }

    /** Shows the setting's current value. */
    fun setValue(text: String) {
        value.text = text
    }

    companion object {
        const val HEIGHT = 44

        /** Wide rows grow only a little: the focus scale of a pill would push their ends far out. */
        private const val SCALE = 1.02f
    }
}

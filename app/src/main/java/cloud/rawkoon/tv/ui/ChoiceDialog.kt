package cloud.rawkoon.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * A small panel of choices over the page, which dims behind it. Lives inside the screen's own
 * view, so Back and focus stay with the screen: call [close] from its onBack.
 */
@SuppressLint("ViewConstructor")
class ChoiceDialog(context: Context, title: String, choices: List<Pair<String, () -> Unit>>) : FrameLayout(context) {
    private val buttons = mutableListOf<PillButton>()
    private var returnFocus: View? = null

    init {
        isClickable = true
        setBackgroundColor(0xB3000000.toInt())
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(Tokens.surface, context.dpf(14f))
            setPadding(context.dp(28), context.dp(22), context.dp(28), context.dp(18))
            // The choices share one width, wide enough for their labels
            minimumWidth = context.dp(300)
        }
        panel.addView(label(context, 20f, Tokens.textPrimary, Fonts.title(context)).apply { text = title }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = context.dp(14) })
        for ((text, action) in choices) {
            val button = PillButton(context, text)
            button.setOnClickListener {
                close()
                action()
            }
            buttons += button
            panel.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        addView(panel, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    fun open(parent: ViewGroup) {
        returnFocus = parent.findFocus()
        parent.addView(this, fill())
        alpha = 0f
        animate().alpha(1f).setDuration(Tokens.SCREEN_FADE_MS).start()
        buttons.firstOrNull()?.requestFocus()
    }

    fun close() {
        (parent as? ViewGroup)?.removeView(this)
        returnFocus?.takeIf { it.isAttachedToWindow }?.requestFocus()
    }

    /** Keeps focus inside the panel while it is open. */
    override fun focusSearch(focused: View?, direction: Int): View? {
        val next = super.focusSearch(focused, direction)
        return if (next != null && buttons.contains(next)) next else focused
    }
}

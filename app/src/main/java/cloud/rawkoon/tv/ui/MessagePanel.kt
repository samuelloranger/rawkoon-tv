package cloud.rawkoon.tv.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * A centered message for a page with nothing to show: an empty page, an error, an account to
 * link. It can carry one button, which a page may ask to take focus. Hidden until shown.
 */
class MessagePanel(context: Context) : LinearLayout(context) {
    /** The button of the message on screen, if it has one. */
    var button: PillButton? = null
        private set

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        visibility = GONE
    }

    /**
     * Shows [text] and, with an [action] (the button's label and what it does), a button under it;
     * the button takes focus only if [takeFocus], and sends Up to [upId].
     */
    fun show(text: String, action: Pair<String, () -> Unit>? = null, takeFocus: Boolean = false, upId: Int = View.NO_ID) {
        removeAllViews()
        button = null
        addView(label(context, TEXT_SP, Tokens.textSoft, Fonts.medium(context)).apply { this.text = text })
        if (action != null) addButton(action, upId, gap = BUTTON_GAP)
        visibility = VISIBLE
        if (takeFocus) button?.requestFocus()
    }

    /** A headline, an optional [hint] under it and a button, which takes focus. */
    fun showNotice(title: String, hint: String?, action: Pair<String, () -> Unit>) {
        removeAllViews()
        button = null
        addView(label(context, NOTICE_TITLE_SP, Tokens.textPrimary, Fonts.title(context)).apply { text = title })
        hint?.let { addView(label(context, NOTICE_HINT_SP, Tokens.textSoft, Fonts.medium(context)).apply { text = it }, spaced(NOTICE_HINT_GAP)) }
        addButton(action, View.NO_ID, gap = NOTICE_BUTTON_GAP)
        visibility = VISIBLE
        button?.requestFocus()
    }

    fun hide() {
        visibility = GONE
        button = null
    }

    private fun addButton(action: Pair<String, () -> Unit>, upId: Int, gap: Int) {
        val pill = PillButton(context, action.first, primary = true)
        pill.setOnClickListener { action.second() }
        pill.nextFocusUpId = upId
        addView(pill, spaced(gap))
        button = pill
    }

    private fun spaced(top: Int) = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(top) }

    companion object {
        private const val TEXT_SP = 16f
        private const val BUTTON_GAP = 12
        private const val NOTICE_TITLE_SP = 20f
        private const val NOTICE_HINT_SP = 15f
        private const val NOTICE_HINT_GAP = 8
        private const val NOTICE_BUTTON_GAP = 16

        /** Where a page puts its panel: centered. */
        fun params() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
    }
}

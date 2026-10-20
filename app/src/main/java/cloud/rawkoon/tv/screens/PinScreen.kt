package cloud.rawkoon.tv.screens

import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.Screen
import cloud.rawkoon.tv.data.Profile
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.Icon
import cloud.rawkoon.tv.ui.PillButton
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.label
import cloud.rawkoon.tv.ui.noClip

/**
 * A profile's PIN: checking it before the profile opens, or choosing a new one (entered twice).
 * The remote's number keys work, and so does the on-screen keypad.
 */
class PinScreen(activity: MainActivity, private val mode: Mode, private val onDone: (String) -> Unit) : Screen(activity) {
    sealed interface Mode {
        val profile: Profile

        data class Verify(override val profile: Profile) : Mode
        data class Create(override val profile: Profile) : Mode
    }

    override val view = FrameLayout(context)
    private val title = label(context, 26f, Tokens.textPrimary, Fonts.title(context))
    private val message = label(context, 14f, Tokens.textMuted, Fonts.medium(context))
    private val dots = TextView(context).apply {
        textSize = 30f
        setTextColor(Tokens.textPrimary)
        letterSpacing = 0.4f
        gravity = Gravity.CENTER
    }
    private val keypad = GridLayout(context).apply { columnCount = 3 }.noClip()
    private var entered = ""
    private var first: String? = null

    init {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }.noClip()
        column.addView(title)
        column.addView(message, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(8) })
        column.addView(dots, LinearLayout.LayoutParams(context.dp(260), context.dp(48)).apply { topMargin = context.dp(14) })
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", Icon.BACK, "0", Icon.CHECK)
        for (key in keys) {
            val glyph = key == Icon.BACK || key == Icon.CHECK
            val button = PillButton(context, if (glyph) "" else key, if (glyph) key else null, primary = key == Icon.CHECK, height = 44)
            button.setOnClickListener {
                when (key) {
                    Icon.BACK -> erase()
                    Icon.CHECK -> submit()
                    else -> type(key)
                }
            }
            keypad.addView(button, GridLayout.LayoutParams().apply { width = context.dp(78); setMargins(context.dp(4), context.dp(2), context.dp(4), context.dp(2)) })
        }
        column.addView(keypad, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(12) })
        view.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        reset()
    }

    private fun reset() {
        entered = ""
        title.text = when (mode) {
            is Mode.Verify -> string(R.string.pin_title, mode.profile.name)
            is Mode.Create -> string(if (first == null) R.string.pin_new else R.string.pin_confirm)
        }
        render()
    }

    private fun render() {
        dots.text = if (entered.isEmpty()) "" else "●".repeat(entered.length)
    }

    private fun type(digit: String) {
        if (entered.length >= MAX) return
        entered += digit
        message.text = ""
        render()
        // A right PIN opens at once; a wrong one waits for the check key
        if (mode is Mode.Verify && entered.length >= MIN && mode.profile.checkPin(entered)) finish(entered)
    }

    private fun erase() {
        entered = entered.dropLast(1)
        render()
    }

    private fun submit() {
        if (entered.length < MIN) {
            message.text = string(R.string.pin_too_short)
            return
        }
        when (mode) {
            is Mode.Verify -> if (mode.profile.checkPin(entered)) finish(entered) else {
                message.text = string(R.string.pin_wrong)
                reset()
            }
            is Mode.Create -> when (val previous = first) {
                null -> {
                    first = entered
                    reset()
                }
                entered -> finish(entered)
                else -> {
                    first = null
                    message.text = string(R.string.pin_mismatch)
                    reset()
                }
            }
        }
    }

    private fun finish(pin: String) {
        activity.pop()
        onDone(pin)
    }

    override fun focusDefault() {
        keypad.getChildAt(0)?.requestFocus()
    }

    override fun onKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val code = event.keyCode
        if (code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            type((code - KeyEvent.KEYCODE_0).toString())
            return true
        }
        if (code == KeyEvent.KEYCODE_DEL) {
            erase()
            return true
        }
        return false
    }

    private companion object {
        const val MIN = 4
        const val MAX = 8
    }
}


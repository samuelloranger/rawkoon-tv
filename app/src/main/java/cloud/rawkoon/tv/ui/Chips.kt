package cloud.rawkoon.tv.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout

/** One piece of a meta line: plain text, an outlined chip, the accent "SHOW" chip, or the score. */
sealed interface Chip {
    data class Text(val text: String) : Chip
    data class Outline(val text: String) : Chip
    data class Kind(val text: String) : Chip
    data class Score(val text: String) : Chip
}

/**
 * A meta line like "SHOW 2023 – · 2 seasons · 16 episodes · CA-18+ ★ 8.4". Text parts are
 * separated by dots; chips stand on their own.
 */
fun chipRow(context: Context, chips: List<Chip>, size: Float = 13f): LinearLayout = LinearLayout(context).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    var previousText = false
    for (chip in chips) {
        val gap = context.dp(if (chip is Chip.Text && previousText) 0 else 8).takeIf { childCount > 0 } ?: 0
        val view = when (chip) {
            is Chip.Text -> label(context, size, Tokens.textSoft, Fonts.medium(context)).apply {
                text = if (previousText) " · ${chip.text}" else chip.text
            }
            is Chip.Score -> label(context, size, Tokens.accent, Fonts.bold(context)).apply { text = chip.text }
            is Chip.Outline, is Chip.Kind -> {
                val accent = chip is Chip.Kind
                label(context, size - 2f, if (accent) Tokens.accent else Tokens.textSoft, Fonts.bold(context)).apply {
                    text = if (chip is Chip.Kind) chip.text.uppercase() else (chip as Chip.Outline).text
                    letterSpacing = if (accent) 0.06f else 0f
                    background = GradientDrawable().apply {
                        cornerRadius = context.dpf(5f)
                        setStroke(context.dp(1), if (accent) Tokens.accent else 0x2EFFFFFF)
                    }
                    setPadding(context.dp(6), context.dp(2), context.dp(6), context.dp(2))
                }
            }
        }
        addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = gap })
        previousText = chip is Chip.Text
    }
}

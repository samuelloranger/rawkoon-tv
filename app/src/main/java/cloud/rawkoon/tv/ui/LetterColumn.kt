package cloud.rawkoon.tv.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View

/**
 * The A to Z column on the right edge of a grid, with "#" for titles that start with something
 * else. One view that draws every letter, instead of 27 focusable ones: Up and Down move the
 * ring, and the jump happens once it rests on a letter (or at once on OK).
 */
class LetterColumn(context: Context, private val onLetter: (Char) -> Unit) : View(context) {
    /** Where Left goes: back to the grid. */
    var onLeft: () -> View? = { null }

    /** The letter the grid was last sent to, tinted while the column is not focused; null when the grid is not sorted by title. */
    var active: Char? = null
        set(value) {
            field = value
            invalidate()
        }

    private var selected = 0
    private val main = Handler(Looper.getMainLooper())
    private val jump = Runnable { choose() }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.dpf(10f)
        typeface = Fonts.bold(context)
        textAlign = Paint.Align.CENTER
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(1.5f)
        color = Tokens.ring
    }
    private val box = RectF()

    init {
        isFocusable = true
        isClickable = true
        setOnClickListener {
            main.removeCallbacks(jump)
            choose()
        }
        setOnFocusChangeListener { _, _ -> invalidate() }
    }

    private fun choose() {
        active = LETTERS[selected]
        onLetter(LETTERS[selected])
    }

    override fun onDraw(canvas: Canvas) {
        val row = height.toFloat() / LETTERS.size
        for ((i, letter) in LETTERS.withIndex()) {
            val cy = row * (i + 0.5f)
            val on = hasFocus() && i == selected
            text.color = when {
                on -> Tokens.textPrimary
                letter == active -> Tokens.accent
                else -> Tokens.textMuted
            }
            if (on) {
                val r = row / 2 - outline.strokeWidth / 2
                box.set(outline.strokeWidth, cy - r, width - outline.strokeWidth, cy + r)
                canvas.drawRoundRect(box, r, r, outline)
            }
            canvas.drawText(LABELS[i], width / 2f, cy - (text.descent() + text.ascent()) / 2, text)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                selected = (selected + if (keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1).coerceIn(0, LETTERS.size - 1)
                invalidate()
                main.removeCallbacks(jump)
                main.postDelayed(jump, SETTLE_MS)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> return true.also { onLeft()?.requestFocus() }
            KeyEvent.KEYCODE_DPAD_RIGHT -> return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDetachedFromWindow() {
        main.removeCallbacks(jump)
        super.onDetachedFromWindow()
    }

    companion object {
        val LETTERS: List<Char> = listOf('#') + ('A'..'Z')

        /** [LETTERS] as strings, so drawing allocates none. */
        private val LABELS = LETTERS.map { it.toString() }

        /** How long the ring rests on a letter before the grid jumps to it. */
        private const val SETTLE_MS = 350L
    }
}

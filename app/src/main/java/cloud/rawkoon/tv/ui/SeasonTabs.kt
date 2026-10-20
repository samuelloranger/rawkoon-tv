package cloud.rawkoon.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.TextView
import cloud.rawkoon.tv.jellyfin.MediaItem

/** The season tabs. Coming from below, focus lands on the selected one. */
class TabsRow(context: Context) : EntryRow(context) {
    private val tabs = mutableListOf<Tab>()

    init {
        orientation = HORIZONTAL
        noClip()
    }

    fun set(seasons: List<MediaItem>, onLeave: () -> Unit, onPick: (MediaItem, focused: Boolean) -> Unit) {
        removeAllViews()
        tabs.clear()
        for (season in seasons) {
            val tab = Tab(context, season)
            tab.setOnFocusChangeListener { _, focused ->
                tab.focusChanged(focused)
                if (focused) onPick(season, true) else onLeave()
            }
            tab.setOnClickListener { onPick(season, false) }
            tabs += tab
            addView(tab)
        }
    }

    fun select(id: String) = tabs.forEach { it.current = it.season.id == id }

    fun focusSelected() = (tabs.firstOrNull { it.current } ?: tabs.firstOrNull())?.requestFocus()

    override fun entry(): View? = tabs.firstOrNull { it.current }
}

/** One season tab: its name, an apricot underline when it is the one shown, a ring when focused. */
@SuppressLint("ViewConstructor")
class Tab(context: Context, val season: MediaItem) : Focusable(context) {
    private val text: TextView = label(context, 14f, Tokens.textMuted, Fonts.bold(context)).apply {
        text = season.title
        gravity = Gravity.CENTER
    }
    private val ring = FlatView(context)
    override val motion = FocusMotion(this, ring)
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Tokens.accent }
    private val rect = RectF()
    private val focusedBackground = GradientDrawable().apply {
        cornerRadius = context.dpf(15f)
        setColor(Tokens.surfaceFocused)
    }
    private val barInset = context.dpf(Tokens.RING_INSET.toFloat()) + context.dpf(12f)
    private val barBottom = context.dpf(Tokens.RING_INSET.toFloat()) + context.dpf(2f)
    private val barHeight = context.dpf(2.5f)

    var current = false
        set(value) {
            field = value
            paint()
            invalidate()
        }

    init {
        setWillNotDraw(false)
        val inset = context.dp(Tokens.RING_INSET)
        text.setPadding(context.dp(12), 0, context.dp(12), 0)
        addView(text, LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(30)).apply { setMargins(inset, inset, inset, inset) })
        ring.background = GradientDrawable().apply {
            cornerRadius = context.dpf(15f) + inset
            setStroke(context.dp(2), Tokens.ring)
        }
        ring.alpha = 0f
        addView(ring, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun focusChanged(focused: Boolean) {
        motion.focus(focused)
        paint()
    }

    private fun paint() {
        text.setTextColor(if (current || isFocused) Tokens.textPrimary else Tokens.textMuted)
        text.background = if (isFocused) focusedBackground else null
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!current || isFocused) return
        val y = height - barBottom
        rect.set(barInset, y, width - barInset, y + barHeight)
        canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, bar)
    }
}

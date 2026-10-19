package cloud.rawkoon.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import cloud.rawkoon.tv.data.Images
import cloud.rawkoon.tv.jellyfin.MediaItem

/** The focus ring: an apricot stroke, a small gap away from the art. */
fun ringDrawable(context: Context, radius: Float) = GradientDrawable().apply {
    cornerRadius = radius + context.dpf(Tokens.RING_INSET.toFloat())
    setStroke(context.dp(2), Tokens.ring)
}

/** Scales a focusable view up and fades its ring in; both are render properties, so focus never redraws content. */
class FocusMotion(private val target: View, private val ring: View?) {
    fun focus(focused: Boolean) {
        val duration = if (focused) Tokens.FOCUS_IN_MS else Tokens.FOCUS_OUT_MS
        val scale = if (focused) Tokens.FOCUS_SCALE else 1f
        target.animate().scaleX(scale).scaleY(scale).setDuration(duration).setInterpolator(Tokens.ease).start()
        ring?.animate()?.alpha(if (focused) 1f else 0f)?.setDuration(duration)?.setInterpolator(Tokens.ease)?.start()
        // The focused view draws above its neighbours (no outline, so no shadow)
        target.translationZ = if (focused) 1f else 0f
    }

    fun press(down: Boolean) {
        val scale = if (down) Tokens.PRESS_SCALE else Tokens.FOCUS_SCALE
        target.animate().scaleX(scale).scaleY(scale).setDuration(if (down) Tokens.PRESS_MS else Tokens.FOCUS_IN_MS)
            .setInterpolator(Tokens.ease).start()
    }
}

fun isConfirmKey(keyCode: Int) =
    keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

/**
 * A poster or landscape card for one item. The art is decoded at its exact size with its corners
 * already rounded, so drawing it is a single blit; progress and the watched badge are drawn once.
 */
@SuppressLint("ViewConstructor")
class Card(
    context: Context,
    val item: MediaItem,
    val landscape: Boolean,
    images: Images,
    private val onFocused: (Card) -> Unit,
    private val onClick: (Card) -> Unit,
) : FrameLayout(context) {
    val key: String get() = item.id
    private val art = ImageView(context)
    private val ring = FlatView(context)
    private val motion = FocusMotion(this, ring)

    @Volatile
    private var alive = true

    init {
        isFocusable = true
        isClickable = true
        contentDescription = item.headerTitle
        val inset = context.dp(Tokens.RING_INSET)
        val w = context.dp(if (landscape) Tokens.LAND_W else Tokens.POSTER_W)
        val h = context.dp(if (landscape) Tokens.LAND_H else Tokens.POSTER_H)
        val radius = context.dpf(Tokens.CARD_RADIUS)
        layoutParams = LinearLayout.LayoutParams(w + inset * 2, h + inset * 2)
        art.background = rounded(Tokens.surfaceHigh, radius)
        addView(art, LayoutParams(w, h).apply { setMargins(inset, inset, inset, inset) })
        if (landscape && item.progress != null) {
            addView(CardProgress(context, item.progress), LayoutParams(w, h).apply { setMargins(inset, inset, inset, inset) })
        }
        if (item.played) addView(WatchedBadge(context), LayoutParams(w, h).apply { setMargins(inset, inset, inset, inset) })
        ring.background = ringDrawable(context, radius)
        ring.alpha = 0f
        addView(ring, fill())

        val url = if (landscape) item.thumbUrl else item.posterUrl
        if (url != null) {
            images.load("$url&fillWidth=$w", w, h, radius, opaque = false, isCurrent = { alive }) { art.setImageBitmap(it) }
        }
        setOnFocusChangeListener { _, focused ->
            motion.focus(focused)
            (parent as? View)?.invalidate()
            if (focused) onFocused(this)
        }
        setOnClickListener { onClick(this) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        alive = true
    }

    override fun onDetachedFromWindow() {
        alive = false
        super.onDetachedFromWindow()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isConfirmKey(keyCode) && event.repeatCount == 0) motion.press(true)
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isConfirmKey(keyCode) && hasFocus()) motion.press(false)
        return super.onKeyUp(keyCode, event)
    }
}

/** Watch progress along the bottom edge; drawn once, never animated. */
@SuppressLint("ViewConstructor")
class CardProgress(context: Context, private val progress: Float) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFFFFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Tokens.accent }
    private val height = context.dpf(3.5f)
    private val margin = context.dpf(7f)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val r = height / 2
        val bottom = getHeight() - margin
        rect.set(margin, bottom - height, width - margin, bottom)
        canvas.drawRoundRect(rect, r, r, track)
        rect.right = margin + (width - margin * 2) * progress
        canvas.drawRoundRect(rect, r, r, fill)
    }
}

/** The apricot check in the top-right corner of something already watched. */
class WatchedBadge(context: Context) : View(context) {
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Tokens.accent }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Tokens.background
        typeface = Fonts.icons(context)
        textSize = context.dpf(9.5f)
        textAlign = Paint.Align.CENTER
    }
    private val size = context.dpf(19f)
    private val margin = context.dpf(5f)

    override fun onDraw(canvas: Canvas) {
        val cx = width - margin - size / 2
        val cy = margin + size / 2
        canvas.drawCircle(cx, cy, size / 2, disc)
        canvas.drawText(Icon.CHECK, cx, cy - (glyph.descent() + glyph.ascent()) / 2, glyph)
    }
}

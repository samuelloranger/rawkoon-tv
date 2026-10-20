package cloud.rawkoon.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import cloud.rawkoon.tv.data.Images
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.jellyfin.Person

/** The focus ring: an apricot stroke, a small gap away from the art. */
fun ringDrawable(context: Context, radius: Float) = GradientDrawable().apply {
    cornerRadius = radius + context.dpf(Tokens.RING_INSET.toFloat())
    setStroke(context.dp(2), Tokens.ring)
}

/** Scales a focusable view up and fades its ring in; both are render properties, so focus never redraws content. */
class FocusMotion(private val target: View, private val ring: View?, private val scale: Float = Tokens.FOCUS_SCALE) {
    fun focus(focused: Boolean) {
        val duration = if (focused) Tokens.FOCUS_IN_MS else Tokens.FOCUS_OUT_MS
        val to = if (focused) scale else 1f
        target.animate().scaleX(to).scaleY(to).setDuration(duration).setInterpolator(Tokens.ease).start()
        ring?.animate()?.alpha(if (focused) 1f else 0f)?.setDuration(duration)?.setInterpolator(Tokens.ease)?.start()
        // The focused view draws above its neighbours (no outline, so no shadow)
        target.translationZ = if (focused) 1f else 0f
    }

    fun press(down: Boolean) {
        val to = if (down) Tokens.PRESS_SCALE else scale
        target.animate().scaleX(to).scaleY(to).setDuration(if (down) Tokens.PRESS_MS else Tokens.FOCUS_IN_MS)
            .setInterpolator(Tokens.ease).start()
    }
}

fun isConfirmKey(keyCode: Int) =
    keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

/**
 * A focusable frame with the shared focus behaviour: it grows, its ring fades in, and OK presses
 * it down a little. [motionTarget] is what scales (the art, for cards with a caption under it).
 */
abstract class Focusable(context: Context) : FrameLayout(context) {
    protected abstract val motion: FocusMotion

    init {
        isFocusable = true
        isClickable = true
        noClip()
    }

    /**
     * Rings and backgrounds that match this view are sized to it here: FrameLayout only measures
     * a match-parent child again when it has more than one.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        for (i in 0 until childCount) {
            val child = getChildAt(i) as? FlatView ?: continue
            val params = child.layoutParams
            if (params.width != LayoutParams.MATCH_PARENT && params.height != LayoutParams.MATCH_PARENT) continue
            val w = if (params.width == LayoutParams.MATCH_PARENT) measuredWidth else child.measuredWidth
            val h = if (params.height == LayoutParams.MATCH_PARENT) measuredHeight else child.measuredHeight
            child.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        }
    }

    protected fun listenForFocus(onFocused: () -> Unit = {}) {
        setOnFocusChangeListener { _, focused ->
            motion.focus(focused)
            (parent as? View)?.invalidate()
            if (focused) onFocused()
        }
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

/**
 * A poster or landscape card for one item, optionally captioned under the art (episodes, seasons).
 * The art is decoded at its exact size with its corners already rounded, so drawing it is a single
 * blit; progress and the watched badge are drawn once.
 */
@SuppressLint("ViewConstructor")
class Card(
    context: Context,
    val item: MediaItem,
    val landscape: Boolean,
    images: Images,
    private val onFocused: (Card) -> Unit,
    private val onClick: (Card) -> Unit,
    caption: Pair<String, String?>? = null,
) : Focusable(context), RowItem {
    override val key: String get() = item.id
    private val art = ImageView(context)
    private val frame = FrameLayout(context).noClip()
    private val ring = FlatView(context)
    override val motion = FocusMotion(frame, ring)

    @Volatile
    private var alive = true

    init {
        contentDescription = item.headerTitle
        val inset = context.dp(Tokens.RING_INSET)
        val w = context.dp(if (landscape) Tokens.LAND_W else Tokens.POSTER_W)
        val h = context.dp(if (landscape) Tokens.LAND_H else Tokens.POSTER_H)
        val radius = context.dpf(Tokens.CARD_RADIUS)
        val captionH = if (caption != null) context.dp(CAPTION_DP) else 0
        layoutParams = LinearLayout.LayoutParams(w + inset * 2, h + inset * 2 + captionH)
        art.background = rounded(Tokens.surfaceHigh, radius)
        val inner = { FrameLayout.LayoutParams(w, h).apply { setMargins(inset, inset, inset, inset) } }
        frame.addView(art, inner())
        if (item.progress != null && (landscape || item.kind == MediaItem.Kind.EPISODE)) frame.addView(CardProgress(context, item.progress), inner())
        if (item.played) frame.addView(WatchedBadge(context), inner())
        ring.background = ringDrawable(context, radius)
        ring.alpha = 0f
        frame.addView(ring, FrameLayout.LayoutParams(w + inset * 2, h + inset * 2))
        addView(frame, LayoutParams(w + inset * 2, h + inset * 2))
        if (caption != null) {
            val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            column.addView(label(context, 12.5f, Tokens.textPrimary, Fonts.bold(context)).apply { text = caption.first })
            caption.second?.let { sub ->
                column.addView(label(context, 11f, Tokens.textMuted, Fonts.medium(context)).apply { text = sub })
            }
            addView(column, LayoutParams(w, captionH, Gravity.BOTTOM).apply { leftMargin = inset })
        }

        val url = if (landscape) item.thumbUrl else item.posterUrl
        if (url != null) {
            images.load("$url&fillWidth=$w", w, h, radius, opaque = false, isCurrent = { alive }) { art.setImageBitmap(it) }
        }
        listenForFocus { onFocused(this) }
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

    private companion object {
        const val CAPTION_DP = 38
    }
}

/** A cast member: a round photo (initials when there is none), the name and the role. */
@SuppressLint("ViewConstructor")
class PersonCard(context: Context, val person: Person, images: Images) : Focusable(context), RowItem {
    override val key: String get() = person.id
    private val photo = FrameLayout(context).noClip()
    private val ring = FlatView(context)
    override val motion = FocusMotion(photo, ring, 1.08f)

    @Volatile
    private var alive = true

    init {
        contentDescription = person.name
        val size = context.dp(SIZE)
        val gap = context.dp(4)
        layoutParams = LinearLayout.LayoutParams(context.dp(W), size + context.dp(48))
        photo.addView(label(context, 22f, Tokens.textSoft, Fonts.title(context)).apply {
            text = person.name.take(1)
            gravity = Gravity.CENTER
            background = oval(Tokens.surfaceHigh)
        }, FrameLayout.LayoutParams(size, size))
        val image = ImageView(context)
        photo.addView(image, FrameLayout.LayoutParams(size, size))
        person.imageUrl?.let { url ->
            images.load("$url&fillWidth=$size", size, size, size / 2f, isCurrent = { alive }) { image.setImageBitmap(it) }
        }
        ring.background = oval(0).apply { setStroke(context.dp(2), Tokens.ring) }
        ring.alpha = 0f
        photo.addView(ring, FrameLayout.LayoutParams(size + gap * 2, size + gap * 2).apply { setMargins(-gap, -gap, -gap, -gap) })
        addView(photo, LayoutParams(size, size, Gravity.CENTER_HORIZONTAL))
        val text = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(label(context, 11.5f, Tokens.textPrimary, Fonts.bold(context)).apply { this.text = person.name; gravity = Gravity.CENTER })
            person.role?.let { role -> addView(label(context, 10.5f, Tokens.textMuted, Fonts.medium(context)).apply { this.text = role; gravity = Gravity.CENTER }) }
        }
        addView(text, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        listenForFocus()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        alive = true
    }

    override fun onDetachedFromWindow() {
        alive = false
        super.onDetachedFromWindow()
    }

    private companion object {
        const val SIZE = 66
        const val W = 92
    }
}

fun oval(color: Int) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
}

/** A round action button with an icon: watched, favorite, more. [setOn] paints the icon apricot. */
class CircleButton(context: Context, glyph: String, description: String) : Focusable(context) {
    private val ring = FlatView(context)
    private val icon: TextView = icon(context, glyph, 14f, Tokens.textPrimary)
    override val motion = FocusMotion(this, ring)

    init {
        contentDescription = description
        val inset = context.dp(Tokens.RING_INSET)
        val size = context.dp(SIZE)
        icon.background = oval(0x24FFFFFF)
        addView(icon, LayoutParams(size, size).apply { setMargins(inset, inset, inset, inset) })
        ring.background = oval(0).apply { setStroke(context.dp(2), Tokens.ring) }
        ring.alpha = 0f
        addView(ring, LayoutParams(size + inset * 2, size + inset * 2))
        listenForFocus()
    }

    fun setOn(on: Boolean) = icon.setTextColor(if (on) Tokens.accent else Tokens.textPrimary)

    companion object {
        const val SIZE = 40
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

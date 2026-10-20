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
import android.widget.LinearLayout
import android.widget.TextView
import cloud.rawkoon.tv.data.Images
import cloud.rawkoon.tv.jellyfin.Badge
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.jellyfin.Person

/** The focus ring: an apricot stroke, a small gap away from the art. */
fun ringDrawable(context: Context, radius: Float) = GradientDrawable().apply {
    cornerRadius = radius + context.dpf(Tokens.RING_INSET.toFloat())
    setStroke(context.dp(2), Tokens.ring)
}

/** An oval ring stroke, the round counterpart of [ringDrawable]. */
fun ovalRing(context: Context) = oval(0).apply { setStroke(context.dp(2), Tokens.ring) }

/**
 * Scales a focusable view up and fades its ring in; both are render properties, so focus never
 * redraws content. [raise] lifts the target above its siblings while focused: for views that
 * overlap their neighbours directly. Cards in a [FocusRow] leave it off, as the row orders its own drawing.
 */
class FocusMotion(
    private val target: View,
    private val ring: View?,
    private val scale: Float = Tokens.FOCUS_SCALE,
    private val raise: Boolean = true,
) {
    fun focus(focused: Boolean) {
        val duration = if (focused) Tokens.FOCUS_IN_MS else Tokens.FOCUS_OUT_MS
        val to = if (focused) scale else 1f
        target.animate().scaleX(to).scaleY(to).setDuration(duration).setInterpolator(Tokens.ease).start()
        ring?.animate()?.alpha(if (focused) 1f else 0f)?.setDuration(duration)?.setInterpolator(Tokens.ease)?.start()
        // The focused view draws above its neighbours (no outline, so no shadow)
        if (raise) target.translationZ = if (focused) 1f else 0f
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
 * A poster or landscape card for one item, optionally captioned under the art (episodes, seasons,
 * TMDB titles). The art is decoded at its exact size with its corners already rounded, so drawing
 * it is a single blit; progress, the watched badge and a TMDB title's status badge are drawn once.
 */
@SuppressLint("ViewConstructor")
class Card(
    context: Context,
    val item: MediaItem,
    val landscape: Boolean,
    private val images: Images,
    private val onFocused: (Card) -> Unit,
    private val onClick: (Card) -> Unit,
    caption: Pair<String, String?>? = null,
) : Focusable(context), RowItem {
    override val key: String get() = item.id
    private val art = BitmapView(context)
    private val frame = FrameLayout(context).noClip()
    private val ring = FlatView(context)
    override val motion = FocusMotion(frame, ring, raise = false)
    private val artW = context.dp(if (landscape) Tokens.LAND_W else Tokens.POSTER_W)
    private val artH = context.dp(if (landscape) Tokens.LAND_H else Tokens.POSTER_H)
    private val artRadius = context.dpf(Tokens.CARD_RADIUS)

    @Volatile
    private var alive = false

    init {
        contentDescription = item.headerTitle
        val inset = context.dp(Tokens.RING_INSET)
        val w = artW
        val h = artH
        val radius = artRadius
        val captionH = if (caption != null) context.dp(CAPTION_DP) else 0
        val badge = item.tmdb?.badge
        layoutParams = LinearLayout.LayoutParams(w + inset * 2, h + inset * 2 + captionH)
        art.placeholder = rounded(Tokens.surfaceHigh, radius)
        val inner = { FrameLayout.LayoutParams(w, h).apply { setMargins(inset, inset, inset, inset) } }
        frame.addView(art, inner())
        if (item.progress != null && (landscape || item.kind == MediaItem.Kind.EPISODE)) frame.addView(CardProgress(context, item.progress), inner())
        if (item.played) frame.addView(WatchedBadge(context), inner())
        badge?.let {
            frame.addView(badgeLabel(it, w - context.dp(12)), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START,
            ).apply { setMargins(inset + context.dp(6), 0, 0, inset + context.dp(6)) })
        }
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
        listenForFocus { onFocused(this) }
        setOnClickListener { onClick(this) }
    }

    private fun badgeLabel(badge: Badge, maxWidth: Int): View {
        val (background, text) = badgeColors(badge.tone)
        return label(context, 10f, text, Fonts.bold(context)).apply {
            this.text = badge.text
            this.maxWidth = maxWidth
            this.background = rounded(background, context.dpf(4f))
            setPadding(context.dp(5), context.dp(2), context.dp(5), context.dp(2))
        }
    }

    /**
     * The art is requested once the card is on a window, not when the row is built: a page builds
     * every card up front. A card detached before its art arrives drops the request and asks again
     * on the next attach. Arrived art is kept on detach: it is already in memory, and dropping it
     * would only flash the placeholder when the card comes back.
     */
    private fun requestArt() {
        if (art.bitmap != null) return
        val url = (if (landscape) item.thumbUrl else item.posterUrl) ?: return
        // A TMDB image's URL is final; a library image is asked at the card's width
        images.load(if (item.tmdb != null) url else "$url&fillWidth=$artW", artW, artH, artRadius, opaque = false, isCurrent = { alive }) { art.bitmap = it }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        alive = true
        requestArt()
    }

    override fun onDetachedFromWindow() {
        alive = false
        super.onDetachedFromWindow()
    }

    private companion object {
        const val CAPTION_DP = 38
    }
}

/** What a card captions under its art: a TMDB title's name and year or air date, an episode's show and "S1 · E3 · Title"; nothing for the rest. */
fun cardCaption(item: MediaItem): Pair<String, String?>? {
    item.tmdb?.let { return item.title to (it.subtitle ?: item.year?.toString()) }
    if (item.kind != MediaItem.Kind.EPISODE) return null
    return item.headerTitle to listOfNotNull(item.episodeLabel, item.title).joinToString(" · ")
}

/** Background and text colour of a badge, by tone. */
fun badgeColors(tone: Badge.Tone): Pair<Int, Int> = when (tone) {
    Badge.Tone.NEUTRAL -> 0xCC000000.toInt() to Tokens.textPrimary
    Badge.Tone.WAITING -> 0xFFE0A93B.toInt() to Tokens.background
    Badge.Tone.ACTIVE -> 0xFF5BA8D8.toInt() to Tokens.background
    Badge.Tone.READY -> 0xFF52B788.toInt() to Tokens.background
    Badge.Tone.REFUSED -> 0xFFC8564D.toInt() to Tokens.textPrimary
}

/** A cast member: a round photo (initials when there is none), the name and the role. */
@SuppressLint("ViewConstructor")
class PersonCard(context: Context, val person: Person, private val images: Images) : Focusable(context), RowItem {
    override val key: String get() = person.id
    private val photo = FrameLayout(context).noClip()
    private val ring = FlatView(context)
    override val motion = FocusMotion(photo, ring, 1.08f, raise = false)
    private val size = context.dp(SIZE)
    private val initials = label(context, 22f, Tokens.textSoft, Fonts.title(context))
    private val image = BitmapView(context)

    @Volatile
    private var alive = false

    init {
        contentDescription = person.name
        val gap = context.dp(4)
        layoutParams = LinearLayout.LayoutParams(context.dp(W), size + context.dp(48))
        photo.addView(initials.apply {
            text = person.name.take(1)
            gravity = Gravity.CENTER
            background = oval(Tokens.surfaceHigh)
        }, FrameLayout.LayoutParams(size, size))
        photo.addView(image, FrameLayout.LayoutParams(size, size))
        ring.background = ovalRing(context)
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

    /** Requested on attach, like [Card]'s art. */
    private fun requestPhoto() {
        if (image.bitmap != null) return
        val url = person.imageUrl ?: return
        images.load("$url&fillWidth=$size", size, size, size / 2f, isCurrent = { alive }) {
            image.bitmap = it
            // Invisible, not gone: no layout, and nothing draws under the photo
            initials.visibility = View.INVISIBLE
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        alive = true
        requestPhoto()
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
class CircleButton(context: Context, glyph: String, description: String, private val filled: Boolean = false) : Focusable(context) {
    private val ring = FlatView(context)
    private val icon: TextView = icon(context, glyph, 14f, if (filled) Tokens.background else Tokens.textPrimary)
    override val motion = FocusMotion(this, ring)

    init {
        contentDescription = description
        val inset = context.dp(Tokens.RING_INSET)
        val size = context.dp(SIZE)
        icon.background = oval(if (filled) Tokens.textPrimary else 0x24FFFFFF)
        addView(icon, LayoutParams(size, size).apply { setMargins(inset, inset, inset, inset) })
        ring.background = ovalRing(context)
        ring.alpha = 0f
        addView(ring, LayoutParams(size + inset * 2, size + inset * 2))
        listenForFocus()
    }

    fun setOn(on: Boolean) = icon.setTextColor(if (on) Tokens.accent else if (filled) Tokens.background else Tokens.textPrimary)

    fun setGlyph(glyph: String) {
        icon.text = glyph
    }

    companion object {
        const val SIZE = 40
    }
}

/** Watch progress along the bottom edge; drawn once, never animated. */
@SuppressLint("ViewConstructor")
class CardProgress(context: Context, private val progress: Float) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFFFFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Tokens.accent }
    private val barHeight = context.dpf(3.5f)
    private val margin = context.dpf(7f)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val r = barHeight / 2
        val bottom = getHeight() - margin
        rect.set(margin, bottom - barHeight, width - margin, bottom)
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

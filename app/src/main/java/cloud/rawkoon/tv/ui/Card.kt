package cloud.rawkoon.tv.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import cloud.rawkoon.tv.data.Images
import cloud.rawkoon.tv.jellyfin.Badge
import cloud.rawkoon.tv.jellyfin.MediaItem

/**
 * A poster or landscape card for one item, optionally captioned under the art (episodes, seasons,
 * TMDB titles). The art is decoded at its exact size with its corners already rounded, so drawing
 * it is a single blit; progress, the watched badge and a TMDB title's status badge are drawn once.
 */
@SuppressLint("ViewConstructor")
class Card(
    context: Context,
    item: MediaItem,
    val landscape: Boolean,
    private val images: Images,
    private val onFocused: (Card) -> Unit,
    private val onClick: (Card) -> Unit,
    caption: Pair<String, String?>? = null,
) : Focusable(context), RowItem {
    /** The item shown; [update] changes it when the page changes it before the server confirms. */
    var item: MediaItem = item
        private set
    override val key: String get() = item.id
    private val artW = context.dp(if (landscape) Tokens.LAND_W else Tokens.POSTER_W)
    private val artH = context.dp(if (landscape) Tokens.LAND_H else Tokens.POSTER_H)
    private val artRadius = context.dpf(Tokens.CARD_RADIUS)
    private val frame = PosterFrame(context, artW, artH, artRadius)
    override val motion = FocusMotion(frame, frame.ring, raise = false)

    @Volatile
    private var alive = false

    private var progressBar: CardProgress? = null
    private var watchedBadge: WatchedBadge? = null

    init {
        contentDescription = item.headerTitle
        val inset = context.dp(Tokens.RING_INSET)
        val captionH = if (caption != null) context.dp(Tokens.CAPTION_H) else 0
        val captionGap = if (caption != null) context.dp(Tokens.CAPTION_GAP) else 0
        layoutParams = LinearLayout.LayoutParams(frame.outerW, frame.outerH + captionGap + captionH)
        showMarks(item, animate = false)
        item.tmdb?.badge?.let {
            frame.addOverlay(badgeLabel(it, artW - context.dp(12)), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START,
            ).apply { setMargins(inset + context.dp(6), 0, 0, inset + context.dp(6)) })
        }
        addView(frame, frame.frameParams())
        if (caption != null) addView(CaptionColumn(context, caption.first, caption.second), LayoutParams(artW, captionH, Gravity.BOTTOM).apply { leftMargin = inset })
        listenForFocus { onFocused(this) }
        setOnClickListener { onClick(this) }
    }

    /**
     * Shows [updated], this card's item as the page just changed it (marked watched or not): the
     * check pops in or goes and the progress bar follows, without waiting for the server.
     */
    fun update(updated: MediaItem) {
        item = updated
        showMarks(updated, animate = true)
    }

    /** The progress bar and the watched check for [item]; a check that appears pops in when [animate]. */
    private fun showMarks(item: MediaItem, animate: Boolean) {
        val progress = item.progress?.takeIf { landscape || item.kind == MediaItem.Kind.EPISODE }
        if (progress != progressBar?.progress) {
            progressBar?.let(frame::removeView)
            progressBar = progress?.let { CardProgress(context, it).also(frame::addOverlay) }
        }
        // A rewatch keeps Played with a resume position: the bar says more than the badge
        val checked = item.played && progress == null
        if (checked == (watchedBadge != null)) return
        watchedBadge?.let(frame::removeView)
        watchedBadge = if (checked) WatchedBadge(context).also { badge -> frame.addOverlay(badge); if (animate) badge.popIn(delay = 0) } else null
    }

    /**
     * This card replaces [old], the same item's card before a refresh: progress made since grows
     * the bar from where [old] had it, and a check [old] did not have pops in.
     */
    fun changedFrom(old: Card) {
        val was = old.progressBar?.progress ?: 0f
        progressBar?.growFrom(was)
        if (old.watchedBadge == null) watchedBadge?.popIn()
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
        if (frame.art.bitmap != null) return
        val url = (if (landscape) item.thumbUrl else item.posterUrl ?: item.thumbUrl) ?: return
        // A TMDB image's URL is final; a library image is asked at the card's width
        images.load(if (item.tmdb != null) url else "$url&fillWidth=$artW", artW, artH, artRadius, opaque = false, isCurrent = { alive }) { frame.art.bitmap = it }
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

/**
 * Watch progress along the bottom edge; drawn once, except while [growFrom] runs: the bar then
 * redraws, but it is two small rectangles, for under a second.
 */
@SuppressLint("ViewConstructor")
class CardProgress(context: Context, val progress: Float) : View(context) {
    private var drawn = progress
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
        rect.right = margin + (width - margin * 2) * drawn
        canvas.drawRoundRect(rect, r, r, fill)
    }

    /** Grows the fill from [from] to the progress, a moment after the page settles; a bar that went back stays as it is. */
    fun growFrom(from: Float) {
        if (from >= progress) return
        drawn = from
        ValueAnimator.ofFloat(from, progress).apply {
            startDelay = Tokens.CHANGE_DELAY_MS
            duration = Tokens.GROW_MS
            interpolator = Tokens.ease
            addUpdateListener {
                drawn = it.animatedValue as Float
                invalidate()
            }
            start()
        }
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

    /** The view covers the art, the check sits in its corner: it grows from there when it pops in, not from the art's middle. */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        pivotX = w - margin - size / 2
        pivotY = margin + size / 2
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width - margin - size / 2
        val cy = margin + size / 2
        canvas.drawCircle(cx, cy, size / 2, disc)
        canvas.drawText(Icon.CHECK, cx, cy - (glyph.descent() + glyph.ascent()) / 2, glyph)
    }
}

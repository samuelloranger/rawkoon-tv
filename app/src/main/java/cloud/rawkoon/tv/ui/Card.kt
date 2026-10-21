package cloud.rawkoon.tv.ui

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
    val item: MediaItem,
    val landscape: Boolean,
    private val images: Images,
    private val onFocused: (Card) -> Unit,
    private val onClick: (Card) -> Unit,
    caption: Pair<String, String?>? = null,
) : Focusable(context), RowItem {
    override val key: String get() = item.id
    private val artW = context.dp(if (landscape) Tokens.LAND_W else Tokens.POSTER_W)
    private val artH = context.dp(if (landscape) Tokens.LAND_H else Tokens.POSTER_H)
    private val artRadius = context.dpf(Tokens.CARD_RADIUS)
    private val frame = PosterFrame(context, artW, artH, artRadius)
    override val motion = FocusMotion(frame, frame.ring, raise = false)

    @Volatile
    private var alive = false

    init {
        contentDescription = item.headerTitle
        val inset = context.dp(Tokens.RING_INSET)
        val captionH = if (caption != null) context.dp(Tokens.CAPTION_H) else 0
        layoutParams = LinearLayout.LayoutParams(frame.outerW, frame.outerH + captionH)
        val shownProgress = item.progress?.takeIf { landscape || item.kind == MediaItem.Kind.EPISODE }
        shownProgress?.let { frame.addOverlay(CardProgress(context, it)) }
        // A rewatch keeps Played with a resume position: the bar says more than the badge
        if (item.played && shownProgress == null) frame.addOverlay(WatchedBadge(context))
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

package cloud.rawkoon.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import cloud.rawkoon.tv.data.Images
import cloud.rawkoon.tv.jellyfin.MediaItem

/** Draws a logo at the bottom-left of a fixed box, so a new logo never changes any layout. */
class LogoView(context: Context) : View(context) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    var bitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val b = bitmap ?: return
        canvas.drawBitmap(b, 0f, (height - b.height).toFloat(), paint)
    }
}

/**
 * The top-left of the home screen: the focused item's logo (its title in Fraunces when there is
 * none), its meta line and overview. It switches only once focus has rested, fading the old one
 * out and the new one in with a short rise. Every child has a fixed size, so new text never
 * triggers a layout pass.
 */
@SuppressLint("ViewConstructor")
class Header(context: Context, private val images: Images) : LinearLayout(context) {
    private val main = Handler(Looper.getMainLooper())
    private val logoW = context.dp(LOGO_W)
    private val logoH = context.dp(LOGO_H)
    private val logo = LogoView(context)
    // Long titles shrink and wrap onto a second line instead of being cut
    private val title = label(context, 40f, Tokens.textPrimary, Fonts.title(context), lines = 2).apply {
        gravity = Gravity.BOTTOM
        setAutoSizeTextTypeUniformWithConfiguration(22, 40, 2, TypedValue.COMPLEX_UNIT_SP)
    }
    private val meta = label(context, 13.5f, Tokens.textSoft, Fonts.medium(context))
    private val overview = label(context, 14f, 0xD9E9E0D8.toInt(), Fonts.body(context), lines = 2).apply {
        setLineSpacing(0f, 1.2f)
    }
    private var shown: MediaItem? = null
    private var pending: MediaItem? = null

    @Volatile
    private var wantedLogo: String? = null
    private val settle = Runnable { pending?.let { switchTo(it) } }

    init {
        orientation = VERTICAL
        val box = FrameLayout(context)
        box.addView(logo, fill())
        box.addView(title, fill())
        addView(box, LayoutParams(logoW, logoH))
        addView(meta, LayoutParams(context.dp(560), context.dp(20)).apply { topMargin = context.dp(10) })
        addView(overview, LayoutParams(context.dp(490), context.dp(44)).apply { topMargin = context.dp(6) })
    }

    /** Called on every focus move; the header follows once focus rests. */
    fun show(item: MediaItem) {
        pending = item
        main.removeCallbacks(settle)
        if (shown == null) switchTo(item) else main.postDelayed(settle, Tokens.HEADER_SETTLE_MS)
    }

    private fun switchTo(item: MediaItem) {
        if (item == shown) return
        // The same item, refreshed (new progress): take the new data without animating
        if (item.id == shown?.id) {
            shown = item
            return
        }
        val first = shown == null
        shown = item
        animate().cancel()
        if (first) {
            fill(item)
            return
        }
        animate().alpha(0f).setDuration(Tokens.HEADER_OUT_MS).withEndAction {
            fill(item)
            translationY = context.dpf(4f)
            animate().alpha(1f).translationY(0f).setStartDelay(40).setDuration(Tokens.HEADER_IN_MS).setInterpolator(Tokens.ease).start()
        }.start()
    }

    private fun fill(item: MediaItem) {
        meta.text = item.metaLine()
        overview.text = item.overview
        val url = item.logoUrl
        wantedLogo = url
        logo.bitmap = null
        if (url == null) {
            title.text = item.headerTitle
            return
        }
        // The title stays empty while the logo loads, so the two never show together
        title.text = ""
        images.load(logoRequest(url), logoW, logoH, 0f, contain = true, isCurrent = { wantedLogo == url }) {
            logo.bitmap = it
        }
    }

    /** Loads [item]'s logo into the memory cache ahead of time, exactly as [fill] will ask for it. */
    fun prefetch(item: MediaItem) {
        val url = item.logoUrl ?: return
        images.load(logoRequest(url), logoW, logoH, 0f, contain = true, isCurrent = { true }) {}
    }

    private fun logoRequest(url: String) = "$url&maxWidth=$logoW"

    private companion object {
        const val LOGO_W = 360
        const val LOGO_H = 80
    }
}

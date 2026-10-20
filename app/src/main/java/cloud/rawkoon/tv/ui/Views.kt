package cloud.rawkoon.tv.ui

import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import cloud.rawkoon.tv.R

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

fun Context.dpf(value: Float): Float = value * resources.displayMetrics.density

/**
 * A plain view for backgrounds, scrims and focus rings. Nothing inside it overlaps, so
 * skipping the offscreen layer keeps alpha fades cheap.
 */
class FlatView(context: Context) : View(context) {
    override fun hasOverlappingRendering() = false
}

/** Layout params filling the parent, for the stacked layers of a [FrameLayout]. */
fun fill() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

/** The app's three families, loaded once. */
object Fonts {
    private var cache: Map<Int, Typeface> = emptyMap()

    private fun get(context: Context, res: Int): Typeface =
        cache[res] ?: context.resources.getFont(res).also { cache = cache + (res to it) }

    fun title(context: Context) = get(context, R.font.fraunces_semibold)

    fun body(context: Context) = get(context, R.font.hanken_regular)

    fun medium(context: Context) = get(context, R.font.hanken_medium)

    fun bold(context: Context) = get(context, R.font.hanken_semibold)

    fun icons(context: Context) = get(context, R.font.icons)
}

/** A single-line label, ellipsized; [size] in sp. */
fun label(context: Context, size: Float, color: Int, face: Typeface, lines: Int = 1) = TextView(context).apply {
    textSize = size
    setTextColor(color)
    typeface = face
    includeFontPadding = false
    maxLines = lines
    ellipsize = TextUtils.TruncateAt.END
}

/** A Font Awesome glyph, centered in its box. */
fun icon(context: Context, glyph: String, size: Float, color: Int) = TextView(context).apply {
    text = glyph
    textSize = size
    setTextColor(color)
    typeface = Fonts.icons(context)
    includeFontPadding = false
    gravity = Gravity.CENTER
}

fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
}

/** Font Awesome 6 solid code points. */
object Icon {
    const val SEARCH = "\uf002"
    const val HOME = "\uf015"
    const val HEART = "\uf004"
    const val DISCOVER = "\uf00e"
    const val FILM = "\uf008"
    const val TV = "\uf26c"
    const val FOLDER = "\uf07c"
    const val GEAR = "\uf013"
    const val PLAY = "\uf04b"
    const val PLUS = "\u002b"
    const val LOCK = "\uf023"
    const val PEN = "\uf304"
    const val KEY = "\uf084"
    const val BACK = "\uf060"
    const val NEXT = "\uf054"
    const val CHECK = "\uf00c"
    const val XMARK = "\uf00d"
    const val TRASH = "\uf1f8"
}

/** Text filled with a top-to-bottom gradient, like the "TV" of the brand. */
class GradientLabel(context: Context, private val top: Int, private val bottom: Int) : TextView(context) {
    init {
        includeFontPadding = false
        maxLines = 1
        // Opaque: the shader is drawn at the paint's alpha, and the theme's text colour is translucent
        setTextColor(0xFFFFFFFF.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
    }
}

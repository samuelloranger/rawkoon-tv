package cloud.rawkoon.tv.ui

import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.widget.TextView
import cloud.rawkoon.tv.R

/** The app's three families, loaded once. */
object Fonts {
    private var cache: Map<Int, Typeface> = emptyMap()

    private fun get(context: Context, res: Int): Typeface =
        cache[res] ?: context.resources.getFont(res).also { cache = cache + (res to it) }

    fun title(context: Context) = get(context, R.font.fraunces_semibold)

    fun body(context: Context) = get(context, R.font.hanken_regular)

    fun medium(context: Context) = get(context, R.font.hanken_medium)

    fun bold(context: Context) = get(context, R.font.hanken_semibold)

    /** The weight of the brand name in the app banner. */
    fun heavy(context: Context) = get(context, R.font.hanken_bold)

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
    const val PAUSE = "\uf04c"
    const val SUBTITLES = "\uf20a"
    const val AUDIO = "\uf028"
    const val REWIND = "\uf2ea"
    const val FORWARD = "\uf2f9"
    const val NEXT_TRACK = "\uf051"
    const val SKIP = "\uf04e"
}

/**
 * Text filled with a top-to-bottom gradient, like the "TV" of the brand. The gradient spans the
 * capitals, from their top to the baseline, so [top] and [bottom] are the colours the letters
 * actually start and end with.
 */
class GradientLabel(context: Context, private val top: Int, private val bottom: Int) : TextView(context) {
    init {
        includeFontPadding = false
        maxLines = 1
        // Opaque: the shader is drawn at the paint's alpha, and the theme's text colour is translucent
        setTextColor(0xFFFFFFFF.toInt())
    }

    private val caps = Rect()

    override fun onLayout(changed: Boolean, left: Int, t: Int, right: Int, b: Int) {
        super.onLayout(changed, left, t, right, b)
        paint.getTextBounds("T", 0, 1, caps)
        val y = baseline.toFloat()
        paint.shader = LinearGradient(0f, y + caps.top, 0f, y, top, bottom, Shader.TileMode.CLAMP)
    }
}

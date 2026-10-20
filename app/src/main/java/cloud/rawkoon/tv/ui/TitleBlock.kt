package cloud.rawkoon.tv.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.data.Images

/**
 * The top of a title's page: the poster, the logo (or the title), the meta chips, the tags and
 * the overview, with room under the poster for the page's own actions ([setActions]). Library
 * titles and TMDB titles share it; each page decides what goes in.
 */
class TitleBlock(context: Context, private val images: Images, private val isCurrent: () -> Boolean) : FrameLayout(context) {
    private val poster = ImageView(context)
    private val logo = TitleLogo(context, images, context.dp(LOGO_W), context.dp(LOGO_H), 34f, 20, isCurrent)
    private val info = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    init {
        noClip()
        poster.background = rounded(Tokens.surfaceHigh, context.dpf(POSTER_RADIUS))
        addView(poster, LayoutParams(context.dp(POSTER_W), context.dp(POSTER_H)).apply {
            leftMargin = context.dp(Tokens.CONTENT_START)
            topMargin = context.dp(TOP)
        })
        info.addView(logo, LinearLayout.LayoutParams(context.dp(LOGO_W), context.dp(LOGO_H)))
        addView(info, LayoutParams(context.dp(INFO_W), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(Tokens.CONTENT_START + POSTER_W + POSTER_GAP)
            topMargin = context.dp(TOP)
        })
    }

    /** Loads the poster; a library image is asked at the poster's width, a TMDB one is used as it is. */
    fun setPoster(url: String?, fillWidth: Boolean) {
        url ?: return
        val w = context.dp(POSTER_W)
        val h = context.dp(POSTER_H)
        images.load(if (fillWidth) "$url&fillWidth=$w" else url, w, h, context.dpf(POSTER_RADIUS), isCurrent = isCurrent) { poster.setImageBitmap(it) }
    }

    /** Puts [actions] under the poster. */
    fun setActions(actions: View) {
        addView(actions, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(Tokens.CONTENT_START - Tokens.RING_INSET)
            topMargin = context.dp(TOP + POSTER_H + ACTIONS_GAP)
        })
    }

    /** Fills the info column: the logo or [name], the [meta] chips, the [tags] (when any) and the [overview]. */
    fun bind(name: String, logoUrl: String?, meta: List<Chip>, tags: List<Chip>, overview: String) {
        logo.bind(logoUrl, name)
        while (info.childCount > 1) info.removeViewAt(1)
        info.addView(chipRow(context, meta), wrapRow(10))
        if (tags.isNotEmpty()) info.addView(chipRow(context, tags, 12.5f), wrapRow(8))
        info.addView(label(context, 14f, Tokens.textBody, Fonts.body(context), lines = 3).apply {
            text = overview
            setLineSpacing(0f, 1.2f)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(10) })
    }

    private fun wrapRow(top: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(top) }

    companion object {
        private const val TOP = 50
        private const val POSTER_W = 135
        private const val POSTER_H = 202
        private const val POSTER_RADIUS = 8f
        private const val POSTER_GAP = 25
        private const val LOGO_W = 330
        private const val LOGO_H = 66
        private const val INFO_W = 540
        private const val ACTIONS_GAP = 14

        /** Room under the poster for the action row. */
        private const val ACTIONS_H = 56

        /** The block's height, poster and actions included: what a page gives it in its column. */
        fun params(context: Context) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(TOP + POSTER_H + ACTIONS_GAP + ACTIONS_H))
    }
}

/** The "MOVIE" or "SHOW" chip. */
fun kindChip(context: Context, show: Boolean) = Chip.Kind(context.getString(if (show) R.string.kind_show else R.string.kind_movie))

/** A year, or for a show its span: "2019 –" while it continues, "2019 – 2023" once it ended in another year. */
fun yearChip(year: Int, show: Boolean, continuing: Boolean, endYear: Int?) = Chip.Text(
    when {
        !show -> year.toString()
        continuing -> "$year –"
        endYear != null && endYear != year -> "$year – $endYear"
        else -> year.toString()
    },
)

/** "3 seasons" and "24 episodes", for whichever of the two is known. */
fun showCountChips(context: Context, seasons: Int?, episodes: Int?): List<Chip> = listOfNotNull(
    seasons?.let { Chip.Text(context.resources.getQuantityString(R.plurals.seasons_count, it, it)) },
    episodes?.let { Chip.Text(context.resources.getQuantityString(R.plurals.episodes_count, it, it)) },
)

package cloud.rawkoon.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.data.Images

/**
 * The Wholphin-style drawer: a column of icons always on screen, which opens with its labels when
 * focus enters it. The rail is always laid out at its open width; opening only scales its
 * background and fades the labels in, so it costs no layout.
 */
@SuppressLint("ViewConstructor")
class NavRail(
    context: Context,
    profileName: String,
    avatarUrl: String?,
    images: Images,
    private val active: Destination,
    private val onSelect: (Destination) -> Unit,
) : FrameLayout(context) {
    private val scrim = FlatView(context).apply {
        setBackgroundColor(Tokens.rail)
        pivotX = 0f
    }
    private val items = LinkedHashMap<Destination, RailItem>()

    /** The entries in order, for Up and Down; built on first use, once all are added. */
    private val order by lazy { items.values.toList() }
    private val fades = mutableListOf<View>()
    private val closedScale = Tokens.RAIL_W.toFloat() / Tokens.RAIL_OPEN_W
    var isOpen = false
        private set

    /** Where Right (and Back) sends focus from the rail. */
    var onExit: (() -> View?)? = null

    /** Told when the rail opens or closes, to dim the page behind. */
    var onOpenChanged: ((Boolean) -> Unit)? = null

    init {
        clipChildren = false
        addView(scrim, LayoutParams(context.dp(Tokens.RAIL_OPEN_W), LayoutParams.MATCH_PARENT))
        scrim.scaleX = closedScale

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(10), context.dp(18), 0, context.dp(18))
            clipChildren = false
        }
        column.addView(logoRow(), LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, context.dp(32)).apply { bottomMargin = context.dp(11) })
        for (destination in Destination.entries) {
            if (destination == Destination.SETTINGS) column.addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
            val item = if (destination == Destination.PROFILE) {
                RailItem(context, destination, profileName, context.getString(R.string.switch_profile), avatarUrl, images, tall = true)
            } else {
                RailItem(context, destination, context.getString(destination.label), null, null, images, tall = false)
            }
            item.setActive(destination == active)
            item.setOnClickListener { onSelect(destination) }
            items[destination] = item
            fades += item.labels
            column.addView(item, LinearLayout.LayoutParams(context.dp(190), context.dp(if (item.tall) 46 else 36)).apply {
                bottomMargin = context.dp(if (destination == Destination.PROFILE) 13 else 3)
            })
        }
        addView(column, LayoutParams(context.dp(Tokens.RAIL_OPEN_W), LayoutParams.MATCH_PARENT))
        fades.forEach { it.alpha = 0f }

        whileAttachedOnFocusChange { now -> setOpen(now != null && isInside(now)) }
    }

    /** The brand as on the TV's home banner: the reel, then "Rawkoon" with "TV" in the apricot gradient. */
    private fun logoRow() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val reel = ImageView(context).apply { setImageResource(R.drawable.logo_reel) }
        val size = context.dp(28)
        addView(reel, LinearLayout.LayoutParams(size, size).apply { marginStart = (context.dp(40) - size) / 2; marginEnd = (context.dp(40) - size) / 2 })
        val name = FlatLinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(label(context, 18f, Tokens.textPrimary, Fonts.bold(context)).apply { text = context.getString(R.string.brand_name) })
            addView(GradientLabel(context, BRAND_TOP, BRAND_BOTTOM).apply {
                text = context.getString(R.string.brand_tv)
                textSize = 18f
                typeface = Fonts.bold(context)
            }, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(5) })
        }
        fades += name
        addView(name, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(12) })
    }

    private companion object {
        // Sampled from the "TV" of the app banner
        const val BRAND_TOP = 0xFFEEB27A.toInt()
        const val BRAND_BOTTOM = 0xFFDF8F5C.toInt()
    }

    /** The entry for the current page: where focus lands when it enters the rail. */
    fun activeItem(): View? = items[active]

    private fun setOpen(open: Boolean) {
        if (open == isOpen) return
        isOpen = open
        scrim.animate().scaleX(if (open) 1f else closedScale).setDuration(Tokens.DRAWER_MS).setInterpolator(Tokens.ease).start()
        for (view in fades) {
            view.animate().alpha(if (open) 1f else 0f).setStartDelay(if (open) 60 else 0)
                .setDuration(if (open) 160 else 90).setInterpolator(Tokens.ease).start()
        }
        items.values.forEach { it.setOpen(open) }
        onOpenChanged?.invoke(open)
    }

    /**
     * Coming from the page, only Left enters the rail, and only on the current destination, as in
     * Wholphin. The rail is laid out at its open width even when closed, so without this its
     * entries would compete with the page's own views for up and down moves.
     */
    override fun addFocusables(views: ArrayList<View>, direction: Int, focusableMode: Int) {
        if (!hasFocus()) {
            val target = items[active]
            if (direction == FOCUS_LEFT && target != null) views.add(target)
            return
        }
        super.addFocusables(views, direction, focusableMode)
    }

    override fun focusSearch(focused: View?, direction: Int): View? {
        if (direction == View.FOCUS_RIGHT) return onExit?.invoke() ?: focused
        // Up and down walk the entries in order: geometric search can't jump the gap above Settings
        if (direction == View.FOCUS_UP || direction == View.FOCUS_DOWN) {
            val i = order.indexOf(focused)
            if (i >= 0) return order.getOrNull(if (direction == View.FOCUS_DOWN) i + 1 else i - 1) ?: focused
        }
        val next = super.focusSearch(focused, direction)
        if (next == null || !isInside(next)) return focused
        return next
    }

    private fun isInside(view: View): Boolean {
        var parent = view.parent
        while (parent != null) {
            if (parent === this) return true
            parent = parent.parent
        }
        return false
    }
}

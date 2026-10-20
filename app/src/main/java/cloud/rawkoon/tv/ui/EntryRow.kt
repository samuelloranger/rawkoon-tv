package cloud.rawkoon.tv.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout

/**
 * A row of buttons or tabs where focus arriving from above or below lands on one chosen view
 * ([entry]) rather than the nearest child. Sideways moves inside the row are untouched.
 */
open class EntryRow(context: Context) : LinearLayout(context) {
    /** The view that takes focus coming from above or below; null leaves the choice to the focus search. */
    protected open fun entry(): View? = null

    override fun addFocusables(views: ArrayList<View>, direction: Int, focusableMode: Int) {
        val target = entry()
        if (!hasFocus() && target != null && (direction == FOCUS_UP || direction == FOCUS_DOWN)) {
            views.add(target)
            return
        }
        super.addFocusables(views, direction, focusableMode)
    }
}

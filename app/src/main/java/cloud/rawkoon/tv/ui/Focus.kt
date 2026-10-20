package cloud.rawkoon.tv.ui

import android.content.Context
import android.view.View
import android.view.ViewTreeObserver

/** True when [this] sits somewhere under [ancestor]. */
fun View.isWithin(ancestor: View): Boolean {
    var parent = parent
    while (parent != null) {
        if (parent === ancestor) return true
        parent = parent.parent
    }
    return false
}

/** Anything a [FocusRow] holds: cards, cast, seasons. The key brings focus back to the same thing after a reload. */
interface RowItem {
    val key: String
}

/**
 * Listens to focus moves anywhere in the window while this view is attached. The window's
 * observer outlives screens, so the listener is removed on detach instead of leaking with it.
 */
fun View.whileAttachedOnFocusChange(listener: (View?) -> Unit) {
    val global = ViewTreeObserver.OnGlobalFocusChangeListener { _, now -> listener(now) }
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = v.viewTreeObserver.addOnGlobalFocusChangeListener(global)
        override fun onViewDetachedFromWindow(v: View) = v.viewTreeObserver.removeOnGlobalFocusChangeListener(global)
    })
}

/**
 * Holds focus while a container swaps its children. Removing the focused view makes Android
 * hand focus to the first focusable it finds (and every view added after that may take it), so
 * a rebuild parks focus here, then puts it back on the right card. Never reachable with the D-pad.
 */
class FocusParking(context: Context) : View(context) {
    init {
        isFocusable = false
    }

    /** Runs [rebuild] with focus parked if [parked] (the container had focus), then lets [restore] place it. */
    fun around(parked: Boolean, rebuild: () -> Unit, restore: () -> Unit) {
        if (parked) {
            isFocusable = true
            requestFocus()
        }
        rebuild()
        if (parked) {
            restore()
            isFocusable = false
        }
    }
}

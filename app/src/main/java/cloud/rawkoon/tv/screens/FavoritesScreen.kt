package cloud.rawkoon.tv.screens

import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.favorites
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.Tokens
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The profile's favorites as rows, like home: films, shows, episodes, collections. It reloads
 * whenever it comes back to the top, since a title is usually unfavorited on its own page, and
 * the rows are updated in place so focus stays where it was.
 */
class FavoritesScreen(activity: MainActivity, session: JellyfinSession) :
    RowsPage(activity, session, Destination.FAVORITES, headerTop = 26, rowsTop = Tokens.ROWS_TOP - 6) {
    init {
        load()
    }

    private var loading: Job? = null

    private fun load() {
        hideMessage()
        loading?.cancel()
        loading = scope.launch {
            try {
                val list = session.favorites { groupTitle(it) }
                if (list.isEmpty()) {
                    clear()
                    showMessage(string(R.string.favorites_empty))
                    // The rows that held focus are gone and the message has nothing to focus
                    if (view.findFocus() == null) rail.activeItem()?.requestFocus()
                } else {
                    show(list)
                }
            } catch (e: Exception) {
                fail(e, R.string.favorites_error, retry = ::load, takeFocus = true)
            }
        }
    }

    /** With no rows yet, [show] puts focus on the first card as soon as they arrive. */
    override fun focusDefault() {
        firstFocus()?.requestFocus()
    }

    /** Coming back from a title's page: refresh in place, keeping focus. */
    override fun onShown(first: Boolean) {
        super.onShown(first)
        if (!first) load()
    }
}

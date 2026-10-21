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
    RowsPage(activity, session, Destination.FAVORITES, headerTop = Tokens.PAGE_HEADER_TOP, rowsTop = Tokens.PAGE_ROWS_TOP) {
    // Declared before init: a property set after load() starts would reset the running job to null
    private var loading: Job? = null

    init {
        load()
    }

    private fun load() {
        loading?.cancel()
        loading = scope.launch {
            try {
                app.awaitStop()
                val found = session.favorites()
                if (found.isEmpty()) showEmpty(string(R.string.favorites_empty)) else show(rowsFor(found))
            } catch (e: Exception) {
                fail(e, R.string.favorites_error, retry = ::load, takeFocus = true, silentIfRows = true)
            }
        }
    }

    /** With no rows yet, [show] puts focus on the first card as soon as they arrive. */
    override val isRailPage get() = true

    override fun focusDefault() {
        firstFocus()?.requestFocus()
    }

    /** Coming back from a title's page: refresh in place, keeping focus. */
    override fun onShown(first: Boolean) {
        super.onShown(first)
        if (!first) load()
    }
}

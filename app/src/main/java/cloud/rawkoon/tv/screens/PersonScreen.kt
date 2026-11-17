package cloud.rawkoon.tv.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.Person
import cloud.rawkoon.tv.jellyfin.personTitles
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.label
import kotlinx.coroutines.launch

/**
 * A cast member's films and shows in the library, as rows under their name. [byId] when [person]
 * is a Jellyfin person; a cast member from TMDB (on a Discover page) is matched by name instead.
 */
class PersonScreen(activity: MainActivity, session: JellyfinSession, private val person: Person, private val byId: Boolean, active: Destination) :
    RowsPage(
        activity, session, active,
        headerTop = Tokens.PAGE_HEADER_TOP + Tokens.SEARCH_BAR_OFFSET,
        rowsTop = Tokens.PAGE_ROWS_TOP + Tokens.SEARCH_BAR_OFFSET,
    ) {
    init {
        content.addView(label(context, 32f, Tokens.textPrimary, Fonts.title(context)).apply { text = person.name }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(Tokens.CONTENT_START)
            topMargin = context.dp(NAME_TOP)
        })
        load()
    }

    private fun load() {
        scope.launch {
            try {
                val found = session.personTitles(person.id.takeIf { byId }, person.name)
                if (found.isEmpty()) showEmpty(string(R.string.person_empty, person.name)) else show(rowsFor(found))
            } catch (e: Exception) {
                fail(e, R.string.person_error, retry = ::load, takeFocus = true)
            }
        }
    }

    override fun focusDefault() {
        firstFocus()?.requestFocus()
    }

    private companion object {
        const val NAME_TOP = 30
    }
}

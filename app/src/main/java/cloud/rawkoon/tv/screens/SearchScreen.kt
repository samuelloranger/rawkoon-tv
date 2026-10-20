package cloud.rawkoon.tv.screens

import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.Find
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.search
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.SearchField
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Search across the library: a field on top, the results as rows under it. A search starts once
 * typing pauses and replaces the one still running, so only the latest term ever lands. The
 * results keep the previous ones on screen until they arrive, so typing never flashes the page;
 * a failed search gives way to its error instead of leaving stale results up.
 */
class SearchScreen private constructor(activity: MainActivity, session: JellyfinSession, fieldId: Int) :
    RowsPage(
        activity, session, Destination.SEARCH,
        headerTop = Tokens.PAGE_HEADER_TOP + Tokens.SEARCH_BAR_OFFSET,
        rowsTop = Tokens.PAGE_ROWS_TOP + Tokens.SEARCH_BAR_OFFSET,
        aboveId = fieldId,
    ) {
    constructor(activity: MainActivity, session: JellyfinSession) : this(activity, session, View.generateViewId())

    private val field = SearchField(
        context,
        hint = string(R.string.search_field_hint),
        onChanged = ::onTyped,
        // Search on the keyboard searches now (no debounce) and goes to the results
        onSearch = ::onSearchAction,
    ).apply { id = fieldId }
    private var searching: Job? = null

    /** The term the rows on screen answer, so a keystroke that doesn't change it starts nothing. */
    private var shownTerm: String? = null

    init {
        content.addView(field, FrameLayout.LayoutParams(context.dp(FIELD_W), context.dp(FIELD_H)).apply {
            leftMargin = context.dp(Tokens.CONTENT_START)
            topMargin = context.dp(FIELD_TOP)
        })
        // Down from the field goes to the results
        field.setOnKeyListener { _, keyCode, event ->
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN && firstFocus()?.requestFocus() == true
        }
        showMessage(string(R.string.search_hint))
    }

    private fun onTyped(typed: CharSequence) {
        val term = Find.term(typed)
        searching?.cancel()
        // Too short first: after a failed search shownTerm is null, and the error must give way to the hint
        if (term == null) {
            shownTerm = null
            showEmpty(string(R.string.search_hint))
            return
        }
        if (term != shownTerm) startSearch(term, debounce = true, focusResults = false)
    }

    private fun onSearchAction() {
        val term = Find.term(field.text)
        if (term != null && term != shownTerm) startSearch(term, debounce = false, focusResults = true)
        else firstFocus()?.requestFocus()
    }

    /** Searches for [term], dropping the search still running so an older term can never land last. */
    private fun startSearch(term: String, debounce: Boolean, focusResults: Boolean) {
        searching?.cancel()
        searching = scope.launch {
            if (debounce) delay(DEBOUNCE_MS)
            try {
                val found = session.search(term)
                shownTerm = term
                if (found.isEmpty()) {
                    showEmpty(string(R.string.search_empty, term))
                } else {
                    show(rowsFor(found))
                    if (focusResults) firstFocus()?.requestFocus()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // The rows on screen answer an older term: retyping this one must search again
                shownTerm = null
                fail(e, R.string.search_error, retry = { startSearch(term, debounce = false, focusResults = false) }, takeFocus = false)
            }
        }
    }

    override fun contentFocus(): View? = super.contentFocus() ?: field

    override fun focusDefault() {
        field.requestFocus()
    }

    private companion object {
        /** How long typing must pause before a search starts. */
        const val DEBOUNCE_MS = 350L
        const val FIELD_TOP = 30
        const val FIELD_W = 420
        const val FIELD_H = 44
    }
}

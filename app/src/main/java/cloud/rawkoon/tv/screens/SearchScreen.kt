package cloud.rawkoon.tv.screens

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.Find
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.search
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.dpf
import cloud.rawkoon.tv.ui.rounded
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Search across the library: a field on top, the results as rows under it. A search starts once
 * typing pauses and replaces the one still running, so only the latest term ever lands. The
 * results keep the previous ones on screen until they arrive, so typing never flashes the page.
 */
class SearchScreen(activity: MainActivity, session: JellyfinSession) :
    RowsPage(activity, session, Destination.SEARCH, headerTop = HEADER_TOP, rowsTop = ROWS_TOP) {
    private val field = EditText(context).apply {
        id = View.generateViewId()
        hint = string(R.string.search_field_hint)
        // No suggestions: autocorrect underlines and a suggestion strip get in the way on a TV
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        textSize = 16f
        typeface = Fonts.medium(context)
        setTextColor(Tokens.textPrimary)
        setHintTextColor(Tokens.textMuted)
        isSingleLine = true
        val idle = rounded(Tokens.surfaceHigh, context.dpf(10f))
        val focusedLook = rounded(Tokens.surfaceFocused, context.dpf(10f)).apply { setStroke(context.dp(2), Tokens.ring) }
        background = idle
        setPadding(context.dp(16), 0, context.dp(16), 0)
        setOnFocusChangeListener { v, focused -> v.background = if (focused) focusedLook else idle }
    }
    private var searching: Job? = null

    /** The term the rows on screen answer, so a keystroke that doesn't change it starts nothing. */
    private var shownTerm: String? = null

    init {
        aboveId = field.id
        content.addView(field, FrameLayout.LayoutParams(context.dp(FIELD_W), context.dp(FIELD_H)).apply {
            leftMargin = context.dp(Tokens.CONTENT_START)
            topMargin = context.dp(FIELD_TOP)
        })
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable) = onTyped(s)
        })
        // Search on the keyboard searches now (no debounce) and goes to the results; Down goes there too
        field.setOnEditorActionListener { _, _, _ ->
            context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(field.windowToken, 0)
            val term = Find.term(field.text)
            if (term != null && term != shownTerm) {
                searching?.cancel()
                searching = scope.launch { runSearch(term, focusResults = true) }
            } else {
                firstFocus()?.requestFocus()
            }
            true
        }
        field.setOnKeyListener { _, keyCode, event ->
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN && firstFocus()?.requestFocus() == true
        }
        showMessage(string(R.string.search_hint))
    }

    /** Starts the search for what [typed] holds once typing pauses, dropping the one still running. */
    private fun onTyped(typed: CharSequence) {
        val term = Find.term(typed)
        searching?.cancel()
        // Too short first: after a failed search shownTerm is still null, and the error must give way to the hint
        if (term == null) {
            shownTerm = null
            clear()
            showMessage(string(R.string.search_hint))
            return
        }
        if (term == shownTerm) return
        searching = scope.launch {
            delay(Find.DEBOUNCE_MS)
            runSearch(term)
        }
    }

    private suspend fun runSearch(term: String, focusResults: Boolean = false) {
        try {
            val list = session.search(term) { groupTitle(it) }
            shownTerm = term
            if (list.isEmpty()) {
                clear()
                showMessage(string(R.string.search_empty, term))
            } else {
                show(list)
                if (focusResults) firstFocus()?.requestFocus()
            }
        } catch (e: Exception) {
            // Retrying replaces whatever search is running, so an older term can never land last
            fail(e, R.string.search_error, retry = {
                searching?.cancel()
                shownTerm = null
                searching = scope.launch { runSearch(term) }
            }, takeFocus = false)
        }
    }

    override fun contentFocus(): View? = super.contentFocus() ?: field

    override fun focusDefault() {
        field.requestFocus()
    }

    private companion object {
        const val FIELD_TOP = 30
        const val FIELD_W = 420
        const val FIELD_H = 44

        /** The header sits under the field; the rows keep the gap home leaves between the two. */
        const val HEADER_TOP = 84
        const val ROWS_TOP = 287
    }
}

package cloud.rawkoon.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * A single-line text field for searching: it lights up with the focus ring like the other
 * controls and asks for no suggestions. [onChanged] gets every edit; [onSearch] fires on the
 * keyboard's search action, after the keyboard is hidden.
 */
@SuppressLint("ViewConstructor")
class SearchField(
    context: Context,
    hint: String,
    onChanged: (CharSequence) -> Unit,
    onSearch: () -> Unit,
) : EditText(context) {
    init {
        this.hint = hint
        // No suggestions: autocorrect underlines and a suggestion strip get in the way on a TV
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        textSize = TEXT_SP
        typeface = Fonts.medium(context)
        setTextColor(Tokens.textPrimary)
        setHintTextColor(Tokens.textMuted)
        isSingleLine = true
        val idle = rounded(Tokens.surfaceHigh, context.dpf(RADIUS))
        val focusedLook = rounded(Tokens.surfaceFocused, context.dpf(RADIUS)).apply { setStroke(context.dp(RING_W), Tokens.ring) }
        background = idle
        setPadding(context.dp(PAD_H), 0, context.dp(PAD_H), 0)
        setOnFocusChangeListener { v, focused -> v.background = if (focused) focusedLook else idle }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable) = onChanged(s)
        })
        setOnEditorActionListener { _, _, _ ->
            context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(windowToken, 0)
            onSearch()
            true
        }
    }

    companion object {
        /** How long typing must pause before a search starts. */
        const val DEBOUNCE_MS = 350L

        private const val TEXT_SP = 16f
        private const val RADIUS = 10f
        private const val RING_W = 2
        private const val PAD_H = 16
    }
}

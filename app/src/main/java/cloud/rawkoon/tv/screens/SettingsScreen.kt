package cloud.rawkoon.tv.screens

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import cloud.rawkoon.tv.BuildConfig
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.Screen
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.ui.ChoiceDialog
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.PillButton
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.label
import cloud.rawkoon.tv.ui.noClip

/** App settings. For now: the language, and the version. */
class SettingsScreen(activity: MainActivity) : Screen(activity) {
    override val view = FrameLayout(context)
    private val language = PillButton(context, languageLabel())
    private var dialog: ChoiceDialog? = null

    init {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(Tokens.CONTENT_START), context.dp(40), context.dp(Tokens.OVERSCAN_H), 0)
        }.noClip()
        column.addView(label(context, 34f, Tokens.textPrimary, Fonts.title(context)).apply { text = string(R.string.nav_settings) })
        column.addView(label(context, 13f, Tokens.textMuted, Fonts.bold(context)).apply { text = string(R.string.language).uppercase() }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = context.dp(28); bottomMargin = context.dp(4) })
        column.addView(language, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(label(context, 13f, Tokens.textMuted, Fonts.medium(context)).apply {
            text = string(R.string.version, BuildConfig.VERSION_NAME)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(28) })
        view.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP))
        view.setBackgroundColor(Tokens.background)
        language.setOnClickListener { chooseLanguage() }
    }

    private fun languageLabel() = when (Prefs.language(context)) {
        "fr" -> string(R.string.language_fr)
        "en" -> string(R.string.language_en)
        else -> string(R.string.language_system)
    }

    private fun chooseLanguage() {
        val choices = listOf("system" to R.string.language_system, "fr" to R.string.language_fr, "en" to R.string.language_en).map { (code, label) ->
            string(label) to {
                if (code != Prefs.language(context)) {
                    Prefs.of(context).edit().putString(Prefs.LANGUAGE, code).apply()
                    // Every screen is rebuilt in the new language, starting from "Who's watching?"
                    activity.recreate()
                }
            }
        }
        dialog = ChoiceDialog(context, string(R.string.language), choices).also { it.open(view) }
    }

    override fun focusDefault() {
        language.requestFocus()
    }

    override fun onBack(): Boolean {
        dialog?.takeIf { it.isAttachedToWindow }?.let {
            it.close()
            return true
        }
        return false
    }
}

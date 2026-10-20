package cloud.rawkoon.tv.screens

import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import cloud.rawkoon.tv.BuildConfig
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.Screen
import cloud.rawkoon.tv.data.Fetcher
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.ui.ChoiceDialog
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.PillButton
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.label
import cloud.rawkoon.tv.ui.noClip
import cloud.rawkoon.tv.update.InstallState
import cloud.rawkoon.tv.update.Installer
import cloud.rawkoon.tv.update.UpdateCheck
import kotlinx.coroutines.isActive

/** App settings: the language, updates, and the version. */
class SettingsScreen(activity: MainActivity) : Screen(activity) {
    override val view = FrameLayout(context)
    private val language = PillButton(context, languageLabel())
    private var dialog: ChoiceDialog? = null
    private val updateCheck = UpdateCheck(context, Fetcher(context))
    private val installer = Installer(context, Fetcher(context))
    private val updateStatus = label(context, 13f, Tokens.textMuted, Fonts.medium(context))
    private val checkNow = PillButton(context, string(R.string.update_check_now))
    private val install = PillButton(context, "", primary = true)

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
        column.addView(label(context, 13f, Tokens.textMuted, Fonts.bold(context)).apply { text = string(R.string.update_title).uppercase() }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = context.dp(28); bottomMargin = context.dp(4) })
        column.addView(updateStatus, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = context.dp(4) })
        if (updateCheck.enabled) {
            val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }.noClip()
            buttons.addView(checkNow)
            buttons.addView(install)
            column.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            checkNow.setOnClickListener { checkForUpdate() }
            install.setOnClickListener { installUpdate() }
        }
        showUpdate()
        column.addView(label(context, 13f, Tokens.textMuted, Fonts.medium(context)).apply {
            text = string(R.string.version, BuildConfig.VERSION_NAME)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(28) })
        view.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP))
        view.setBackgroundColor(Tokens.background)
        language.setOnClickListener { chooseLanguage() }
    }

    /** What the last check found, and the install button when a newer release is waiting. */
    private fun showUpdate() {
        val pending = updateCheck.pending()
        val state = Installer.state
        updateStatus.text = when {
            !updateCheck.enabled -> string(R.string.update_disabled)
            state is InstallState.Downloading -> string(R.string.update_downloading, state.percent)
            state is InstallState.Ready -> string(R.string.update_confirm)
            state is InstallState.Failed -> string(state.messageRes)
            pending != null -> string(R.string.update_available, pending.version)
            updateCheck.checkedAt == 0L -> string(R.string.update_never_checked)
            updateCheck.failed -> string(R.string.update_check_failed, checkedAtText())
            else -> string(R.string.update_up_to_date, checkedAtText())
        }
        install.visibility = if (pending != null && !Installer.running && state !is InstallState.Ready) View.VISIBLE else View.GONE
        pending?.let { install.label.text = string(R.string.update_install, it.version) }
    }

    /** Back from the installer's confirmation (declined or not), the status says what is true now. */
    override fun onResume() {
        Installer.acknowledge()
        showUpdate()
    }

    override fun onShown(first: Boolean) {
        super.onShown(first)
        Installer.listen(this) { state ->
            if (state is InstallState.Failed) Toast.makeText(context, state.messageRes, Toast.LENGTH_LONG).show()
            showUpdate()
        }
        showUpdate()
    }

    override fun destroy() {
        Installer.unlisten(this)
        super.destroy()
    }

    private fun checkedAtText() = DateFormat.getMediumDateFormat(context).format(updateCheck.checkedAt)

    private fun checkForUpdate() {
        updateStatus.text = string(R.string.update_checking)
        updateCheck.refreshAsync(force = true) { if (scope.isActive) showUpdate() }
    }

    private fun installUpdate() {
        val release = updateCheck.pending() ?: return
        install.visibility = View.GONE
        // Focus would otherwise fall to whatever Android finds once the button is gone.
        checkNow.requestFocus()
        installer.install(release)
        showUpdate()
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

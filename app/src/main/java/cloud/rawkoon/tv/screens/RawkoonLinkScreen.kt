package cloud.rawkoon.tv.screens

import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.Toast
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.rawkoon.RawkoonClient
import cloud.rawkoon.tv.ui.PillButton
import cloud.rawkoon.tv.ui.dp

/**
 * Links a profile that has no Rawkoon session (one imported from the first RawkoonTV, or whose
 * session ended) to its Rawkoon account, with the same device code as adding a profile. The token
 * goes onto the existing profile: its Jellyfin sign-in is already good, so nothing else is redone.
 * Goes back to the page that asked once linked, which then reloads.
 */
class RawkoonLinkScreen(activity: MainActivity, private val profileId: String) :
    DeviceCodeScreen(activity, activity.getString(R.string.rk_connect_title), activity.getString(R.string.sign_in_hint)) {
    private var rawkoonUrl: String? = app.profiles.get(profileId)?.let { it.rawkoonUrl ?: Prefs.rawkoonUrl(context) }
    private val server = field("https://", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
    private val serverNext = PillButton(context, string(R.string.continue_), primary = true)

    init {
        panel.visibility = View.GONE
        column.addView(panel)
        column.addView(server, LinearLayout.LayoutParams(context.dp(380), context.dp(44)))
        column.addView(serverNext, wrap().apply { topMargin = context.dp(14) })
        column.addView(status, LinearLayout.LayoutParams(context.dp(520), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(18) })
        column.addView(retry, wrap().apply { topMargin = context.dp(8) })
        retry.setOnClickListener { start() }
        server.setText("https://")
        server.imeOptions = EditorInfo.IME_ACTION_GO
        server.setOnEditorActionListener { _, _, _ ->
            submitServer()
            true
        }
        serverNext.setOnClickListener { submitServer() }

        val cancel = PillButton(context, string(R.string.cancel))
        cancel.setOnClickListener { activity.pop() }
        footer.addView(cancel, wrap())

        // Without a server address, ask for it first (a profile imported without one, on a TV that never signed in here)
        server.visibility = if (rawkoonUrl == null) View.VISIBLE else View.GONE
        serverNext.visibility = server.visibility
        if (rawkoonUrl != null) start()
    }

    override fun focusDefault() {
        when {
            rawkoonUrl == null -> server.requestFocus()
            retry.visibility == View.VISIBLE -> retry.requestFocus()
            else -> footer.getChildAt(0)?.requestFocus()
        }
    }

    private fun submitServer() {
        val url = server.text.toString().trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://") || url.length < 12) {
            status.text = string(R.string.error_invalid_url)
            return
        }
        Prefs.setRawkoonUrl(context, url)
        rawkoonUrl = url
        server.visibility = View.GONE
        serverNext.visibility = View.GONE
        status.text = ""
        start()
        footer.getChildAt(0)?.requestFocus()
    }

    /** Asks for a code and links the profile once it is approved. */
    private fun start() {
        val url = rawkoonUrl ?: return
        val client = RawkoonClient(url)
        runFlow(client) { token -> link(client, url, token) }
    }

    /** Puts the session on the existing profile, read again in case it changed while the code was out. */
    private suspend fun link(client: RawkoonClient, url: String, token: String) {
        val userId = client.bootstrap(token).userId.ifBlank { null }
        val profile = app.profiles.get(profileId) ?: return activity.pop()
        app.profiles.save(profile.copy(rawkoonUrl = url, rawkoonToken = token, rawkoonUserId = userId))
        Toast.makeText(context, R.string.rk_linked, Toast.LENGTH_SHORT).show()
        activity.pop()
    }
}

package cloud.rawkoon.tv.screens

import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.Screen
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.data.Profile
import cloud.rawkoon.tv.rawkoon.OnboardingStep
import cloud.rawkoon.tv.rawkoon.RawkoonClient
import cloud.rawkoon.tv.rawkoon.onboard
import cloud.rawkoon.tv.rawkoon.signInMessage
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.Icon
import cloud.rawkoon.tv.ui.PillButton
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.dpf
import cloud.rawkoon.tv.ui.label
import cloud.rawkoon.tv.ui.noClip
import cloud.rawkoon.tv.ui.rounded
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A centered column: a Fraunces title and a line under it, then whatever the screen adds. */
abstract class FormScreen(activity: MainActivity, title: String, hint: String) : Screen(activity) {
    override val view = FrameLayout(context)
    protected val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }.noClip()
    protected val footer = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }.noClip()
    protected val status = label(context, 14f, Tokens.accent, Fonts.medium(context), lines = 2).apply { gravity = Gravity.CENTER }

    init {
        column.addView(label(context, 34f, Tokens.textPrimary, Fonts.title(context)).apply { text = title }, wrap().apply { bottomMargin = context.dp(8) })
        column.addView(label(context, 15f, Tokens.textSoft, Fonts.medium(context)).apply { text = hint }, wrap().apply { bottomMargin = context.dp(26) })
        view.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        view.addView(footer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            bottomMargin = context.dp(30)
        })
    }

    protected fun wrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    protected fun field(hint: String, type: Int) = EditText(context).apply {
        this.hint = hint
        inputType = type
        textSize = 16f
        typeface = Fonts.medium(context)
        setTextColor(Tokens.textPrimary)
        setHintTextColor(Tokens.textMuted)
        isSingleLine = true
        background = rounded(Tokens.surfaceHigh, context.dpf(10f))
        setPadding(context.dp(16), 0, context.dp(16), 0)
        setOnFocusChangeListener { v, focused ->
            v.background = rounded(if (focused) Tokens.surfaceFocused else Tokens.surfaceHigh, context.dpf(10f)).apply {
                if (focused) setStroke(context.dp(2), Tokens.ring)
            }
        }
    }

    /** After a sign-in: save the profile and open its home, with "Who's watching?" below it. */
    protected fun finish(profile: Profile) {
        val saved = app.profiles.save(profile)
        app.profiles.markUsed(saved.id)
        activity.resetTo(ProfilesScreen(activity), HomeScreen(activity, app.profiles.get(saved.id) ?: saved))
    }

    protected suspend fun runOnboarding(rawkoonUrl: String, token: String) {
        val profile = onboard(context, rawkoonUrl, token) { step ->
            status.text = string(if (step == OnboardingStep.READING_ACCOUNT) R.string.progress_account else R.string.progress_jellyfin)
        }
        finish(profile)
    }
}

/** The Rawkoon server's address, asked for once, before the first profile. */
class ServerScreen(activity: MainActivity) : FormScreen(activity, activity.getString(R.string.server_title), activity.getString(R.string.server_hint)) {
    private val input = field("https://", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
    private val next = PillButton(context, string(R.string.continue_), primary = true)

    init {
        input.setText(Prefs.rawkoonUrl(context) ?: "https://")
        input.imeOptions = EditorInfo.IME_ACTION_GO
        input.setOnEditorActionListener { _, _, _ ->
            submit()
            true
        }
        column.addView(input, LinearLayout.LayoutParams(context.dp(380), context.dp(44)))
        column.addView(next, wrap().apply { topMargin = context.dp(14) })
        column.addView(status, LinearLayout.LayoutParams(context.dp(420), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(10) })
        next.setOnClickListener { submit() }
    }

    override fun focusDefault() {
        input.requestFocus()
        input.setSelection(input.text.length)
    }

    private fun submit() {
        val url = input.text.toString().trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://") || url.length < 12) {
            status.text = string(R.string.error_invalid_url)
            return
        }
        Prefs.setRawkoonUrl(context, url)
        activity.replace(SignInScreen(activity, url))
    }
}

/**
 * "Add a profile": the device code to approve on a phone, as text and as a QR code. Polls at the
 * server's pace, asks for a new code when one expires, then runs the Jellyfin side on its own.
 */
class SignInScreen(activity: MainActivity, private val rawkoonUrl: String) :
    DeviceCodeScreen(activity, activity.getString(R.string.add_profile_title), activity.getString(R.string.sign_in_hint)) {
    private val client = RawkoonClient(rawkoonUrl)

    init {
        panel.details.addView(label(context, 12f, Tokens.textMuted, Fonts.bold(context)).apply {
            text = string(R.string.sign_in_steps)
        }, wrap().apply { topMargin = context.dp(20) })
        column.addView(panel)
        column.addView(status, LinearLayout.LayoutParams(context.dp(520), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(18) })
        column.addView(retry, wrap().apply { topMargin = context.dp(8) })
        retry.setOnClickListener { start() }

        val email = PillButton(context, string(R.string.email_sign_in), Icon.KEY)
        email.setOnClickListener { activity.push(EmailScreen(activity, rawkoonUrl)) }
        val server = PillButton(context, string(R.string.change_server))
        server.setOnClickListener { activity.replace(ServerScreen(activity)) }
        val back = PillButton(context, string(R.string.back_to_profiles), Icon.BACK)
        back.setOnClickListener { activity.pop() }
        listOf(email, server, back).forEach { footer.addView(it, wrap().apply { marginStart = context.dp(6); marginEnd = context.dp(6) }) }
        start()
    }

    override fun focusDefault() {
        (footer.getChildAt(0))?.requestFocus()
    }

    private fun start() = runFlow(client) { token -> runOnboarding(rawkoonUrl, token) }
}

/** The fallback sign-in: the Rawkoon account's email and password. */
class EmailScreen(activity: MainActivity, private val rawkoonUrl: String) :
    FormScreen(activity, activity.getString(R.string.email_sign_in), activity.getString(R.string.email_hint)) {
    private val email = field(string(R.string.email_label), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
    private val password = field(string(R.string.password_label), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
    private val submit = PillButton(context, string(R.string.sign_in), primary = true)
    private var job: Job? = null

    init {
        column.addView(email, LinearLayout.LayoutParams(context.dp(360), context.dp(44)))
        column.addView(password, LinearLayout.LayoutParams(context.dp(360), context.dp(44)).apply { topMargin = context.dp(10) })
        column.addView(submit, wrap().apply { topMargin = context.dp(14) })
        column.addView(status, LinearLayout.LayoutParams(context.dp(420), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(10) })
        submit.setOnClickListener { signIn() }
    }

    override fun focusDefault() {
        email.requestFocus()
    }

    private fun signIn() {
        // A second OK while one is running would sign in, and save the profile, twice
        if (job?.isActive == true) return
        status.text = ""
        job = scope.launch {
            try {
                val token = RawkoonClient(rawkoonUrl).signInWithEmail(email.text.toString(), password.text.toString())
                runOnboarding(rawkoonUrl, token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.text = e.signInMessage(context)
            }
        }
    }
}

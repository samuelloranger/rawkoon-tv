package cloud.rawkoon.tv.screens

import android.graphics.Bitmap
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.Screen
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.data.Profile
import cloud.rawkoon.tv.rawkoon.DevicePoll
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    FormScreen(activity, activity.getString(R.string.add_profile_title), activity.getString(R.string.sign_in_hint)) {
    private val client = RawkoonClient(rawkoonUrl)
    private val qr = ImageView(context).apply {
        background = rounded(Color.WHITE, context.dpf(8f))
        val p = context.dp(11)
        setPadding(p, p, p, p)
    }
    // Never ellipsized: Fraunces draws a little past its measured width, which would cut the last letter
    private val code = label(context, 46f, Tokens.textPrimary, Fonts.title(context)).apply {
        ellipsize = null
        setPadding(0, 0, context.dp(6), 0)
    }
    private val address = label(context, 15f, Tokens.textSoft, Fonts.medium(context))
    private val retry = PillButton(context, string(R.string.try_again), primary = true)
    private var job: Job? = null

    init {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }.noClip()
        row.addView(qr, LinearLayout.LayoutParams(context.dp(QR_DP), context.dp(QR_DP)))
        val right = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        right.addView(code)
        right.addView(address, wrap().apply { topMargin = context.dp(4) })
        right.addView(label(context, 12f, Tokens.textMuted, Fonts.bold(context)).apply {
            text = string(R.string.sign_in_steps)
        }, wrap().apply { topMargin = context.dp(20) })
        row.addView(right, LinearLayout.LayoutParams(context.dp(300), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(44) })
        column.addView(row)
        column.addView(status, LinearLayout.LayoutParams(context.dp(520), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(18) })
        column.addView(retry, wrap().apply { topMargin = context.dp(8) })
        retry.visibility = android.view.View.GONE
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

    private fun start() {
        job?.cancel()
        retry.visibility = android.view.View.GONE
        status.text = ""
        job = scope.launch {
            try {
                var device = client.requestDeviceCode()
                showCode(device)
                var interval = device.interval.coerceAtLeast(1) * 1_000L
                while (true) {
                    delay(interval)
                    when (val poll = client.pollDeviceToken(device.deviceCode)) {
                        DevicePoll.Pending -> Unit
                        DevicePoll.SlowDown -> interval += 5_000L
                        DevicePoll.Expired -> {
                            device = client.requestDeviceCode()
                            showCode(device)
                            interval = device.interval.coerceAtLeast(1) * 1_000L
                        }
                        DevicePoll.Denied -> {
                            fail(string(R.string.error_denied))
                            return@launch
                        }
                        is DevicePoll.Granted -> {
                            runOnboarding(rawkoonUrl, poll.token)
                            return@launch
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e.signInMessage(context))
            }
        }
    }

    private fun showCode(device: cloud.rawkoon.tv.rawkoon.DeviceCode) {
        code.text = device.displayCode
        address.text = device.verificationUri.removePrefix("https://").removePrefix("http://")
        qr.setImageBitmap(qrBitmap(device.verificationUriComplete ?: device.verificationUri, context.dp(QR_DP) - context.dp(22)))
    }

    private fun fail(message: String) {
        status.text = message
        retry.visibility = android.view.View.VISIBLE
        retry.requestFocus()
    }

    private companion object {
        const val QR_DP = 190

        fun qrBitmap(text: String, size: Int): Bitmap {
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 0))
            val pixels = IntArray(matrix.width * matrix.height) { i -> if (matrix[i % matrix.width, i / matrix.width]) Color.BLACK else Color.WHITE }
            return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.RGB_565)
        }
    }
}

/** The fallback sign-in: the Rawkoon account's email and password. */
class EmailScreen(activity: MainActivity, private val rawkoonUrl: String) :
    FormScreen(activity, activity.getString(R.string.email_sign_in), activity.getString(R.string.email_hint)) {
    private val email = field(string(R.string.email_label), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
    private val password = field(string(R.string.password_label), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
    private val submit = PillButton(context, string(R.string.sign_in), primary = true)

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
        status.text = ""
        scope.launch {
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

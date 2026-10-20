package cloud.rawkoon.tv.screens

import android.graphics.Bitmap
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.rawkoon.DeviceCode
import cloud.rawkoon.tv.rawkoon.DevicePoll
import cloud.rawkoon.tv.rawkoon.RawkoonClient
import cloud.rawkoon.tv.rawkoon.signInMessage
import cloud.rawkoon.tv.ui.Fonts
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

/**
 * Links a profile that has no Rawkoon session (one imported from the first RawkoonTV, or whose
 * session ended) to its Rawkoon account, with the same device code as adding a profile. The token
 * goes onto the existing profile: its Jellyfin sign-in is already good, so nothing else is redone.
 * Goes back to the page that asked once linked, which then reloads.
 */
class RawkoonLinkScreen(activity: MainActivity, private val profileId: String) :
    FormScreen(activity, activity.getString(R.string.rk_connect_title), activity.getString(R.string.sign_in_hint)) {
    private var rawkoonUrl: String? = app.profiles.get(profileId)?.let { it.rawkoonUrl ?: Prefs.rawkoonUrl(context) }
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
    private val codeRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
    }.noClip()
    private val server = field("https://", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
    private val serverNext = PillButton(context, string(R.string.continue_), primary = true)
    private var job: Job? = null

    init {
        codeRow.addView(qr, LinearLayout.LayoutParams(context.dp(QR_DP), context.dp(QR_DP)))
        val right = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        right.addView(code)
        right.addView(address, wrap().apply { topMargin = context.dp(4) })
        codeRow.addView(right, LinearLayout.LayoutParams(context.dp(300), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(44) })
        column.addView(codeRow)
        column.addView(server, LinearLayout.LayoutParams(context.dp(380), context.dp(44)))
        column.addView(serverNext, wrap().apply { topMargin = context.dp(14) })
        column.addView(status, LinearLayout.LayoutParams(context.dp(520), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(18) })
        column.addView(retry, wrap().apply { topMargin = context.dp(8) })
        retry.visibility = View.GONE
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

    /** Asks for a code, polls at the server's pace, asks for a new code when one expires. */
    private fun start() {
        val url = rawkoonUrl ?: return
        val client = RawkoonClient(url)
        job?.cancel()
        retry.visibility = View.GONE
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
                            link(client, url, poll.token)
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

    /** Puts the session on the existing profile, read again in case it changed while the code was out. */
    private suspend fun link(client: RawkoonClient, url: String, token: String) {
        val userId = client.bootstrap(token).userId.ifBlank { null }
        val profile = app.profiles.get(profileId) ?: return activity.pop()
        app.profiles.save(profile.copy(rawkoonUrl = url, rawkoonToken = token, rawkoonUserId = userId))
        Toast.makeText(context, R.string.rk_linked, Toast.LENGTH_SHORT).show()
        activity.pop()
    }

    private fun showCode(device: DeviceCode) {
        codeRow.visibility = View.VISIBLE
        code.text = device.displayCode
        address.text = device.verificationUri.removePrefix("https://").removePrefix("http://")
        qr.setImageBitmap(qrBitmap(device.verificationUriComplete ?: device.verificationUri, context.dp(QR_DP) - context.dp(22)))
    }

    private fun fail(message: String) {
        status.text = message
        retry.visibility = View.VISIBLE
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

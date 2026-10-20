package cloud.rawkoon.tv.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import cloud.rawkoon.tv.rawkoon.DeviceCode

/**
 * A device code to approve on a phone: the QR code on the left, the code and the address it opens
 * on the right. [details] is the column on the right, for what a screen adds under the address.
 */
class DeviceCodePanel(context: Context) : LinearLayout(context) {
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
    val details = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        noClip()
        addView(qr, LayoutParams(context.dp(QR_DP), context.dp(QR_DP)))
        details.addView(code)
        details.addView(address, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(4) })
        addView(details, LayoutParams(context.dp(300), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(44) })
    }

    /** Shows [device]'s code, its address and a QR code that opens it already filled in. */
    fun show(device: DeviceCode) {
        visibility = VISIBLE
        code.text = device.displayCode
        address.text = device.verificationUri.removePrefix("https://").removePrefix("http://")
        qr.setImageBitmap(qrBitmap(device.verificationUriComplete ?: device.verificationUri, context.dp(QR_DP) - context.dp(22)))
    }

    private companion object {
        const val QR_DP = 190
    }
}

package cloud.rawkoon.tv.screens

import android.view.View
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.rawkoon.DeviceCodeFlow
import cloud.rawkoon.tv.rawkoon.RawkoonClient
import cloud.rawkoon.tv.rawkoon.signInMessage
import cloud.rawkoon.tv.ui.DeviceCodePanel
import cloud.rawkoon.tv.ui.PillButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A form that signs in with a device code: the [panel] shows the code, [runFlow] runs the flow
 * and hands the token to the screen, and a failure puts its message under the code with [retry].
 * The screen adds the views and decides what the token is for.
 */
abstract class DeviceCodeScreen(activity: MainActivity, title: String, hint: String) : FormScreen(activity, title, hint) {
    protected val panel = DeviceCodePanel(context)
    protected val retry = PillButton(context, string(R.string.try_again), primary = true).apply { visibility = View.GONE }
    private var job: Job? = null

    /** Asks [client] for a code, shows it, and passes the approved token to [onToken]; a new run replaces one under way. */
    protected fun runFlow(client: RawkoonClient, onToken: suspend (String) -> Unit) {
        job?.cancel()
        retry.visibility = View.GONE
        status.text = ""
        job = scope.launch {
            try {
                val token = DeviceCodeFlow.run(client, panel::show)
                if (token == null) fail(string(R.string.error_denied)) else onToken(token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e.signInMessage(context))
            }
        }
    }

    private fun fail(message: String) {
        status.text = message
        retry.visibility = View.VISIBLE
        retry.requestFocus()
    }
}

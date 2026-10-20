package cloud.rawkoon.tv.rawkoon

import android.content.Context
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.Badge

/**
 * The words the Rawkoon rows, badges and title page use. The app answers from its string
 * resources ([ResourceText]); the row logic only sees this, so it stays testable off the device.
 */
interface RawkoonText {
    fun upcomingTitle(): String
    fun requestsTitle(): String
    fun today(): String
    fun tomorrow(): String
    fun episodes(count: Int): String
    fun inLibrary(): String
    fun inProgress(): String

    /** What a state is called: "Pending", "Downloading 40 %"; null when there is nothing to say yet. */
    fun stateLabel(state: TitleState): String?

    fun badge(state: TitleState): Badge? = stateLabel(state)?.let { Badge(it, tone(state)) }

    private fun tone(state: TitleState) = when (state) {
        TitleState.Pending -> Badge.Tone.WAITING
        is TitleState.Processing, TitleState.Partial -> Badge.Tone.ACTIVE
        TitleState.Available -> Badge.Tone.READY
        is TitleState.Refused -> Badge.Tone.REFUSED
        TitleState.Skipped, TitleState.Requestable -> Badge.Tone.NEUTRAL
    }
}

class ResourceText(private val context: Context) : RawkoonText {
    override fun upcomingTitle() = context.getString(R.string.rk_row_upcoming)
    override fun requestsTitle() = context.getString(R.string.rk_row_requests)
    override fun today() = context.getString(R.string.rk_today)
    override fun tomorrow() = context.getString(R.string.rk_tomorrow)
    override fun episodes(count: Int) = context.resources.getQuantityString(R.plurals.episodes_count, count, count)
    override fun inLibrary() = context.getString(R.string.rk_in_library)
    override fun inProgress() = context.getString(R.string.rk_in_progress)

    override fun stateLabel(state: TitleState): String? = when (state) {
        TitleState.Requestable -> null
        TitleState.Pending -> context.getString(R.string.rk_state_pending)
        is TitleState.Processing -> when {
            state.downloading && state.percent != null -> context.getString(R.string.rk_state_downloading_percent, state.percent)
            state.downloading -> context.getString(R.string.rk_state_downloading)
            else -> context.getString(R.string.rk_state_approved)
        }
        TitleState.Partial -> context.getString(R.string.rk_state_partial)
        TitleState.Available -> context.getString(R.string.rk_state_available)
        is TitleState.Refused -> context.getString(R.string.rk_state_refused)
        TitleState.Skipped -> context.getString(R.string.rk_state_skipped)
    }
}

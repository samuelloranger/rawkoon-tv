package cloud.rawkoon.tv.screens

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.ui.CircleButton
import cloud.rawkoon.tv.ui.EntryRow
import cloud.rawkoon.tv.ui.Icon
import cloud.rawkoon.tv.ui.PillButton
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.label
import cloud.rawkoon.tv.ui.noClip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The actions under a title: play or resume (the player comes in the next milestone), watched and
 * favorite. The toggles change on screen at once, [onChanged] included, and go back if the server
 * refuses.
 */
class ActionBar(private val screen: RailScreen) {
    private val context = screen.context
    val primary = PillButton(context, context.getString(R.string.play), Icon.PLAY, primary = true, height = 40)

    /** Runs before the bar is used or gets focus: a page that settles its item late catches up first. */
    var beforeUse: () -> Unit = {}

    /** Coming from above or below, focus lands on the main action rather than the nearest button. */
    val view: LinearLayout = object : EntryRow(context) {
        override fun entry(): View = primary

        override fun requestChildFocus(child: View?, focused: View?) {
            if (!hasFocus()) beforeUse()
            super.requestChildFocus(child, focused)
        }
    }.apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }.noClip()
    private val watched = CircleButton(context, Icon.CHECK, context.getString(R.string.watched))
    private val favorite = CircleButton(context, Icon.HEART, context.getString(R.string.favorite))
    private var item: MediaItem? = null

    /** For a show: the episode the play button resumes or starts. */
    var next: MediaItem? = null

    /** A toggle changed the item on screen, or put it back after a refusal: a page showing it elsewhere (its card) follows. */
    var onChanged: (MediaItem) -> Unit = {}

    init {
        view.addView(primary)
        view.addView(watched, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(6) })
        view.addView(favorite, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(2) })
        primary.setOnClickListener { play() }
        watched.setOnClickListener { toggle(watched = true) }
        favorite.setOnClickListener { toggle(watched = false) }
    }

    fun bind(item: MediaItem) {
        this.item = item
        primary.label.text = playLabel(item)
        watched.setOn(item.played)
        favorite.setOn(item.favorite)
    }

    /** Plays the item, or for a show the episode it is at. */
    private fun play() {
        beforeUse()
        val current = item ?: return
        val target = if (current.kind == MediaItem.Kind.SERIES) next else current
        if (target == null) {
            Toast.makeText(context, R.string.page_error, Toast.LENGTH_SHORT).show()
            return
        }
        screen.activity.push(PlayerScreen(screen.activity, screen.session, target))
    }

    private fun playLabel(item: MediaItem): String {
        val episode = next?.takeIf { item.kind == MediaItem.Kind.SERIES } ?: item.takeIf { it.kind == MediaItem.Kind.EPISODE }
        val started = (episode ?: item).progress != null
        val label = episode?.episodeLabel
        return when {
            label != null -> context.getString(if (started) R.string.resume_episode else R.string.play_episode, label)
            started -> context.getString(R.string.resume)
            else -> context.getString(R.string.play)
        }
    }

    private fun toggle(watched: Boolean) {
        beforeUse()
        val current = item ?: return
        // Marking watched clears the resume point on the server: the page shows it at once, "Play" included
        val updated = when {
            !watched -> current.copy(favorite = !current.favorite)
            current.played -> current.copy(played = false)
            else -> current.copy(played = true, progress = null, resumeMs = 0)
        }
        bind(updated)
        onChanged(updated)
        screen.scope.launch {
            try {
                if (watched) screen.session.setPlayed(current.id, updated.played) else screen.session.setFavorite(current.id, updated.favorite)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Put back only this field: another toggle may have changed the item meanwhile
                item?.takeIf { it.id == current.id }?.let { now ->
                    val restored = if (watched) now.copy(played = current.played, progress = current.progress, resumeMs = current.resumeMs) else now.copy(favorite = current.favorite)
                    bind(restored)
                    onChanged(restored)
                }
                Toast.makeText(context, R.string.action_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

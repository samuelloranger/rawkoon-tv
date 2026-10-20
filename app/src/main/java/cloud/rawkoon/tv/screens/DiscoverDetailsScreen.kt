package cloud.rawkoon.tv.screens

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.JellyfinTmdbLookup
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.jellyfin.Person
import cloud.rawkoon.tv.rawkoon.AlreadyInLibrary
import cloud.rawkoon.tv.rawkoon.AlreadyRequested
import cloud.rawkoon.tv.rawkoon.RawkoonDiscover
import cloud.rawkoon.tv.rawkoon.RawkoonException
import cloud.rawkoon.tv.rawkoon.RawkoonText
import cloud.rawkoon.tv.rawkoon.ResourceText
import cloud.rawkoon.tv.rawkoon.TitleModal
import cloud.rawkoon.tv.rawkoon.TitleState
import cloud.rawkoon.tv.rawkoon.TitleStatus
import cloud.rawkoon.tv.rawkoon.rawTmdbImage
import cloud.rawkoon.tv.rawkoon.rawkoonDiscover
import cloud.rawkoon.tv.rawkoon.tmdbImage
import cloud.rawkoon.tv.rawkoon.tmdbLogo
import cloud.rawkoon.tv.rawkoon.toItem
import cloud.rawkoon.tv.ui.Chip
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.Icon
import cloud.rawkoon.tv.ui.LogoView
import cloud.rawkoon.tv.ui.PersonCard
import cloud.rawkoon.tv.ui.PillButton
import cloud.rawkoon.tv.ui.Rows
import cloud.rawkoon.tv.ui.Section
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.TitleCard
import cloud.rawkoon.tv.ui.badgeColors
import cloud.rawkoon.tv.ui.chipRow
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.dpf
import cloud.rawkoon.tv.ui.fill
import cloud.rawkoon.tv.ui.label
import cloud.rawkoon.tv.ui.noClip
import cloud.rawkoon.tv.ui.rounded
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Opens a TMDB title's page. With [preferLibrary], a title Rawkoon has in its library opens its
 * Jellyfin page when Jellyfin has it (looked up by TMDB id), and falls back to the TMDB page.
 */
fun RailScreen.openTitle(item: MediaItem, preferLibrary: Boolean = false) {
    val tmdbId = item.tmdbId ?: return
    if (!preferLibrary) {
        activity.push(DiscoverDetailsScreen(activity, session, item))
        return
    }
    scope.launch {
        val found = JellyfinTmdbLookup(session).find(tmdbId, item.title, item.year, item.kind == MediaItem.Kind.SERIES)
        activity.push(if (found != null) DetailsScreen(activity, session, found) else DiscoverDetailsScreen(activity, session, item))
    }
}

/**
 * A TMDB title's page, laid out like a library title's: poster, logo or title, chips, genres and
 * overview, then what it is for this account (request it, or where it stands, or open it in
 * Jellyfin when it is there), then the cast and similar titles. Rawkoon requests the whole title.
 */
class DiscoverDetailsScreen(activity: MainActivity, session: JellyfinSession, private val start: MediaItem) :
    RailScreen(activity, session, Destination.DISCOVER) {
    private val isMovie = start.kind != MediaItem.Kind.SERIES
    private val tmdbId = checkNotNull(start.tmdbId)
    private val text: RawkoonText = ResourceText(context)
    private val page = Rows(context)
    private val block = FrameLayout(context).noClip()
    private val poster = ImageView(context)
    private val logo = LogoView(context)
    private val title = label(context, 34f, Tokens.textPrimary, Fonts.title(context), lines = 2).apply { gravity = Gravity.BOTTOM }
    private val info = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val request = PillButton(context, string(R.string.rk_request), Icon.PLUS, primary = true, height = 40)
    private val open = PillButton(context, string(R.string.rk_open), Icon.PLAY, primary = true, height = 40)
    private val state = label(context, 13f, Tokens.textSoft, Fonts.bold(context))
    private val actions = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }.noClip()
    private var titleState: TitleState = TitleState.Requestable
    private var inLibrary: MediaItem? = null
    private var modal: TitleModal? = null
    private var busy = false

    /** The token the page was loaded with: coming back from linking reloads only when it changed. */
    private var loadedWith: String? = null

    init {
        page.column.setPadding(0, 0, 0, context.dp(200))
        page.anchor = context.dp(34)
        content.addView(page, fill())
        buildBlock()
        page.column.addView(block, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(BLOCK_H)))
        showTitle()
        request.setOnClickListener { onRequest() }
        open.setOnClickListener { inLibrary?.let { activity.push(DetailsScreen(activity, session, it)) } }
        load()
    }

    private fun buildBlock() {
        poster.background = rounded(Tokens.surfaceHigh, context.dpf(8f))
        block.addView(poster, FrameLayout.LayoutParams(context.dp(POSTER_W), context.dp(POSTER_H)).apply {
            leftMargin = context.dp(Tokens.CONTENT_START)
            topMargin = context.dp(TOP)
        })
        val box = FrameLayout(context)
        box.addView(logo, fill())
        box.addView(title, fill())
        info.addView(box, LinearLayout.LayoutParams(context.dp(LOGO_W), context.dp(LOGO_H)))
        block.addView(info, FrameLayout.LayoutParams(context.dp(540), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(Tokens.CONTENT_START + POSTER_W + 25)
            topMargin = context.dp(TOP)
        })
        actions.addView(request)
        actions.addView(open)
        actions.addView(state, LinearLayout.LayoutParams(context.dp(380), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(10) })
        block.addView(actions, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(Tokens.CONTENT_START - Tokens.RING_INSET)
            topMargin = context.dp(TOP + POSTER_H + 14)
        })
        title.setAutoSizeTextTypeUniformWithConfiguration(20, 34, 2, TypedValue.COMPLEX_UNIT_SP)
        val w = context.dp(POSTER_W)
        val h = context.dp(POSTER_H)
        start.posterUrl?.let { url -> app.images.load(url, w, h, context.dpf(8f), isCurrent = { true }) { poster.setImageBitmap(it) } }
    }

    /** Fills the top block from the list entry, then again with the modal's genres, runtime and logo. */
    private fun showTitle() {
        val modal = modal
        val logoUrl = tmdbLogo(modal?.logoUrl)
        if (logoUrl == null) {
            title.text = start.title
        } else {
            title.text = ""
            val lw = context.dp(LOGO_W)
            val lh = context.dp(LOGO_H)
            app.images.load("$logoUrl&maxWidth=$lw", lw, lh, 0f, contain = true, isCurrent = { true }) { logo.bitmap = it }
        }
        while (info.childCount > 1) info.removeViewAt(1)
        info.addView(chipRow(context, metaChips(modal)), wrapRow(10))
        val tags = modal?.genres.orEmpty().take(3).map { Chip.Outline(it) }
        if (tags.isNotEmpty()) info.addView(chipRow(context, tags, 12.5f), wrapRow(8))
        info.addView(label(context, 14f, 0xE6ECE3DB.toInt(), Fonts.body(context), lines = 3).apply {
            this.text = modal?.overview ?: start.overview
            setLineSpacing(0f, 1.2f)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(10) })
    }

    private fun wrapRow(top: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(top) }

    private fun metaChips(modal: TitleModal?): List<Chip> = buildList {
        add(Chip.Kind(string(if (isMovie) R.string.kind_movie else R.string.kind_show)))
        val year = modal?.year ?: start.year
        if (year != null) {
            val end = modal?.endYear
            add(
                Chip.Text(
                    when {
                        isMovie -> year.toString()
                        modal?.continuing == true -> "$year –"
                        end != null && end != year -> "$year – $end"
                        else -> year.toString()
                    },
                ),
            )
        }
        if (isMovie) {
            modal?.runtimeMinutes?.let { add(Chip.Text(MediaItem.duration(it))) }
        } else {
            modal?.seasons?.let { add(Chip.Text(context.resources.getQuantityString(R.plurals.seasons_count, it, it))) }
            modal?.episodes?.let { add(Chip.Text(context.resources.getQuantityString(R.plurals.episodes_count, it, it))) }
        }
        val score = modal?.voteAverage ?: start.communityRating?.toDouble()
        score?.let { add(Chip.Score("★ " + String.format(Locale.ROOT, "%.1f", it))) }
    }

    private fun load() {
        val profile = app.profiles.get(session.profile.id) ?: session.profile
        loadedWith = profile.rawkoonToken
        val api = profile.rawkoonDiscover(context) ?: return reconnect()
        scope.launch {
            try {
                coroutineScope {
                    val modalCall = async { api.modal(isMovie, tmdbId) }
                    // The page is still worth showing without knowing where the title stands
                    val statusCall = async { optional { api.status(isMovie, tmdbId) } }
                    val similarCall = async { optional { api.similar(isMovie, tmdbId) }.orEmpty() }
                    modal = modalCall.await()
                    showTitle()
                    modal?.backdropUrl?.let { backdrop.show(it) }
                    applyStatus(statusCall.await())
                    val cast = modal?.cast.orEmpty().map { Person(it.id.toString(), it.name, it.character, tmdbImage(it.photoUrl)) }
                    addRows(cast, similarCall.await().map { it.toItem(text) })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RawkoonException.Unauthorized) {
                reconnect()
            } catch (e: Exception) {
                Toast.makeText(context, R.string.page_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** A call whose failure only costs this page a part of itself; a refused session still ends it. */
    private suspend fun <T> optional(call: suspend () -> T): T? = try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RawkoonException.Unauthorized) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** The session ended (or the profile never had one): link the account again, then reload. */
    private fun reconnect() {
        Toast.makeText(context, R.string.rk_reconnect_hint, Toast.LENGTH_LONG).show()
        activity.push(RawkoonLinkScreen(activity, session.profile.id))
    }

    private fun applyStatus(status: TitleStatus?) {
        titleState = TitleState.of(status, !isMovie)
        if (!titleState.inLibrary) inLibrary = null
        bindActions()
        if (!titleState.inLibrary) return
        scope.launch {
            inLibrary = JellyfinTmdbLookup(session).find(tmdbId, start.title, modal?.year ?: start.year, !isMovie)
            bindActions()
        }
    }

    /**
     * The request button says what to do, or where the title stands (and checks again when pressed);
     * Jellyfin's page, when there is one, replaces it. A refusal's reason shows beside it.
     */
    private fun bindActions() {
        val hadFocus = request.hasFocus() || open.hasFocus()
        val opens = inLibrary != null
        open.visibility = if (opens) View.VISIBLE else View.GONE
        request.visibility = if (opens) View.GONE else View.VISIBLE
        request.label.text = when {
            busy -> string(R.string.rk_requesting)
            titleState.canRequest -> string(R.string.rk_request)
            else -> text.stateLabel(titleState).orEmpty()
        }
        val badge = text.badge(titleState)
        val aside = if (opens) badge?.text else (titleState as? TitleState.Refused)?.reason?.let { string(R.string.rk_refused_reason, it) }
        state.text = aside.orEmpty()
        state.setTextColor(if (opens && badge != null) badgeColors(badge.tone).first else Tokens.textSoft)
        if (hadFocus) focusDefault()
    }

    private fun onRequest() {
        if (busy) return
        if (!titleState.canRequest) {
            refreshStatus()
            return
        }
        val api = (app.profiles.get(session.profile.id) ?: session.profile).rawkoonDiscover(context) ?: return reconnect()
        busy = true
        bindActions()
        scope.launch {
            try {
                api.request(isMovie, tmdbId, start.title, rawTmdbImage(start.posterUrl), modal?.year ?: start.year)
                Toast.makeText(context, R.string.rk_request_sent, Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AlreadyRequested) {
                Toast.makeText(context, R.string.rk_already_requested, Toast.LENGTH_SHORT).show()
            } catch (e: AlreadyInLibrary) {
                Toast.makeText(context, R.string.rk_already_in_library, Toast.LENGTH_SHORT).show()
            } catch (e: RawkoonException.Unauthorized) {
                busy = false
                bindActions()
                return@launch reconnect()
            } catch (e: Exception) {
                Toast.makeText(context, R.string.action_failed, Toast.LENGTH_SHORT).show()
            }
            busy = false
            // Whatever the answer, Rawkoon now knows better than this page does (an admin's request is approved at once)
            refreshStatus()
        }
    }

    private fun refreshStatus() {
        val api: RawkoonDiscover = (app.profiles.get(session.profile.id) ?: session.profile).rawkoonDiscover(context) ?: return
        scope.launch {
            try {
                applyStatus(api.status(isMovie, tmdbId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                bindActions()
            }
        }
    }

    private fun addRows(cast: List<Person>, similar: List<MediaItem>) {
        // Loading again (after linking the account) replaces the rows instead of adding to them
        while (page.column.childCount > 1) page.column.removeViewAt(1)
        if (cast.isNotEmpty()) {
            val section = Section(context, "cast", string(R.string.row_cast))
            section.row.setCards(cast.map { PersonCard(context, it, app.images) })
            addSection(section)
        }
        if (similar.isNotEmpty()) {
            val section = Section(context, "similar", string(R.string.row_similar))
            section.row.setCards(similar.map { s -> TitleCard(context, s, app.images, {}, { openTitle(it.item) }) })
            addSection(section)
        }
    }

    private fun addSection(section: Section) {
        section.row.onLeftEdge = { rail.activeItem() }
        page.column.addView(section)
    }

    override fun focusDefault() {
        (if (open.visibility == View.VISIBLE) open else request).requestFocus()
    }

    /** Back from linking the account: load again with the new session. */
    override fun onShown(first: Boolean) {
        super.onShown(first)
        if (first) return
        val token = (app.profiles.get(session.profile.id) ?: session.profile).rawkoonToken
        if (token != null && token != loadedWith) load()
    }

    private companion object {
        const val TOP = 50
        const val POSTER_W = 135
        const val POSTER_H = 202
        const val LOGO_W = 330
        const val LOGO_H = 66
        const val BLOCK_H = TOP + POSTER_H + 14 + 56
    }
}

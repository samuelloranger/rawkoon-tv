package cloud.rawkoon.tv.screens

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import cloud.rawkoon.tv.rawkoon.tmdbBackdrop
import cloud.rawkoon.tv.rawkoon.tmdbImage
import cloud.rawkoon.tv.rawkoon.tmdbLogo
import cloud.rawkoon.tv.rawkoon.toItem
import cloud.rawkoon.tv.ui.Card
import cloud.rawkoon.tv.ui.Chip
import cloud.rawkoon.tv.ui.ChoiceDialog
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.Icon
import cloud.rawkoon.tv.ui.PersonCard
import cloud.rawkoon.tv.ui.PillButton
import cloud.rawkoon.tv.ui.Rows
import cloud.rawkoon.tv.ui.Section
import cloud.rawkoon.tv.ui.TitleBlock
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.badgeColors
import cloud.rawkoon.tv.ui.cardCaption
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.fill
import cloud.rawkoon.tv.ui.kindChip
import cloud.rawkoon.tv.ui.label
import cloud.rawkoon.tv.ui.noClip
import cloud.rawkoon.tv.ui.showCountChips
import cloud.rawkoon.tv.ui.yearChip
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * A TMDB title's page, laid out like a library title's: poster, logo or title, chips, genres and
 * overview, then what it is for this account (request it, or where it stands, or open it in
 * Jellyfin when it is there), then the cast and similar titles. Rawkoon requests the whole title.
 */
class DiscoverDetailsScreen(activity: MainActivity, session: JellyfinSession, private val start: MediaItem) :
    RailScreen(activity, session, Destination.DISCOVER) {
    private val isMovie = start.kind != MediaItem.Kind.SERIES
    private val tmdbId = checkNotNull(start.tmdb?.id)
    private val text: RawkoonText = ResourceText(context)
    private val page = Rows(context)
    private val block = TitleBlock(context, app.images) { alive }
    private val request = PillButton(context, string(R.string.rk_request), Icon.PLUS, primary = true, height = 40)
    private val open = PillButton(context, string(R.string.rk_open), Icon.PLAY, primary = true, height = 40)
    private val withdraw = PillButton(context, string(R.string.rk_withdraw), Icon.XMARK, height = 40)
    private val state = label(context, 13f, Tokens.textSoft, Fonts.bold(context))
    private val actions = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }.noClip()
    private var titleState: TitleState = TitleState.Requestable
    private var status: TitleStatus? = null
    private var dialog: ChoiceDialog? = null
    private var inLibrary: MediaItem? = null
    private var modal: TitleModal? = null
    private var busy = false
    private var loadJob: Job? = null

    /** The status fetch and the library lookup that follows it: the latest one wins. */
    private var statusJob: Job? = null
    private val opener = TitleOpener(this)

    /** The token the page was loaded with: coming back from linking reloads only when it changed. */
    private var loadedWith: String? = null

    init {
        page.column.setPadding(0, 0, 0, context.dp(PAGE_BOTTOM))
        page.anchor = context.dp(PAGE_ANCHOR)
        content.addView(page, fill())
        buildBlock()
        page.column.addView(block, TitleBlock.params(context))
        showTitle()
        request.setOnClickListener { onRequest() }
        withdraw.setOnClickListener { onWithdraw() }
        open.setOnClickListener { inLibrary?.let { activity.push(DetailsScreen(activity, session, it)) } }
        load()
    }

    private fun buildBlock() {
        actions.addView(request)
        actions.addView(withdraw, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(10) })
        actions.addView(open)
        actions.addView(state, LinearLayout.LayoutParams(context.dp(STATE_W), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(10) })
        block.setActions(actions)
        block.setPoster(start.posterUrl, fillWidth = false)
    }

    /** Fills the top block from the list entry, then again with the modal's genres, runtime and logo. */
    private fun showTitle() {
        val modal = modal
        block.bind(
            start.title, tmdbLogo(modal?.logoUrl), metaChips(modal), modal?.genres.orEmpty().take(3).map { Chip.Outline(it) },
            modal?.overview ?: start.overview,
        )
    }

    private fun metaChips(modal: TitleModal?): List<Chip> = buildList {
        add(kindChip(context, !isMovie))
        val year = modal?.year ?: start.year
        if (year != null) add(yearChip(year, !isMovie, modal?.continuing == true, modal?.endYear))
        if (isMovie) {
            modal?.runtimeMinutes?.let { add(Chip.Text(MediaItem.duration(it))) }
        } else {
            addAll(showCountChips(context, modal?.seasons, modal?.episodes))
        }
        val score = modal?.voteAverage ?: start.communityRating?.toDouble()
        score?.let { add(Chip.Score("★ " + String.format(Locale.ROOT, "%.1f", it))) }
    }

    private fun load() {
        val profile = app.profiles.get(session.profile.id) ?: session.profile
        loadedWith = profile.rawkoonToken
        val api = profile.rawkoonDiscover(context) ?: return reconnect()
        loadJob?.cancel()
        loadJob = launchLoading(onError = { e -> if (e is RawkoonException.Unauthorized) reconnect() else toastPageError() }) {
            val modalCall = async { api.modal(isMovie, tmdbId) }
            // The page is still worth showing without knowing where the title stands
            val statusCall = async { optional { api.status(isMovie, tmdbId) } }
            val similarCall = async { optional { api.similar(isMovie, tmdbId) }.orEmpty() }
            modal = modalCall.await()
            showTitle()
            modal?.backdropUrl?.let { backdrop.show(tmdbBackdrop(it)) }
            val status = statusCall.await()
            statusJob?.cancel()
            statusJob = scope.launch { applyStatus(status) }
            val cast = modal?.cast.orEmpty().map { Person(it.id.toString(), it.name, it.character, tmdbImage(it.photoUrl)) }
            addRows(cast, similarCall.await().map { it.toItem(text) })
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

    /** Binds what Rawkoon knows of the title, then looks for its Jellyfin item when the library can serve it. */
    private suspend fun applyStatus(status: TitleStatus?) {
        this.status = status
        titleState = TitleState.of(status, !isMovie)
        if (!titleState.inLibrary) inLibrary = null
        bindActions()
        if (!titleState.inLibrary) return
        inLibrary = JellyfinTmdbLookup(session).find(tmdbId, start.title, modal?.year ?: start.year, !isMovie)
        bindActions()
    }

    /**
     * The request button says what to do, or where the title stands (and checks again when pressed);
     * Jellyfin's page, when there is one, replaces it. A refusal's reason shows beside it.
     */
    private fun bindActions() {
        val hadFocus = request.hasFocus() || open.hasFocus() || withdraw.hasFocus()
        val opens = inLibrary != null
        open.visibility = if (opens) View.VISIBLE else View.GONE
        request.visibility = if (opens) View.GONE else View.VISIBLE
        val own = TitleState.withdrawable(titleState, status?.request)
        withdraw.visibility = if (!opens && own != null) View.VISIBLE else View.GONE
        withdraw.label.text = string(if (titleState is TitleState.Refused) R.string.rk_clear else R.string.rk_withdraw)
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

    /** Asks first: a remote has no undo. Withdrawing a declined request clears it, and the title can be asked for again. */
    private fun onWithdraw() {
        if (busy) return
        val own = TitleState.withdrawable(titleState, status?.request) ?: return
        val refused = titleState is TitleState.Refused
        dialog = ChoiceDialog(
            context,
            string(if (refused) R.string.rk_clear_confirm else R.string.rk_withdraw_confirm),
            listOf(
                string(if (refused) R.string.rk_clear else R.string.rk_withdraw) to { withdraw(own.id) },
                string(R.string.cancel) to {},
            ),
        ).also { it.open(view) }
    }

    private fun withdraw(requestId: Int) {
        val api = (app.profiles.get(session.profile.id) ?: session.profile).rawkoonDiscover(context) ?: return reconnect()
        busy = true
        bindActions()
        scope.launch {
            try {
                api.withdraw(requestId)
                Toast.makeText(context, R.string.rk_withdrawn, Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: RawkoonException.Unauthorized) {
                busy = false
                bindActions()
                return@launch reconnect()
            } catch (e: Exception) {
                Toast.makeText(context, R.string.action_failed, Toast.LENGTH_SHORT).show()
            }
            busy = false
            refreshStatus()
        }
    }

    private fun refreshStatus() {
        val api: RawkoonDiscover = (app.profiles.get(session.profile.id) ?: session.profile).rawkoonDiscover(context) ?: return
        statusJob?.cancel()
        statusJob = scope.launch {
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
            section.row.setCards(similar.map { s -> Card(context, s, false, app.images, {}, { opener.open(it.item) }, cardCaption(s)) })
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

    override fun onBack(): Boolean {
        dialog?.takeIf { it.isAttachedToWindow }?.let {
            it.close()
            return true
        }
        return super.onBack()
    }

    /** Back from linking the account: load again with the new session. */
    override fun onShown(first: Boolean) {
        super.onShown(first)
        if (first) return
        val token = (app.profiles.get(session.profile.id) ?: session.profile).rawkoonToken
        if (token != null && token != loadedWith) load()
    }

    private companion object {
        const val STATE_W = 380
        const val PAGE_BOTTOM = 200
        const val PAGE_ANCHOR = 34
    }
}

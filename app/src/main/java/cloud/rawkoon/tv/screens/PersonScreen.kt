package cloud.rawkoon.tv.screens

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.FindGroup
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.jellyfin.Person
import cloud.rawkoon.tv.jellyfin.PersonInfo
import cloud.rawkoon.tv.jellyfin.personInfo
import cloud.rawkoon.tv.jellyfin.personTitles
import cloud.rawkoon.tv.ui.Card
import cloud.rawkoon.tv.ui.Chip
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.FocusParking
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.Rows
import cloud.rawkoon.tv.ui.Section
import cloud.rawkoon.tv.ui.TitleBlock
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.backdropRequest
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.fill
import cloud.rawkoon.tv.ui.label
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A cast member's page, laid out like a title's: their photo, name, birth, age and birthplace and
 * biography (what Jellyfin keeps from TMDB), then their films and shows in the library, newest
 * first. [byId] when [person] is a Jellyfin person; a cast member from TMDB (on a Discover page)
 * is looked up by name instead.
 */
class PersonScreen(activity: MainActivity, session: JellyfinSession, private val person: Person, private val byId: Boolean, active: Destination) :
    RailScreen(activity, session, active) {
    private val page = Rows(context)
    private val block = TitleBlock(context, app.images) { alive }
    private val parking = FocusParking(context)
    private val note = label(context, 14f, Tokens.textMuted, Fonts.medium(context)).apply { visibility = View.GONE }
    private val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(context.resources.configuration.locales[0])
    private var firstSection: Section? = null

    /** Set once Jellyfin's photo is on screen: the card's, still loading, must not land over it. */
    @Volatile
    private var infoPhoto = false

    /** The load ended: with titles, without, or failed. Focus no longer waits on the page. */
    private var loaded = false

    init {
        page.column.setPadding(0, 0, 0, context.dp(200))
        // The first row's anchor is under the block, so focusing it keeps the photo and biography on screen
        page.anchor = TitleBlock.params(context, actions = false).height
        content.addView(page, fill())
        // Not in the page's column: that holds the block and rows only
        content.addView(note, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(Tokens.CONTENT_START)
            topMargin = context.dp(NOTE_TOP)
        })
        content.addView(parking, FrameLayout.LayoutParams(1, 1))
        page.column.addView(block, TitleBlock.params(context, actions = false))
        // The card's photo and name until Jellyfin's details arrive; a TMDB photo is used as it is
        block.setPoster(person.imageUrl, fillWidth = byId, wanted = { !infoPhoto })
        block.bind(person.name, null, emptyList(), emptyList(), "")
        load()
    }

    private fun load() {
        launchLoading(onError = {
            toastPageError()
            leaveParking()
        }) {
            val info = try {
                session.personInfo(person.id.takeIf { byId }, person.name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The titles can still come: the page keeps the card's name and photo
                null
            }
            info?.let(::showInfo)
            val found = session.personTitles(info?.id ?: person.id.takeIf { byId }, person.name)
            found.values.flatten().firstOrNull { it.backdropUrl != null }?.let { backdrop.show(backdropRequest(it.backdropUrl)) }
            if (found.isEmpty()) {
                note.text = string(R.string.person_empty, person.name)
                note.visibility = View.VISIBLE
                leaveParking()
                return@launchLoading
            }
            found[FindGroup.MOVIES]?.let { addSection("movies", R.string.find_row_movies, it) }
            found[FindGroup.SERIES]?.let { addSection("shows", R.string.find_row_shows, it) }
        }
    }

    private fun showInfo(info: PersonInfo) {
        // If Jellyfin's photo fails, the card's stays
        info.imageUrl?.let { block.setPoster(it, fillWidth = true, onShown = { infoPhoto = true }) }
        block.bind(info.name, null, facts(info), emptyList(), info.bio)
    }

    /** "Born June 4, 1975 · 51 years old · Montréal, Québec, Canada", or the death date in place of the age. */
    private fun facts(info: PersonInfo): List<Chip> = buildList {
        info.born?.let { add(Chip.Text(string(R.string.person_born, dates.format(it)))) }
        val died = info.died
        if (died != null) {
            add(Chip.Text(string(R.string.person_died, dates.format(died))))
        } else {
            info.age(LocalDate.now())?.let { add(Chip.Text(context.resources.getQuantityString(R.plurals.person_age, it, it))) }
        }
        info.birthplace?.let { add(Chip.Text(it)) }
    }

    private fun addSection(id: String, titleRes: Int, items: List<MediaItem>) {
        val section = Section(context, id, string(titleRes))
        section.row.setCards(items.map { s -> Card(context, s, false, app.images, {}, { openItem(it.item) }).withMenu() })
        section.row.onLeftEdge = { rail.activeItem() }
        page.column.addView(section)
        if (firstSection == null) {
            firstSection = section
            loaded = true
            if (isFullyShown) section.row.arrive()
            // Focus waited on the page while it loaded: the first card takes it
            if (view.findFocus() == null || parking.isFocused) section.row.restoreFocus()
            parking.release()
        }
    }

    /** Nothing to focus on the page after all: focus held while it loaded goes to the rail. */
    private fun leaveParking() {
        loaded = true
        if (parking.isFocused) rail.activeItem()?.requestFocus()
        parking.release()
    }

    override fun contentFocus(): View? = super.contentFocus() ?: firstSection?.row?.remembered()

    /** Still loading, focus waits on the page instead of falling to the rail and opening it. */
    override fun focusDefault() {
        if (firstSection?.row?.restoreFocus() == true) return
        if (loaded) rail.activeItem()?.requestFocus() else parking.hold()
    }

    private companion object {
        // Under the photo, where the first row would be
        const val NOTE_TOP = 270
    }
}

package cloud.rawkoon.tv.live

import java.util.UUID
import org.jellyfin.sdk.model.api.LibraryUpdateInfo
import org.jellyfin.sdk.model.api.UserDataChangeInfo

/**
 * What a server pushed that a page may want to show: Jellyfin's user data or library, or Rawkoon's
 * news of a title that finished importing or of a request that changed.
 */
enum class LiveKind(val fromRawkoon: Boolean = false) {
    USER_DATA,
    LIBRARY,
    RAWKOON_LANDED(fromRawkoon = true),
    REQUEST_CHANGED(fromRawkoon = true),
    ;

    companion object {
        val JELLYFIN: Set<LiveKind> = entries.filterNot { it.fromRawkoon }.toSet()
    }
}

/** The kinds of page that react to live events; every other screen reloads only when it is shown. */
enum class LivePage {
    HOME,
    DETAILS,
    SEASON,
    FAVORITES,
    LIBRARY,

    /** Discover's rows: kept as they are (a reload would drop a search), but Rawkoon's toasts still reach it. */
    DISCOVER,

    /** A TMDB title's page: its request state and its Jellyfin item follow the server. */
    DISCOVER_DETAILS,
    OTHER,
    ;

    /** Pages list what they show, so an addition or removal matters to them; a favorites list only follows the user's own data. */
    fun reactsTo(kind: LiveKind): Boolean = when (this) {
        HOME -> true
        DETAILS, SEASON, LIBRARY -> !kind.fromRawkoon
        FAVORITES -> kind == LiveKind.USER_DATA
        // Not the user's watched state: the page shows neither
        DISCOVER_DETAILS -> kind != LiveKind.USER_DATA
        DISCOVER, OTHER -> false
    }

    /** Whether the Jellyfin socket is worth keeping open for this page. */
    val followsJellyfin: Boolean get() = LiveKind.JELLYFIN.any(::reactsTo)

    /** Whether Rawkoon's streams stay open: every page but the player and the like, so a toast reaches any of them. */
    val followsRawkoon: Boolean get() = this != OTHER

    fun reactsToAny(kinds: Set<LiveKind>): Boolean = kinds.any(::reactsTo)
}

/** Other users' changes can reach the socket too; an empty or missing payload carries nothing to show. */
fun UserDataChangeInfo?.concerns(user: UUID): Boolean = this != null && userId == user && userDataList.isNotEmpty()

/** A scan can announce a change with nothing in it. */
fun LibraryUpdateInfo?.concernsLibrary(): Boolean = this != null &&
    (itemsAdded.isNotEmpty() || itemsRemoved.isNotEmpty() || itemsUpdated.isNotEmpty() || foldersAddedTo.isNotEmpty() || foldersRemovedFrom.isNotEmpty())

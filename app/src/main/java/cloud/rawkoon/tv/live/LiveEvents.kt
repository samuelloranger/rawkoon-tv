package cloud.rawkoon.tv.live

import java.util.UUID
import org.jellyfin.sdk.model.api.LibraryUpdateInfo
import org.jellyfin.sdk.model.api.UserDataChangeInfo

/** What the server pushed that a page may want to show: the user's own data, or the library itself. */
enum class LiveKind { USER_DATA, LIBRARY }

/** The kinds of page that react to live events; every other screen reloads only when it is shown. */
enum class LivePage {
    HOME,
    DETAILS,
    SEASON,
    FAVORITES,
    LIBRARY,
    OTHER,
    ;

    /** Pages list what they show, so an addition or removal matters to them; a favorites list only follows the user's own data. */
    fun reactsTo(kind: LiveKind): Boolean = when (this) {
        HOME, DETAILS, SEASON, LIBRARY -> true
        FAVORITES -> kind == LiveKind.USER_DATA
        OTHER -> false
    }

    fun reactsToAny(kinds: Set<LiveKind>): Boolean = kinds.any(::reactsTo)
}

/** Other users' changes can reach the socket too; an empty or missing payload carries nothing to show. */
fun UserDataChangeInfo?.concerns(user: UUID): Boolean = this != null && userId == user && userDataList.isNotEmpty()

/** A scan can announce a change with nothing in it. */
fun LibraryUpdateInfo?.concernsLibrary(): Boolean = this != null &&
    (itemsAdded.isNotEmpty() || itemsRemoved.isNotEmpty() || itemsUpdated.isNotEmpty() || foldersAddedTo.isNotEmpty() || foldersRemovedFrom.isNotEmpty())

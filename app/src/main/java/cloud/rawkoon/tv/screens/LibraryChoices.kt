package cloud.rawkoon.tv.screens

import android.content.Context
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.LibraryFilter
import cloud.rawkoon.tv.jellyfin.LibraryQuery
import cloud.rawkoon.tv.jellyfin.LibrarySort
import cloud.rawkoon.tv.ui.ChoiceDialog

fun sortLabel(sort: LibrarySort) = when (sort) {
    LibrarySort.ADDED -> R.string.library_sort_added
    LibrarySort.TITLE -> R.string.library_sort_title
    LibrarySort.YEAR -> R.string.library_sort_year
    LibrarySort.RATING -> R.string.library_sort_rating
}

fun filterLabel(filter: LibraryFilter) = when (filter) {
    LibraryFilter.ALL -> R.string.library_filter_all
    LibraryFilter.UNWATCHED -> R.string.library_filter_unwatched
    LibraryFilter.FAVORITES -> R.string.library_filter_favorites
}

/** The sort choices: each order, and reversing it. [apply] takes the query a choice leads to. */
fun sortDialog(context: Context, query: LibraryQuery, apply: (LibraryQuery) -> Unit): ChoiceDialog {
    val choices = LibrarySort.entries.map { sort -> context.getString(sortLabel(sort)) to { apply(query.copy(sort = sort)) } } +
        (context.getString(if (query.reverse) R.string.library_reverse_on else R.string.library_reverse_off) to { apply(query.copy(reverse = !query.reverse)) })
    return ChoiceDialog(context, context.getString(R.string.library_sort_dialog), choices)
}

/** The filter choices. */
fun filterDialog(context: Context, query: LibraryQuery, apply: (LibraryQuery) -> Unit): ChoiceDialog {
    val choices = LibraryFilter.entries.map { filter -> context.getString(filterLabel(filter)) to { apply(query.copy(filter = filter)) } }
    return ChoiceDialog(context, context.getString(R.string.library_filter_dialog), choices)
}

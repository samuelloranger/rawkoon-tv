package com.github.damontecres.wholphin.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Home rows fed by Rawkoon. They live in this package because the subtypes of [HomeRowConfig] must; the code that
 * fetches and shows them is in the rawkoon package.
 */
@Serializable
sealed interface RawkoonHomeRow : HomeRowConfig

/** Titles about to be released, from Rawkoon's dashboard */
@Serializable
@SerialName("RawkoonUpcoming")
data class RawkoonUpcomingRow(
    override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
) : RawkoonHomeRow {
    override fun updateViewOptions(viewOptions: HomeRowViewOptions): RawkoonUpcomingRow = copy(viewOptions = viewOptions)
}

/** The signed-in user's own requests */
@Serializable
@SerialName("RawkoonRequests")
data class RawkoonRequestsRow(
    override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
) : RawkoonHomeRow {
    override fun updateViewOptions(viewOptions: HomeRowViewOptions): RawkoonRequestsRow = copy(viewOptions = viewOptions)
}

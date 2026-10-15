package com.github.damontecres.wholphin.rawkoon

import com.github.damontecres.wholphin.preferences.AppThemeColors

/**
 * The theme picker lists the themes by position, and Rawkoon's enum number is far from its position, so map them
 */
object RawkoonTheme {
    /** Position of Rawkoon after the upstream themes, whose numbers are their positions */
    const val PICKER_INDEX = 8

    fun fromIndex(index: Int): AppThemeColors =
        if (index == PICKER_INDEX) AppThemeColors.RAWKOON else AppThemeColors.forNumber(index) ?: AppThemeColors.RAWKOON

    fun toIndex(theme: AppThemeColors): Int =
        when (theme) {
            AppThemeColors.RAWKOON -> PICKER_INDEX
            AppThemeColors.UNRECOGNIZED -> 0
            else -> theme.number
        }
}

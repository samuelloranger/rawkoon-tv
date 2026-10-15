package com.github.damontecres.wholphin.rawkoon

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.github.damontecres.wholphin.ui.theme.ThemeColors

val RawkoonThemeColors =
    object : ThemeColors {
        val primaryLight = Color(0xFFAD5440)
        val onPrimaryLight = Color(0xFFFFFFFF)
        val primaryContainerLight = Color(0xFFF9E1D0)
        val onPrimaryContainerLight = Color(0xFF5E2418)
        val secondaryLight = Color(0xFFA24A33)
        val onSecondaryLight = Color(0xFFFFFFFF)
        val secondaryContainerLight = Color(0xFFFBDDD2)
        val onSecondaryContainerLight = Color(0xFF5A2616)
        val tertiaryLight = Color(0xFF8A5A2B)
        val onTertiaryLight = Color(0xFFFFFFFF)
        val tertiaryContainerLight = Color(0xFFF7E0C8)
        val onTertiaryContainerLight = Color(0xFF4A2E10)
        val errorLight = Color(0xFFBA1A1A)
        val onErrorLight = Color(0xFFFFFFFF)
        val errorContainerLight = Color(0xFFFFDAD6)
        val onErrorContainerLight = Color(0xFF93000A)
        val backgroundLight = Color(0xFFFDF5EF)
        val onBackgroundLight = Color(0xFF2A1F19)
        val surfaceLight = Color(0xFFFDF5EF)
        val onSurfaceLight = Color(0xFF2A1F19)
        val surfaceVariantLight = Color(0xFFF1E3DA)
        val onSurfaceVariantLight = Color(0xFF5A4A40)
        val outlineLight = Color(0xFF8C7A6D)
        val outlineVariantLight = Color(0xFFDCCBBF)
        val scrimLight = Color(0xFF000000)
        val inverseSurfaceLight = Color(0xFF382F29)
        val inverseOnSurfaceLight = Color(0xFFFEEDE4)
        val inversePrimaryLight = Color(0xFFE8A06A)
        val surfaceDimLight = Color(0xFFE5D8CE)
        val surfaceBrightLight = Color(0xFFFDF5EF)
        val surfaceContainerLowestLight = Color(0xFFFFFFFF)
        val surfaceContainerLowLight = Color(0xFFFBEFE7)
        val surfaceContainerLight = Color(0xFFF7EBE2)
        val surfaceContainerHighLight = Color(0xFFF1E5DB)
        val surfaceContainerHighestLight = Color(0xFFEBDFD5)

        val primaryDark = Color(0xFFE8A06A)
        val onPrimaryDark = Color(0xFF1C1715)
        val primaryContainerDark = Color(0xFFAD5440)
        val onPrimaryContainerDark = Color(0xFFF9E6D5)
        val secondaryDark = Color(0xFFCF6A4E)
        val onSecondaryDark = Color(0xFF1C1715)
        val secondaryContainerDark = Color(0xFF5A2F25)
        val onSecondaryContainerDark = Color(0xFFF9D9CC)
        val tertiaryDark = Color(0xFFF0BF93)
        val onTertiaryDark = Color(0xFF1C1715)
        val tertiaryContainerDark = Color(0xFF5C4228)
        val onTertiaryContainerDark = Color(0xFFF9E3CC)
        val errorDark = Color(0xFFFFB4AB)
        val onErrorDark = Color(0xFF690005)
        val errorContainerDark = Color(0xFF93000A)
        val onErrorContainerDark = Color(0xFFFFDAD6)
        val backgroundDark = Color(0xFF1C1715)
        val onBackgroundDark = Color(0xFFF4ECE4)
        val surfaceDark = Color(0xFF1C1715)
        val onSurfaceDark = Color(0xFFF4ECE4)
        val surfaceVariantDark = Color(0xFF2B2420)
        val onSurfaceVariantDark = Color(0xFFAA9A8C)
        val outlineDark = Color(0xFF463A32)
        val outlineVariantDark = Color(0xFF322A25)
        val scrimDark = Color(0xFF000000)
        val inverseSurfaceDark = Color(0xFFF4ECE4)
        val inverseOnSurfaceDark = Color(0xFF1C1715)
        val inversePrimaryDark = Color(0xFFAD5440)
        val surfaceDimDark = Color(0xFF171311)
        val surfaceBrightDark = Color(0xFF322A25)
        val surfaceContainerLowestDark = Color(0xFF171311)
        val surfaceContainerLowDark = Color(0xFF201A17)
        val surfaceContainerDark = Color(0xFF241E1B)
        val surfaceContainerHighDark = Color(0xFF2B2420)
        val surfaceContainerHighestDark = Color(0xFF322A25)

        override val lightSchemeMaterial: ColorScheme =
            androidx.compose.material3.lightColorScheme(
                primary = primaryLight,
                onPrimary = onPrimaryLight,
                primaryContainer = primaryContainerLight,
                onPrimaryContainer = onPrimaryContainerLight,
                secondary = secondaryLight,
                onSecondary = onSecondaryLight,
                secondaryContainer = secondaryContainerLight,
                onSecondaryContainer = onSecondaryContainerLight,
                tertiary = tertiaryLight,
                onTertiary = onTertiaryLight,
                tertiaryContainer = tertiaryContainerLight,
                onTertiaryContainer = onTertiaryContainerLight,
                error = errorLight,
                onError = onErrorLight,
                errorContainer = errorContainerLight,
                onErrorContainer = onErrorContainerLight,
                background = backgroundLight,
                onBackground = onBackgroundLight,
                surface = surfaceLight,
                onSurface = onSurfaceLight,
                surfaceVariant = surfaceVariantLight,
                onSurfaceVariant = onSurfaceVariantLight,
                scrim = scrimLight,
                inverseSurface = inverseSurfaceLight,
                inverseOnSurface = inverseOnSurfaceLight,
                inversePrimary = inversePrimaryLight,
                surfaceDim = surfaceDimLight,
                surfaceBright = surfaceBrightLight,
                surfaceContainerLowest = surfaceContainerLowestLight,
                surfaceContainerLow = surfaceContainerLowLight,
                surfaceContainer = surfaceContainerLight,
                surfaceContainerHigh = surfaceContainerHighLight,
                surfaceContainerHighest = surfaceContainerHighestLight,
                outline = outlineLight,
                outlineVariant = outlineVariantLight,
            )

        override val lightScheme =
            lightColorScheme(
                primary = primaryLight,
                onPrimary = onPrimaryLight,
                primaryContainer = primaryContainerLight,
                onPrimaryContainer = onPrimaryContainerLight,
                secondary = secondaryLight,
                onSecondary = onSecondaryLight,
                secondaryContainer = secondaryContainerLight,
                onSecondaryContainer = onSecondaryContainerLight,
                tertiary = tertiaryLight,
                onTertiary = onTertiaryLight,
                tertiaryContainer = tertiaryContainerLight,
                onTertiaryContainer = onTertiaryContainerLight,
                error = errorLight,
                onError = onErrorLight,
                errorContainer = errorContainerLight,
                onErrorContainer = onErrorContainerLight,
                background = backgroundLight,
                onBackground = onBackgroundLight,
                surface = surfaceLight,
                onSurface = onSurfaceLight,
                surfaceVariant = surfaceVariantLight,
                onSurfaceVariant = onSurfaceVariantLight,
                scrim = scrimLight,
                inverseSurface = inverseSurfaceLight,
                inverseOnSurface = inverseOnSurfaceLight,
                inversePrimary = inversePrimaryLight,
                border = primaryLight,
            )

        override val darkSchemeMaterial =
            androidx.compose.material3.darkColorScheme(
                primary = primaryDark,
                onPrimary = onPrimaryDark,
                primaryContainer = primaryContainerDark,
                onPrimaryContainer = onPrimaryContainerDark,
                secondary = secondaryDark,
                onSecondary = onSecondaryDark,
                secondaryContainer = secondaryContainerDark,
                onSecondaryContainer = onSecondaryContainerDark,
                tertiary = tertiaryDark,
                onTertiary = onTertiaryDark,
                tertiaryContainer = tertiaryContainerDark,
                onTertiaryContainer = onTertiaryContainerDark,
                error = errorDark,
                onError = onErrorDark,
                errorContainer = errorContainerDark,
                onErrorContainer = onErrorContainerDark,
                background = backgroundDark,
                onBackground = onBackgroundDark,
                surface = surfaceDark,
                onSurface = onSurfaceDark,
                surfaceVariant = surfaceVariantDark,
                onSurfaceVariant = onSurfaceVariantDark,
                scrim = scrimDark,
                inverseSurface = inverseSurfaceDark,
                inverseOnSurface = inverseOnSurfaceDark,
                inversePrimary = inversePrimaryDark,
                surfaceDim = surfaceDimDark,
                surfaceBright = surfaceBrightDark,
                surfaceContainerLowest = surfaceContainerLowestDark,
                surfaceContainerLow = surfaceContainerLowDark,
                surfaceContainer = surfaceContainerDark,
                surfaceContainerHigh = surfaceContainerHighDark,
                surfaceContainerHighest = surfaceContainerHighestDark,
                outline = outlineDark,
                outlineVariant = outlineVariantDark,
            )

        override val darkScheme =
            darkColorScheme(
                primary = primaryDark,
                onPrimary = onPrimaryDark,
                primaryContainer = primaryContainerDark,
                onPrimaryContainer = onPrimaryContainerDark,
                secondary = secondaryDark,
                onSecondary = onSecondaryDark,
                secondaryContainer = secondaryContainerDark,
                onSecondaryContainer = onSecondaryContainerDark,
                tertiary = tertiaryDark,
                onTertiary = onTertiaryDark,
                tertiaryContainer = tertiaryContainerDark,
                onTertiaryContainer = onTertiaryContainerDark,
                error = errorDark,
                onError = onErrorDark,
                errorContainer = errorContainerDark,
                onErrorContainer = onErrorContainerDark,
                background = backgroundDark,
                onBackground = onBackgroundDark,
                surface = surfaceDark,
                onSurface = onSurfaceDark,
                surfaceVariant = surfaceVariantDark,
                onSurfaceVariant = onSurfaceVariantDark,
                scrim = scrimDark,
                inverseSurface = inverseSurfaceDark,
                inverseOnSurface = inverseOnSurfaceDark,
                inversePrimary = inversePrimaryDark,
                border = primaryDark,
            )
    }

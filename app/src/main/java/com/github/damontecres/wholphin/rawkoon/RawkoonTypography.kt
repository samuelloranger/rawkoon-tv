package com.github.damontecres.wholphin.rawkoon

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Typography
import com.github.damontecres.wholphin.R

private val Fraunces =
    FontFamily(
        Font(R.font.rawkoon_fraunces_regular, FontWeight.Normal),
        Font(R.font.rawkoon_fraunces_semibold, FontWeight.SemiBold),
        Font(R.font.rawkoon_fraunces_bold, FontWeight.Bold),
    )

private val HankenGrotesk =
    FontFamily(
        Font(R.font.rawkoon_hanken_regular, FontWeight.Normal),
        Font(R.font.rawkoon_hanken_medium, FontWeight.Medium),
        Font(R.font.rawkoon_hanken_semibold, FontWeight.SemiBold),
        Font(R.font.rawkoon_hanken_bold, FontWeight.Bold),
    )

/**
 * The Rawkoon theme's type: Fraunces SemiBold for display, headline and the larger titles, Hanken Grotesk for the rest.
 * Sizes and line heights are Wholphin's. A glyph a font lacks falls back to the system font, which Compose does per glyph.
 */
val RawkoonTypography: Typography =
    Typography().let { base ->
        base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
            displayMedium = base.displayMedium.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
            displaySmall = base.displaySmall.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
            headlineLarge = base.headlineLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
            headlineMedium = base.headlineMedium.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
            headlineSmall = base.headlineSmall.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
            titleLarge = base.titleLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
            titleMedium = base.titleMedium.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
            // Too small for a display serif to stay crisp on a TV
            titleSmall = base.titleSmall.copy(fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold),
            bodyLarge = base.bodyLarge.copy(fontFamily = HankenGrotesk),
            bodyMedium = base.bodyMedium.copy(fontFamily = HankenGrotesk),
            bodySmall = base.bodySmall.copy(fontFamily = HankenGrotesk),
            labelLarge = base.labelLarge.copy(fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium),
            labelMedium = base.labelMedium.copy(fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium),
            labelSmall = base.labelSmall.copy(fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium),
        )
    }

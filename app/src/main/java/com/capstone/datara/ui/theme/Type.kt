package com.capstone.datara.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.capstone.datara.R

/**
 * Brand font: Plus Jakarta Sans (SIL OFL 1.1, licence in assets/licenses/), bundled as the
 * official variable TTF from google/fonts so it works offline. Used for display, headline,
 * title and label styles; body text stays on the system sans for long-form readability.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun jakarta(weight: FontWeight) = Font(R.font.plus_jakarta_sans, weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val DataraFontFamily = FontFamily(
    jakarta(FontWeight.Normal), jakarta(FontWeight.Medium), jakarta(FontWeight.SemiBold),
    jakarta(FontWeight.Bold), jakarta(FontWeight.ExtraBold))

val DataraBodyFontFamily = FontFamily.SansSerif

/** Tabular digits so changing numbers ("29.98 GB") keep their width. */
const val TabularNumbers = "tnum"

private fun brand(weight: FontWeight, size: Int, line: Int, tracking: TextUnit = 0.sp) = TextStyle(
    fontFamily = DataraFontFamily, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = tracking, fontFeatureSettings = TabularNumbers)

private fun body(weight: FontWeight, size: Int, line: Int, tracking: TextUnit) = TextStyle(
    fontFamily = DataraBodyFontFamily, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = tracking, fontFeatureSettings = TabularNumbers)

val Typography = Typography(
    displayLarge = brand(FontWeight.ExtraBold, 44, 50, (-1.0).sp),
    displayMedium = brand(FontWeight.ExtraBold, 38, 44, (-0.8).sp),
    displaySmall = brand(FontWeight.Bold, 32, 38, (-0.6).sp),
    headlineLarge = brand(FontWeight.Bold, 30, 36, (-0.6).sp),
    headlineMedium = brand(FontWeight.Bold, 24, 32, (-0.4).sp),
    headlineSmall = brand(FontWeight.Bold, 22, 28, (-0.2).sp),
    titleLarge = brand(FontWeight.Bold, 20, 28, (-0.2).sp),
    titleMedium = brand(FontWeight.SemiBold, 16, 24, 0.1.sp),
    titleSmall = brand(FontWeight.SemiBold, 14, 20, 0.1.sp),
    bodyLarge = body(FontWeight.Normal, 16, 24, 0.2.sp),
    bodyMedium = body(FontWeight.Normal, 14, 20, 0.2.sp),
    bodySmall = body(FontWeight.Normal, 12, 16, 0.3.sp),
    labelLarge = brand(FontWeight.SemiBold, 14, 20, 0.1.sp),
    labelMedium = brand(FontWeight.SemiBold, 12, 16, 0.3.sp),
    labelSmall = brand(FontWeight.SemiBold, 11, 16, 0.4.sp),
)

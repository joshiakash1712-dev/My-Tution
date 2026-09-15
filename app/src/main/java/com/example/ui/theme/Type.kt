package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Relative Units Utility for Jetpack Compose.
 * Maps CSS-like relative typography sizing directly to Jetpack Compose TextUnit.
 * This ensures that when the base size is scaled or dynamic context is updated,
 * the entire application UI scales proportionally to prevent text clipping and truncation on small devices.
 */
object RelativeUnits {
  // Base font size (1rem) defaults to 14.sp (optimized for high-density, compact mobile screens)
  var baseFontSize: TextUnit = 14.sp

  fun updateBaseSize(newBase: TextUnit) {
    baseFontSize = newBase
  }
}

// Relative units: rem (relative to base root font size)
val Number.rem: TextUnit
  get() = (RelativeUnits.baseFontSize.value * this.toDouble()).sp

// Relative units: em (proportional context size)
val Number.em: TextUnit
  get() = (RelativeUnits.baseFontSize.value * this.toDouble()).sp


// Set of Material typography styles to start with
val Typography =
  Typography(
    displayLarge = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Bold,
      fontSize = 2.2.rem,
      lineHeight = 2.8.rem,
      letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Bold,
      fontSize = 1.9.rem,
      lineHeight = 2.4.rem,
      letterSpacing = (-0.25).sp
    ),
    displaySmall = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.SemiBold,
      fontSize = 1.6.rem,
      lineHeight = 2.1.rem,
      letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Bold,
      fontSize = 1.45.rem,
      lineHeight = 1.9.rem,
      letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.SemiBold,
      fontSize = 1.3.rem,
      lineHeight = 1.75.rem,
      letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.SemiBold,
      fontSize = 1.15.rem,
      lineHeight = 1.55.rem,
      letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Bold,
      fontSize = 1.15.rem,
      lineHeight = 1.5.rem,
      letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.SemiBold,
      fontSize = 1.0.rem,
      lineHeight = 1.35.rem,
      letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Medium,
      fontSize = 0.9.rem,
      lineHeight = 1.25.rem,
      letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Normal,
      fontSize = 1.0.rem,
      lineHeight = 1.4.rem,
      letterSpacing = 0.25.sp
    ),
    bodyMedium = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Normal,
      fontSize = 0.9.rem,
      lineHeight = 1.3.rem,
      letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Normal,
      fontSize = 0.8.rem,
      lineHeight = 1.15.rem,
      letterSpacing = 0.3.sp
    ),
    labelLarge = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.SemiBold,
      fontSize = 0.85.rem,
      lineHeight = 1.2.rem,
      letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Medium,
      fontSize = 0.75.rem,
      lineHeight = 1.05.rem,
      letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Medium,
      fontSize = 0.7.rem,
      lineHeight = 0.95.rem,
      letterSpacing = 0.4.sp
    )
  )

/**
 * 4-Tier Typographic Scale for Numerical & Quantitative Data:
 *
 * Tier 1 (Hero Stat):
 *   Display/headline KPI figures (e.g. ₹9.60 L, 154 enrolled, 94% institute attendance, 92.4% overall score)
 *   Size: 24.sp, FontWeight.Bold, tight letterSpacing (-0.3.sp), lineHeight: 28.sp
 *
 * Tier 2 (Card-Level Stat):
 *   Primary metric tile / card figures (e.g. ₹73,600 collected, 18/20 students, 86%, ₹12,800 overdue)
 *   Size: 20.sp, FontWeight.Bold, letterSpacing (-0.2.sp), lineHeight: 24.sp
 *
 * Tier 3 (Inline Numerical Stat):
 *   List-row metrics, secondary card stats, timetable slots, subject marks, table amounts (e.g. ₹6,400, 92.5%, 45 mins, 24 Enrolled)
 *   Size: 13.sp, FontWeight.SemiBold, letterSpacing (0.sp), lineHeight: 17.sp
 *
 * Tier 4 (Micro-Label / Telemetry Badge / Axis):
 *   Chart axes, trend deltas (+4.2%), micro status counts, timestamps, sub-labels (e.g. 100% Present, ₹12k axis, 10/12/2026)
 *   Size: 11.sp, FontWeight.Medium, letterSpacing (+0.1.sp), lineHeight: 14.sp
 */
object NumberTypography {
  val heroStat = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.3).sp
  )

  val cardStat = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 24.sp,
    letterSpacing = (-0.2).sp
  )

  val inlineStat = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
    lineHeight = 17.sp,
    letterSpacing = 0.sp
  )

  val microLabel = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.1.sp
  )
}

@androidx.compose.runtime.Composable
fun HeroStatText(
  text: String,
  modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
  color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
  maxLines: Int = 1,
  overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
) {
  val responsiveFontSize = when {
    text.length >= 11 -> 18.sp
    text.length >= 8 -> 20.sp
    else -> 24.sp
  }
  androidx.compose.material3.Text(
    text = text,
    modifier = modifier,
    style = NumberTypography.heroStat.copy(
      fontSize = responsiveFontSize,
      letterSpacing = (-0.4).sp
    ),
    color = color,
    maxLines = maxLines,
    overflow = overflow,
    softWrap = false
  )
}

@androidx.compose.runtime.Composable
fun CardStatText(
  text: String,
  modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
  color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
  maxLines: Int = 1,
  overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
) {
  // Dynamically scale font size based on string length to guarantee full values display without truncation
  // e.g. "₹73,600" (7 chars) -> 15.sp, "₹1,12,000" (9 chars) -> 13.5.sp
  val responsiveFontSize = when {
    text.length >= 10 -> 13.sp
    text.length >= 8 -> 14.sp
    text.length >= 6 -> 15.sp
    else -> 18.sp
  }
  androidx.compose.material3.Text(
    text = text,
    modifier = modifier,
    style = NumberTypography.cardStat.copy(
      fontSize = responsiveFontSize,
      letterSpacing = (-0.4).sp
    ),
    color = color,
    maxLines = maxLines,
    overflow = overflow,
    softWrap = false
  )
}

@androidx.compose.runtime.Composable
fun InlineStatText(
  text: String,
  modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
  color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
  maxLines: Int = 1,
  overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
) {
  androidx.compose.material3.Text(
    text = text,
    modifier = modifier,
    style = NumberTypography.inlineStat.copy(
      letterSpacing = (-0.2).sp
    ),
    color = color,
    maxLines = maxLines,
    overflow = overflow,
    softWrap = false
  )
}

@androidx.compose.runtime.Composable
fun MicroLabelText(
  text: String,
  modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
  color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
  maxLines: Int = 1,
  overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
) {
  androidx.compose.material3.Text(
    text = text,
    modifier = modifier,
    style = NumberTypography.microLabel,
    color = color,
    maxLines = maxLines,
    overflow = overflow,
    softWrap = false
  )
}


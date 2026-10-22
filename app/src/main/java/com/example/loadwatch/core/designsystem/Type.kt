package com.example.loadwatch.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// A single sans-serif family at four sizes (design doc 4.1). The countdown is the largest element on Home
// because it carries the most information.
private val LoadWatchFont = FontFamily.SansSerif

private val Countdown = TextStyle(
    fontFamily = LoadWatchFont,
    fontWeight = FontWeight.Bold,
    fontSize = 48.sp,
    lineHeight = 56.sp,
    letterSpacing = 0.sp,
)

private val Title = TextStyle(
    fontFamily = LoadWatchFont,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 28.sp,
    letterSpacing = 0.sp,
)

private val Body = TextStyle(
    fontFamily = LoadWatchFont,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.15.sp,
)

private val Label = TextStyle(
    fontFamily = LoadWatchFont,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.5.sp,
)

// Every Material slot maps onto one of the four sizes
val LoadWatchTypography = Typography(
    displayLarge = Countdown,
    displayMedium = Countdown,
    displaySmall = Countdown,
    headlineLarge = Title,
    headlineMedium = Title,
    headlineSmall = Title,
    titleLarge = Title,
    titleMedium = Title,
    titleSmall = Title,
    bodyLarge = Body,
    bodyMedium = Body,
    bodySmall = Body,
    labelLarge = Label.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = Label,
    labelSmall = Label,
)

package com.example.loadwatch.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Supply-state colours sit outside the Material scheme, so they are provided alongside it.
// Colour is never the only carrier of meaning: every state is also labelled in words (NFR-07).
@Immutable
data class StatusColors(
    val supplyOn: Color,
    val onSupplyOn: Color,
    val supplyOnContainer: Color,
    val onSupplyOnContainer: Color,
    val supplyOff: Color,
    val onSupplyOff: Color,
    val supplyOffContainer: Color,
    val onSupplyOffContainer: Color,
)

internal val DarkStatusColors = StatusColors(
    supplyOn = Green400,
    onSupplyOn = Slate950,
    supplyOnContainer = Green900,
    onSupplyOnContainer = Green100,
    supplyOff = Red400,
    onSupplyOff = Slate950,
    supplyOffContainer = Red900,
    onSupplyOffContainer = Red100,
)

internal val LightStatusColors = StatusColors(
    supplyOn = Green700,
    onSupplyOn = Color.White,
    supplyOnContainer = Green100,
    onSupplyOnContainer = Green900,
    supplyOff = Red700,
    onSupplyOff = Color.White,
    supplyOffContainer = Red100,
    onSupplyOffContainer = Red900,
)

val LocalStatusColors = staticCompositionLocalOf { DarkStatusColors }

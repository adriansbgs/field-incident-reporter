package com.incidentreporter.field.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FieldLightColors = lightColorScheme(
    primary = Color(0xFF1B5E8C),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAF9),
    onBackground = Color(0xFF1C1B1A),
    surface = Color(0xFFFAFAF9),
    onSurface = Color(0xFF1C1B1A),
    onSurfaceVariant = Color(0xFF5F5D5A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    outline = Color(0xFF8A867F),
    outlineVariant = Color(0xFFE9E7E4),
    error = Color(0xFF8A1E22),

    primaryContainer = Color(0xFF1B5E8C),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF1B5E8C),
    onSecondaryContainer = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDEAE6),
    surfaceContainerLow = Color(0xFFF5F4F2),
    surfaceContainer = Color(0xFFF1EFEC),
    surfaceContainerHigh = Color(0xFFEDEAE6),
    surfaceContainerHighest = Color(0xFFE9E7E4),
)

@Composable
fun FieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FieldLightColors, content = content)
}

data class ChipColors(val container: Color, val label: Color)

/** Colors with no Material 3 role. */
object FieldPalette {
    val severityLow = ChipColors(container = Color(0xFFDDEBDE), label = Color(0xFF2F5A36))
    val severityMedium = ChipColors(container = Color(0xFFFAF2CE), label = Color(0xFF6B5410))
    val severityHigh = ChipColors(container = Color(0xFFFBE4D3), label = Color(0xFF8A4514))
    val severityCritical = ChipColors(container = Color(0xFFFADBD9), label = Color(0xFF8A1E22))

    val syncWaiting = Color(0xFF6B4B10)   // Pending badge
    val syncNeedsYou = Color(0xFF8A1E22)  // Failed / Changed on server badges

    val bannerContainer = Color(0xFFE6EDF2)
    val bannerText = Color(0xFF163F58)
    val bannerIcon = Color(0xFF194A66)

    // Camera chrome, drawn over the live viewfinder
    val cameraTopScrim = Color(0x8C000000)      // 55% black
    val cameraBottomScrim = Color(0x94000000)   // 58% black
    val cameraPill = Color(0x9E000000)          // 62% black: the hint pill, and buttons over a photo
    val cameraHintText = Color(0xFFF2F0ED)
    val cameraControlFill = Color(0x1AFFFFFF)   // 10% white
    val cameraControlStroke = Color(0x6BFFFFFF) // 42% white
    val cameraShutterRing = Color(0xEBFFFFFF)   // 92% white
    val cameraOnWhite = Color(0xFF141617)       // text on a white camera button
}

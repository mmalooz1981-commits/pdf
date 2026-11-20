package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = CrimsonPrimaryLight,
    onPrimary = OnCrimsonPrimaryLight,
    primaryContainer = CrimsonContainerLight,
    onPrimaryContainer = OnCrimsonContainerLight,
    secondary = AlmondGoldSecondaryLight,
    onSecondary = OnAlmondGoldSecondaryLight,
    secondaryContainer = AlmondGoldContainerLight,
    onSecondaryContainer = OnAlmondGoldContainerLight,
    tertiary = CedarSageTertiaryLight,
    onTertiary = OnCedarSageTertiaryLight,
    tertiaryContainer = CedarSageContainerLight,
    onTertiaryContainer = OnCedarSageContainerLight,
    background = ParchmentBackgroundLight,
    onBackground = EspressoOnBackgroundLight,
    surface = ParchmentSurfaceLight,
    onSurface = EspressoOnBackgroundLight,
    surfaceVariant = WarmSurfaceVariantLight,
    onSurfaceVariant = WarmOnSurfaceVariantLight,
    outline = WarmOutlineLight
)

private val DarkColorScheme = darkColorScheme(
    primary = BlossomPrimaryDark,
    onPrimary = OnBlossomPrimaryDark,
    primaryContainer = BlossomContainerDark,
    onPrimaryContainer = OnBlossomContainerDark,
    secondary = AlmondGoldSecondaryDark,
    onSecondary = OnAlmondGoldSecondaryDark,
    secondaryContainer = AlmondGoldContainerDark,
    onSecondaryContainer = OnAlmondGoldContainerDark,
    tertiary = CedarSageTertiaryDark,
    onTertiary = OnCedarSageTertiaryDark,
    tertiaryContainer = CedarSageContainerDark,
    onTertiaryContainer = OnCedarSageContainerDark,
    background = ObsidianBackgroundDark,
    onBackground = IvoryOnBackgroundDark,
    surface = ObsidianSurfaceDark,
    onSurface = IvoryOnBackgroundDark,
    surfaceVariant = ObsidianSurfaceVariantDark,
    onSurfaceVariant = ObsidianOnSurfaceVariantDark,
    outline = ObsidianOutlineDark
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MawidAlLawzTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

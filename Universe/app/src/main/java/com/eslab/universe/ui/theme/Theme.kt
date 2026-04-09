package com.eslab.universe.ui.theme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NightPrimary,
    secondary = NightSecondary,
    tertiary = NightTertiary,
    background = NightBackground,
    surface = NightSurface,
    surfaceVariant = NightSurfaceVariant,
    onPrimary = NightOnPrimary,
    onSurface = NightOnSurface,
    onSurfaceVariant = NightOnSurfaceVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = DayPrimary,
    secondary = DaySecondary,
    tertiary = DayTertiary,
)

@Composable
fun UniverseTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

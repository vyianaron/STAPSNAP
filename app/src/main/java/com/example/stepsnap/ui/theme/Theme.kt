package com.example.stepsnap.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val StepSnapColorScheme = lightColorScheme(
    primary = StepSnapColors.Lime,
    onPrimary = StepSnapColors.Ink,
    secondary = StepSnapColors.ParkGreen,
    onSecondary = StepSnapColors.Card,
    background = StepSnapColors.Card,
    onBackground = StepSnapColors.Ink,
    surface = StepSnapColors.SheetCream,
    onSurface = StepSnapColors.Ink,
    surfaceVariant = StepSnapColors.Card,
    onSurfaceVariant = StepSnapColors.Ink,
    outline = StepSnapColors.LimeDark
)

@Composable
fun StepSnapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StepSnapColorScheme,
        typography = StepSnapTypography,
        content = content
    )
}

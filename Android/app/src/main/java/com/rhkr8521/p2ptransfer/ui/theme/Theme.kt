package com.rhkr8521.p2ptransfer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Pine,
    onPrimary = Fog,
    secondary = Moss,
    onSecondary = Fog,
    tertiary = Clay,
    background = Sand,
    onBackground = Charcoal,
    surface = Fog,
    onSurface = Charcoal,
)

private val DarkColors = darkColorScheme(
    primary = Fog,
    secondary = Sand,
    tertiary = Clay,
)

@Composable
fun Rhkr8521P2PTransferTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}

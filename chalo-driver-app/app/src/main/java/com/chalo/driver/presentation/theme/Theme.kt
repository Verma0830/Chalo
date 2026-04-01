package com.chalo.driver.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary          = ChaloPrimary,
    onPrimary        = ChaloOnPrimary,
    primaryContainer = Color(0xFFFFE0CC),
    onPrimaryContainer = ChaloPrimaryDark,

    secondary        = ChaloSecondary,
    onSecondary      = ChaloOnSecondary,
    secondaryContainer = Color(0xFFE8E8F0),
    onSecondaryContainer = ChaloSecondary,

    background       = ChaloBackground,
    onBackground     = ChaloOnBackground,
    surface          = ChaloSurface,
    onSurface        = ChaloOnSurface,
    surfaceVariant   = Color(0xFFF0F0F0),
    onSurfaceVariant = TextSecondary,

    error            = ChaloError,
    onError          = ChaloOnError,
    errorContainer   = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    outline          = DividerColor,
    outlineVariant   = Color(0xFFEEEEEE),
)

@Composable
fun ChaloDriverTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography  = ChaloTypography,
        content     = content,
    )
}

object ChaloSpacing {
    val xs  = 4.dp
    val sm  = 8.dp
    val md  = 16.dp
    val lg  = 24.dp
    val xl  = 32.dp
    val xxl = 48.dp
}

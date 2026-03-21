package com.chalo.customer.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

// Dark theme — V1 uses light only, but keeping dark ready for V2
private val DarkColorScheme = darkColorScheme(
    primary          = Color(0xFFFFAB76),
    onPrimary        = Color(0xFF4E1900),
    background       = Color(0xFF121212),
    surface          = Color(0xFF1E1E1E),
    onBackground     = Color(0xFFE8E8E8),
    onSurface        = Color(0xFFE8E8E8),
)

@Composable
fun ChaloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // V1: force light theme — dark mode in V2
    val colorScheme = LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = ChaloTypography,
        content     = content,
    )
}

// Spacing constants — use these instead of hardcoded Dp values
object ChaloSpacing {
    val xs  = androidx.compose.ui.unit.dp * 4
    val sm  = androidx.compose.ui.unit.dp * 8
    val md  = androidx.compose.ui.unit.dp * 16
    val lg  = androidx.compose.ui.unit.dp * 24
    val xl  = androidx.compose.ui.unit.dp * 32
    val xxl = androidx.compose.ui.unit.dp * 48
}

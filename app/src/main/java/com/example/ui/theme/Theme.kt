package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = MintBright,
    onPrimary = MintOnBright,
    primaryContainer = MintDarkContainer,
    onPrimaryContainer = MintOnDarkContainer,
    secondary = Color(0xFFA5B4FC),
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF312E81),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = CoralTertiary,
    onTertiary = CoralOnTertiary,
    tertiaryContainer = Color(0xFF6B1E0D),
    onTertiaryContainer = CoralContainer,
    background = DarkBg,
    onBackground = Color(0xFFECFDF5),
    surface = DarkSurface,
    onSurface = Color(0xFFECFDF5),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFA7C4BC),
    outline = Color(0xFF2E4742),
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldPrimaryContainer,
    onPrimaryContainer = EmeraldOnPrimaryContainer,
    secondary = IndigoSecondary,
    onSecondary = IndigoOnSecondary,
    secondaryContainer = IndigoContainer,
    onSecondaryContainer = IndigoOnContainer,
    tertiary = CoralTertiary,
    onTertiary = CoralOnTertiary,
    tertiaryContainer = CoralContainer,
    onTertiaryContainer = CoralOnContainer,
    background = LightBg,
    onBackground = Color(0xFF0D1F1C),
    surface = LightSurface,
    onSurface = Color(0xFF0D1F1C),
    surfaceVariant = Color(0xFFE2EEEC),
    onSurfaceVariant = Color(0xFF445D57),
    outline = Color(0xFFC2D6D1),
)

val ChatMockShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ChatMockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ChatMockTypography,
        shapes = ChatMockShapes,
        content = content
    )
}

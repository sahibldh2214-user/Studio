package com.example.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val VaultDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianCanvas,
    primaryContainer = ElectricCyanContainer,
    onPrimaryContainer = TextPrimary,
    secondary = IndigoAccent,
    onSecondary = TextPrimary,
    secondaryContainer = IndigoContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = AmberVault,
    onTertiary = ObsidianCanvas,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = TextPrimary,
    background = ObsidianCanvas,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = ObsidianCard,
    surfaceContainerHigh = ObsidianCardFocused,
    outline = ObsidianBorder,
    error = CoralAlert,
    onError = Color.White,
    errorContainer = CoralContainer
)

private val VaultLightColorScheme = lightColorScheme(
    primary = DeepCyanPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = DeepIndigoSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    background = CloudWhiteCanvas,
    onBackground = Color(0xFF0F172A),
    surface = CloudWhiteSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = CloudWhiteVariant,
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFE11D48)
)

val VaultShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun isTvDevice(): Boolean {
    val context = LocalContext.current
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

@Composable
fun VaultTvTheme(
    darkTheme: Boolean = true, // Dark cinema-first default for Google TV & mobile media vault
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VaultDarkColorScheme else VaultLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = VaultShapes,
        content = content
    )
}

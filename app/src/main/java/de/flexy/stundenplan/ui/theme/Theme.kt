package de.flexy.stundenplan.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Fallback (ohne Dynamic Color): HKA-Rot als Seed
private val LightColors = lightColorScheme(
    primary = Color(0xFFB3261E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF775651),
    secondaryContainer = Color(0xFFFFDAD5),
    tertiary = Color(0xFF705C2E),
    tertiaryContainer = Color(0xFFFCDFA6),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4AA),
    onPrimary = Color(0xFF690004),
    primaryContainer = Color(0xFF930009),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFE7BDB6),
    secondaryContainer = Color(0xFF5D3F3B),
    tertiary = Color(0xFFDEC48C),
    tertiaryContainer = Color(0xFF574419),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val base = Typography()
val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

@Composable
fun StundenplanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}

/** Farben pro Modul – gleicher Name ergibt immer dieselbe Farbe. */
data class ModuleColors(val container: Color, val onContainer: Color, val accent: Color)

private val HUES = floatArrayOf(4f, 28f, 45f, 88f, 140f, 168f, 192f, 215f, 245f, 275f, 305f, 335f)

@Composable
@ReadOnlyComposable
fun moduleColors(title: String): ModuleColors {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val hue = HUES[Math.floorMod(title.hashCode(), HUES.size)]
    return if (dark) {
        ModuleColors(
            container = Color.hsl(hue, 0.28f, 0.22f),
            onContainer = Color.hsl(hue, 0.55f, 0.90f),
            accent = Color.hsl(hue, 0.65f, 0.70f),
        )
    } else {
        ModuleColors(
            container = Color.hsl(hue, 0.70f, 0.92f),
            onContainer = Color.hsl(hue, 0.60f, 0.16f),
            accent = Color.hsl(hue, 0.60f, 0.45f),
        )
    }
}

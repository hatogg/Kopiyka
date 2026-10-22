package ua.x44to.kopiyka.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import ua.x44to.kopiyka.data.Settings

/** Запасна схема (Android до 12 або вимкнений Material You): зелено-бірюзова, «грошова». */
private val LightFallback = lightColorScheme(
    primary = Color(0xFF006B5B), onPrimary = Color.White,
    primaryContainer = Color(0xFF9FF2DC), onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4B635C), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE8DF), onSecondaryContainer = Color(0xFF06201A),
    tertiary = Color(0xFF426278), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC8E6FF), onTertiaryContainer = Color(0xFF001E2E),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FBF7), onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF5FBF7), onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDBE5E0), onSurfaceVariant = Color(0xFF3F4945),
    outline = Color(0xFF6F7975), outlineVariant = Color(0xFFBFC9C4),
    inverseSurface = Color(0xFF2B3230), inverseOnSurface = Color(0xFFECF2EE), inversePrimary = Color(0xFF83D5C1),
    surfaceDim = Color(0xFFD5DBD8), surfaceBright = Color(0xFFF5FBF7),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEFF5F1), surfaceContainer = Color(0xFFE9EFEB),
    surfaceContainerHigh = Color(0xFFE3EAE6), surfaceContainerHighest = Color(0xFFDEE4E0),
)

private val DarkFallback = darkColorScheme(
    primary = Color(0xFF83D5C1), onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF005144), onPrimaryContainer = Color(0xFF9FF2DC),
    secondary = Color(0xFFB2CCC3), onSecondary = Color(0xFF1D352F),
    secondaryContainer = Color(0xFF334B45), onSecondaryContainer = Color(0xFFCDE8DF),
    tertiary = Color(0xFFAACBE4), onTertiary = Color(0xFF103447),
    tertiaryContainer = Color(0xFF2A4A5F), onTertiaryContainer = Color(0xFFC8E6FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1513), onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF0F1513), onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF3F4945), onSurfaceVariant = Color(0xFFBFC9C4),
    outline = Color(0xFF89938F), outlineVariant = Color(0xFF3F4945),
    inverseSurface = Color(0xFFDEE4E0), inverseOnSurface = Color(0xFF2B3230), inversePrimary = Color(0xFF006B5B),
    surfaceDim = Color(0xFF0F1513), surfaceBright = Color(0xFF353B39),
    surfaceContainerLowest = Color(0xFF0A0F0E), surfaceContainerLow = Color(0xFF171D1B), surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252B2A), surfaceContainerHighest = Color(0xFF303634),
)

@Composable
fun isDark(s: Settings): Boolean = when (s.theme) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

/** Material You: на Android 12+ кольори беруться зі шпалер телефона. */
@Composable
fun KopiykaTheme(s: Settings, content: @Composable () -> Unit) {
    val dark = isDark(s)
    val context = LocalContext.current
    val scheme = when {
        s.dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkFallback
        else -> LightFallback
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Колір доходів — завжди зелений, але читабельний і на світлій, і на темній темі. */
val incomeColor: Color
    @Composable @ReadOnlyComposable
    get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF7BD88F) else Color(0xFF1B7F3A)

val warnColor: Color
    @Composable @ReadOnlyComposable
    get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFFFB74D) else Color(0xFFC66A00)

fun argb(c: Long): Color = Color(c.toInt())

/** Колір категорії, трохи світліший у темній темі, щоб не губився на темному тлі. */
@Composable
@ReadOnlyComposable
fun tint(c: Long): Color {
    val col = argb(c)
    return if (MaterialTheme.colorScheme.surface.luminance() < 0.5f && col.luminance() < 0.25f)
        Color(col.red + (1 - col.red) * 0.35f, col.green + (1 - col.green) * 0.35f, col.blue + (1 - col.blue) * 0.35f)
    else col
}

fun onColor(c: Color): Color = if (c.luminance() > 0.5f) Color(0xFF1B1B1F) else Color.White

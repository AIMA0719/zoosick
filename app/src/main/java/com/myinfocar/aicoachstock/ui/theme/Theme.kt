package com.myinfocar.aicoachstock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = TossBlue,
    onPrimary = Color.White,
    primaryContainer = TossBlueLight,
    onPrimaryContainer = TossBlueDark,
    secondary = TossBlue,
    onSecondary = Color.White,
    background = NeutralBackground,
    onBackground = TextPrimary,
    surface = NeutralSurface,
    onSurface = TextPrimary,
    surfaceVariant = NeutralSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = NeutralOutline,
    outlineVariant = NeutralDivider,
    error = ErrorRed,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = TossBlue,
    onPrimary = Color.White,
    primaryContainer = TossBlueDark,
    onPrimaryContainer = TossBlueLight,
    secondary = TossBlue,
    onSecondary = Color.White,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = ErrorRed,
    onError = Color.White,
)

@Composable
fun AICoachStockTheme(
    darkTheme: Boolean = false, // 토스 톤은 라이트 고정 (브랜드 일관성)
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)

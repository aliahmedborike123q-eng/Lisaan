package com.lisaan.app.core.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import com.lisaan.app.core.datastore.AppThemeMode

private val LightColorScheme = lightColorScheme(
    primary = LapisBlueLight,
    onPrimary = OnLapisBlueLight,
    primaryContainer = LapisContainerLight,
    onPrimaryContainer = OnLapisContainerLight,
    secondary = OasisEmeraldLight,
    onSecondary = OnOasisEmeraldLight,
    secondaryContainer = OasisContainerLight,
    onSecondaryContainer = OnOasisContainerLight,
    tertiary = DesertAmberLight,
    onTertiary = OnDesertAmberLight,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = OnAmberContainerLight,
    background = WarmSandBackgroundLight,
    onBackground = OnSurfacePrimaryLight,
    surface = PureSurfaceLight,
    onSurface = OnSurfacePrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceSecondaryLight,
    outline = OutlineLight,
    error = SemanticErrorRed,
    errorContainer = SemanticErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = LapisBlueDark,
    onPrimary = OnLapisBlueDark,
    primaryContainer = LapisContainerDark,
    onPrimaryContainer = OnLapisContainerDark,
    secondary = OasisEmeraldDark,
    onSecondary = OnOasisEmeraldDark,
    secondaryContainer = OasisContainerDark,
    onSecondaryContainer = OnOasisContainerDark,
    tertiary = DesertAmberDark,
    onTertiary = OnDesertAmberDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    background = NightSkyBackgroundDark,
    onBackground = OnSurfacePrimaryDark,
    surface = DeepSlateSurfaceDark,
    onSurface = OnSurfacePrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceSecondaryDark,
    outline = OutlineDark,
    error = SemanticErrorRed,
    errorContainer = SemanticErrorContainer
)

val LocalAppLanguage = staticCompositionLocalOf { "ar" }

@Composable
fun LisaanTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    interfaceLangCode: String = "ar",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val isArabic = interfaceLangCode.lowercase() == "ar"
    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
    val typography = buildLisaanTypography(isArabic = isArabic)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalAppLanguage provides interfaceLangCode
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = LisaanShapes,
            content = content
        )
    }
}

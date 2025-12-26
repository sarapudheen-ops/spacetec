// core/ui/src/main/kotlin/com/spacetec/automotive/core/ui/theme/Theme.kt
package com.spacetec.obd.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================================================
// COLOR DEFINITIONS
// ============================================================================

// Primary Colors - Professional Blue
private val PrimaryLight = Color(0xFF1565C0)
private val OnPrimaryLight = Color(0xFFFFFFFF)
private val PrimaryContainerLight = Color(0xFFD1E4FF)
private val OnPrimaryContainerLight = Color(0xFF001D36)

private val PrimaryDark = Color(0xFF9ECAFF)
private val OnPrimaryDark = Color(0xFF003258)
private val PrimaryContainerDark = Color(0xFF00497D)
private val OnPrimaryContainerDark = Color(0xFFD1E4FF)

// Secondary Colors - Automotive Orange
private val SecondaryLight = Color(0xFFE65100)
private val OnSecondaryLight = Color(0xFFFFFFFF)
private val SecondaryContainerLight = Color(0xFFFFDBCF)
private val OnSecondaryContainerLight = Color(0xFF341100)

private val SecondaryDark = Color(0xFFFFB599)
private val OnSecondaryDark = Color(0xFF552000)
private val SecondaryContainerDark = Color(0xFF793100)
private val OnSecondaryContainerDark = Color(0xFFFFDBCF)

// Tertiary Colors - Status Green
private val TertiaryLight = Color(0xFF2E7D32)
private val OnTertiaryLight = Color(0xFFFFFFFF)
private val TertiaryContainerLight = Color(0xFFB8F5B0)
private val OnTertiaryContainerLight = Color(0xFF002204)

private val TertiaryDark = Color(0xFF8CD987)
private val OnTertiaryDark = Color(0xFF00390A)
private val TertiaryContainerDark = Color(0xFF005312)
private val OnTertiaryContainerDark = Color(0xFFB8F5B0)

// Error Colors
private val ErrorLight = Color(0xFFBA1A1A)
private val OnErrorLight = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFFFDAD6)
private val OnErrorContainerLight = Color(0xFF410002)

private val ErrorDark = Color(0xFFFFB4AB)
private val OnErrorDark = Color(0xFF690005)
private val ErrorContainerDark = Color(0xFF93000A)
private val OnErrorContainerDark = Color(0xFFFFDAD6)

// Background Colors
private val BackgroundLight = Color(0xFFFDFCFF)
private val OnBackgroundLight = Color(0xFF1A1C1E)
private val SurfaceLight = Color(0xFFFDFCFF)
private val OnSurfaceLight = Color(0xFF1A1C1E)

private val BackgroundDark = Color(0xFF1A1C1E)
private val OnBackgroundDark = Color(0xFFE2E2E6)
private val SurfaceDark = Color(0xFF1A1C1E)
private val OnSurfaceDark = Color(0xFFE2E2E6)

// Surface Variants
private val SurfaceVariantLight = Color(0xFFDFE2EB)
private val OnSurfaceVariantLight = Color(0xFF43474E)
private val OutlineLight = Color(0xFF73777F)
private val OutlineVariantLight = Color(0xFFC3C6CF)

private val SurfaceVariantDark = Color(0xFF43474E)
private val OnSurfaceVariantDark = Color(0xFFC3C6CF)
private val OutlineDark = Color(0xFF8D9199)
private val OutlineVariantDark = Color(0xFF43474E)

// ============================================================================
// COLOR SCHEMES
// ============================================================================

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

// ============================================================================
// CUSTOM COLORS
// ============================================================================

/**
 * Custom colors for SpaceTec-specific UI elements.
 */
object SpaceTecColors {
    // DTC Severity Colors
    val Critical = Color(0xFFD32F2F)
    val CriticalContainer = Color(0xFFFFCDD2)
    val Major = Color(0xFFF57C00)
    val MajorContainer = Color(0xFFFFE0B2)
    val Minor = Color(0xFFFBC02D)
    val MinorContainer = Color(0xFFFFF9C4)
    val Info = Color(0xFF1976D2)
    val InfoContainer = Color(0xFFBBDEFB)
    
    // DTC System Colors
    val Powertrain = Color(0xFF1565C0)
    val Body = Color(0xFF7B1FA2)
    val Chassis = Color(0xFF00838F)
    val Network = Color(0xFF558B2F)
    
    // Connection Status
    val Connected = Color(0xFF4CAF50)
    val Connecting = Color(0xFFFFC107)
    val Disconnected = Color(0xFF9E9E9E)
    val Error = Color(0xFFF44336)
    
    // MIL (Check Engine Light)
    val MilOn = Color(0xFFFF9800)
    val MilOff = Color(0xFF4CAF50)
    
    // Signal Strength
    val SignalExcellent = Color(0xFF4CAF50)
    val SignalGood = Color(0xFF8BC34A)
    val SignalFair = Color(0xFFFFC107)
    val SignalPoor = Color(0xFFFF5722)
    val SignalNone = Color(0xFF9E9E9E)
}

// ============================================================================
// THEME COMPOSABLE
// ============================================================================

/**
 * SpaceTec application theme.
 * 
 * @param darkTheme Whether to use dark theme
 * @param dynamicColor Whether to use dynamic colors (Android 12+)
 * @param content Content to display
 */
@Composable
fun SpaceTecTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) 
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SpaceTecTypography,
        shapes = SpaceTecShapes,
        content = content
    )
}

/**
 * Extension to get severity color.
 */
@Composable
fun ColorScheme.severityColor(severity: com.spacetec.obd.core.domain.model.dtc.DtcSeverity): Color {
    return when (severity) {
        com.spacetec.obd.core.domain.model.dtc.DtcSeverity.CRITICAL -> SpaceTecColors.Critical
        com.spacetec.obd.core.domain.model.dtc.DtcSeverity.HIGH -> SpaceTecColors.Critical
        com.spacetec.obd.core.domain.model.dtc.DtcSeverity.MAJOR -> SpaceTecColors.Major
        com.spacetec.obd.core.domain.model.dtc.DtcSeverity.MEDIUM -> SpaceTecColors.Major
        com.spacetec.obd.core.domain.model.dtc.DtcSeverity.MINOR -> SpaceTecColors.Minor
        com.spacetec.obd.core.domain.model.dtc.DtcSeverity.LOW -> SpaceTecColors.Minor
        com.spacetec.obd.core.domain.model.dtc.DtcSeverity.INFO -> SpaceTecColors.Info
    }
}

/**
 * Extension to get system color.
 */
@Composable
fun ColorScheme.systemColor(system: com.spacetec.obd.core.domain.model.dtc.DtcSystem): Color {
    return when (system) {
        com.spacetec.obd.core.domain.model.dtc.DtcSystem.POWERTRAIN -> SpaceTecColors.Powertrain
        com.spacetec.obd.core.domain.model.dtc.DtcSystem.BODY -> SpaceTecColors.Body
        com.spacetec.obd.core.domain.model.dtc.DtcSystem.CHASSIS -> SpaceTecColors.Chassis
        com.spacetec.obd.core.domain.model.dtc.DtcSystem.NETWORK -> SpaceTecColors.Network
    }
}
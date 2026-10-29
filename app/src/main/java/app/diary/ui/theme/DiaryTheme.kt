package app.diary.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.compose.foundation.shape.RoundedCornerShape

private val Pine = Color(0xFF2C4A3E)
private val PineSoft = Color(0xFFD7E6DE)
private val Ink = Color(0xFF1E1B17)
private val Clay = Color(0xFF8A6A4F)
private val Paper = Color(0xFFF3EEE6)
private val Card = Color(0xFFFCFAF6)
private val Line = Color(0xFFE4DCD0)

private val LightColors = lightColorScheme(
    primary = Pine,
    onPrimary = Color(0xFFF6F3EE),
    primaryContainer = PineSoft,
    onPrimaryContainer = Color(0xFF143028),
    secondary = Clay,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Color(0xFFECE4D8),
    onSurfaceVariant = Color(0xFF5C564E),
    outline = Line,
    error = Color(0xFF8C3A32),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC5DDD2),
    onPrimary = Color(0xFF143028),
    primaryContainer = Color(0xFF2A4037),
    onPrimaryContainer = Color(0xFFD7E6DE),
    secondary = Color(0xFFE0C6B0),
    onSecondary = Color(0xFF2C2118),
    background = Color(0xFF141311),
    onBackground = Color(0xFFE8E2D8),
    surface = Color(0xFF1E1C19),
    onSurface = Color(0xFFE8E2D8),
    surfaceVariant = Color(0xFF2A2723),
    onSurfaceVariant = Color(0xFFC9C0B4),
    outline = Color(0xFF3A3631),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val DiaryTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.6).sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 34.sp,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)

private val DiaryShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun DiaryTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = diaryColors(context, dark)
    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context.findActivity()
        if (activity != null) {
            SideEffect {
                val controller = WindowCompat.getInsetsController(activity.window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = DiaryTypography,
        shapes = DiaryShapes,
        content = content,
    )
}

/** 浅色或深色跟着系统走。Android 12 起主色用系统动态色，纸色背景仍用日记自己的。 */
private fun diaryColors(context: Context, dark: Boolean) = run {
    val base = if (dark) DarkColors else LightColors
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@run base
    val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    base.copy(
        primary = dynamic.primary,
        onPrimary = dynamic.onPrimary,
        primaryContainer = dynamic.primaryContainer,
        onPrimaryContainer = dynamic.onPrimaryContainer,
        secondary = dynamic.secondary,
        onSecondary = dynamic.onSecondary,
    )
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

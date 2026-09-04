package com.naran.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

/**
 * همان هویتی که در پنل هست: نور گرم روی شب سرمه‌ای.
 * برند «ناران» یعنی نور، پس تنها جای روشن صفحه باید دکمه‌ی اتصال باشد.
 */
object NaranColors {
    val Night = Color(0xFF0D1220)
    val Surface = Color(0xFF151E33)
    val Raise = Color(0xFF1C2844)
    val Edge = Color(0xFF243255)
    val Glow = Color(0xFFFFC46B)
    val GlowDim = Color(0xFF8A6A38)
    val Live = Color(0xFF34D399)
    val Dead = Color(0xFFF87171)
    val Text = Color(0xFFE6ECF7)
    val Muted = Color(0xFF8496B8)
}

private val Scheme = darkColorScheme(
    primary = NaranColors.Glow,
    onPrimary = Color(0xFF241703),
    secondary = NaranColors.Live,
    background = NaranColors.Night,
    onBackground = NaranColors.Text,
    surface = NaranColors.Surface,
    onSurface = NaranColors.Text,
    surfaceVariant = NaranColors.Raise,
    onSurfaceVariant = NaranColors.Muted,
    outline = NaranColors.Edge,
    error = NaranColors.Dead
)

private val NaranType = Typography(
    displayLarge = TextStyle(fontSize = 42.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontSize = 13.sp, color = NaranColors.Muted, lineHeight = 22.sp)
)

/**
 * کل اپ راست‌چین است. فونت Vazirmatn را در res/font بگذارید و در
 * NaranType به عنوان fontFamily ست کنید.
 */
@Composable
fun NaranTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = Scheme, typography = NaranType, content = content)
    }
}

package com.naran.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.naran.core.T

/**
 * هویت بصری ناران.
 *
 * برند یعنی نور، پس پایه شب عمیق است و نور از دل آن بیرون می‌زند. دو
 * رنگ نئون داریم — کهربایی گرم و فیروزه‌ای سرد — که در گرادیان‌ها به هم
 * می‌رسند. عمداً فقط دو تا: نئونی که همه‌جا باشد دیگر نئون نیست.
 */
object NaranColors {
    // پایه
    val Night = Color(0xFF080B14)
    val Surface = Color(0xFF111726)
    val Raise = Color(0xFF192033)
    val Edge = Color(0xFF243150)
    val EdgeLit = Color(0xFF3D5490)

    // نئون گرم — نور، برند
    val Glow = Color(0xFFFFC46B)
    val GlowHot = Color(0xFFFF9E4A)
    val GlowDim = Color(0xFF8A6A38)

    // نئون سرد — تضاد و وضعیت
    val Cyan = Color(0xFF3DE0D5)
    val CyanDim = Color(0xFF1E6B66)
    val Violet = Color(0xFF8B5CF6)

    // وضعیت
    val Live = Color(0xFF34D399)
    val Dead = Color(0xFFFF6B7A)
    val Warn = Color(0xFFFBBF24)

    // متن
    val Text = Color(0xFFEAF0FF)
    val Muted = Color(0xFF7E90B4)

    // ── گرادیان‌ها ──

    val screenGlow = Brush.radialGradient(
        colors = listOf(Color(0x1AFFC46B), Color(0x0D3DE0D5), Color.Transparent),
        radius = 1400f
    )

    val powerOn = Brush.linearGradient(listOf(GlowHot, Glow))

    val edgeLit = Brush.linearGradient(
        listOf(Glow.copy(alpha = 0.55f), Cyan.copy(alpha = 0.35f))
    )

    val cardLit = Brush.linearGradient(
        listOf(Color(0xFF1A2338), Color(0xFF121A2C))
    )

    /** بخش عمومی، تا از سرورهای شخصی جدا دیده شود. */
    val publicTint = Brush.linearGradient(
        listOf(Cyan.copy(alpha = 0.10f), Violet.copy(alpha = 0.06f))
    )
}

private val Scheme = darkColorScheme(
    primary = NaranColors.Glow,
    onPrimary = Color(0xFF1A1002),
    secondary = NaranColors.Cyan,
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
    displayLarge = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontSize = 13.sp, color = NaranColors.Muted, lineHeight = 21.sp)
)

/** جهت صفحه از زبان می‌آید: فارسی راست‌چین، انگلیسی چپ‌چین. */
@Composable
fun NaranTheme(content: @Composable () -> Unit) {
    val dir = if (T.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides dir) {
        MaterialTheme(colorScheme = Scheme, typography = NaranType, content = content)
    }
}

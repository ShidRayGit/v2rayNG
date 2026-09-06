package com.naran.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naran.core.T

/**
 * منوی افزودن.
 *
 * سه راه ورود، همه در یک جا: کد لایسنس، لینک ساب، و چسباندن کانفیگ.
 * قبلاً این‌ها پراکنده بودند و کاربر تازه نمی‌دانست از کجا شروع کند.
 */
@Composable
fun AddSheet(
    onLicense: () -> Unit,
    onSubscription: () -> Unit,
    onClipboard: () -> Unit
) {
    Column(
        Modifier
            .background(NaranColors.Surface)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Text(T.addTitle, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        Choice("＃", T.addLicense, T.addLicenseSub, NaranColors.Glow, onLicense)
        Spacer(Modifier.height(10.dp))
        Choice("⇢", T.addSubLink, T.addSubLinkSub, NaranColors.Cyan, onSubscription)
        Spacer(Modifier.height(10.dp))
        Choice("⧉", T.addClipboard, T.addClipboardSub, NaranColors.Violet, onClipboard)

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Choice(
    glyph: String,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(NaranColors.Night)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.08f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, fontSize = 19.sp, color = accent, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }

        Text(if (T.isRtl) "‹" else "›", color = NaranColors.Muted, fontSize = 19.sp)
    }
}

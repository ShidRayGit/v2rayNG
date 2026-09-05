package com.naran.core.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.naran.core.NaranNotice
import com.naran.core.T

/**
 * اطلاعیه از پنل.
 *
 * یک بار نشان داده می‌شود و بعد از تأیید دیگر نمی‌آید — شناسه‌اش لوکال
 * ذخیره می‌شود.
 */
@Composable
fun NoticeDialog(notice: NaranNotice, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NaranColors.Surface,
        title = {
            Text(
                notice.title.ifBlank { T.notices },
                style = MaterialTheme.typography.titleMedium,
                color = if (notice.isWarning) NaranColors.Warn else NaranColors.Glow
            )
        },
        text = {
            Text(notice.body, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(T.gotIt, color = NaranColors.Glow)
            }
        }
    )
}

package com.naran.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naran.core.NaranRelease
import com.naran.core.NaranUpdate
import com.naran.core.T

/**
 * اطلاع آپدیت، هنگام باز شدن اپ.
 *
 * آپدیت اجباری دکمه‌ی رد ندارد و با کلیک بیرون هم بسته نمی‌شود.
 */
@Composable
fun UpdateDialog(
    release: NaranRelease,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onOpenLink: () -> Unit
) {
    val state by NaranUpdate.state.collectAsState()
    val busy = state is NaranUpdate.State.Downloading

    AlertDialog(
        onDismissRequest = { if (!release.mandatory && !busy) onDismiss() },
        containerColor = NaranColors.Surface,
        title = {
            Text(
                T.updateReady(release.versionName),
                style = MaterialTheme.typography.titleMedium.copy(
                    brush = NaranColors.textHot
                )
            )
        },
        text = {
            Column {
                if (release.changelog.isNotBlank()) {
                    Text(
                        release.changelog,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                }

                when (val s = state) {
                    is NaranUpdate.State.Downloading -> {
                        Text(
                            T.downloading(s.percent),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(NaranColors.Night)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(s.percent / 100f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(NaranColors.powerOn)
                            )
                        }
                    }

                    is NaranUpdate.State.Failed -> Text(
                        s.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = NaranColors.Dead
                    )

                    else -> {}
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload, enabled = !busy) {
                Text(
                    if (state is NaranUpdate.State.Failed) T.openInBrowser else T.getUpdate,
                    color = NaranColors.Glow,
                    fontSize = 15.sp
                )
            }
        },
        dismissButton = {
            if (!release.mandatory && !busy) {
                TextButton(onClick = onDismiss) {
                    Text(T.later, color = NaranColors.Muted)
                }
            }
        }
    )
}

package com.naran.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naran.core.*

/**
 * کانفیگ‌های یک سابسکریپشن.
 *
 * هدف اصلی این صفحه انتخاب سرور است، نه مدیریت ساب — پس فهرست
 * کانفیگ‌ها بیشترین جا را می‌گیرد و مدیریت به یک ردیف کوچک بالا می‌رود.
 */
@Composable
fun SubDetailScreen(
    sub: Subscription,
    selectedId: Int?,
    onBack: () -> Unit,
    onPingAll: () -> Unit,
    onToggleSort: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onSelect: (NaranConfig) -> Unit,
    onConnect: (NaranConfig) -> Unit
) {
    val progress by NaranTest.progress.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    val daysLeft = remember(sub.expiresAt) {
        if (sub.expiresAt <= 0) -1L
        else (sub.expiresAt - System.currentTimeMillis() / 1000) / 86400
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = NaranColors.Surface,
            title = { Text(T.removeSub, style = MaterialTheme.typography.titleMedium) },
            text = { Text(T.removeSubBody, style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(T.delete, color = NaranColors.Dead)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(T.cancel, color = NaranColors.Muted)
                }
            }
        )
    }

    ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                BackButton(onBack)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        sub.title.ifBlank { sub.domain },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        T.subServers(sub.configs.size) +
                            if (daysLeft >= 0) " · " + T.subExpires(daysLeft) else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // نوار مصرف، فقط اگر سرویس‌دهنده عددی داده باشد
            if (sub.totalBytes > 0) {
                val ratio = (sub.usedBytes.toFloat() / sub.totalBytes).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(NaranColors.Night)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(ratio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (ratio > 0.9f)
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(NaranColors.Dead, NaranColors.Dead)
                                    )
                                else NaranColors.powerOn
                            )
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    T.subUsage(
                        NaranTraffic.bytes(sub.usedBytes),
                        NaranTraffic.bytes(sub.totalBytes)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
            }

            // ردیف ابزار: تست همه، مرتب‌سازی، مدیریت
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NaranColors.publicTint)
                        .border(1.dp, NaranColors.CyanDim, RoundedCornerShape(12.dp))
                        .clickable(enabled = !progress.running) { onPingAll() }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (progress.running) {
                        Text(
                            T.pingProgress(progress.done, progress.total),
                            fontSize = 12.sp, color = NaranColors.Cyan
                        )
                    } else {
                        Text(T.pingAll, fontSize = 13.sp, color = NaranColors.Cyan)
                    }
                }

                Spacer(Modifier.width(9.dp))

                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(NaranColors.Surface)
                        .border(1.dp, NaranColors.Edge, RoundedCornerShape(12.dp))
                        .clickable { onRefresh() }
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) { Text(T.updateNow, fontSize = 13.sp, color = NaranColors.Glow) }

                Spacer(Modifier.width(9.dp))

                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(NaranColors.Surface)
                        .border(1.dp, NaranColors.Edge, RoundedCornerShape(12.dp))
                        .clickable { confirmDelete = true }
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) { Text(T.delete, fontSize = 13.sp, color = NaranColors.Dead) }
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    T.sortByBest,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = sub.sortByBest,
                    onCheckedChange = onToggleSort,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NaranColors.Night,
                        checkedTrackColor = NaranColors.Cyan,
                        uncheckedThumbColor = NaranColors.Muted,
                        uncheckedTrackColor = NaranColors.Night,
                        uncheckedBorderColor = NaranColors.Edge
                    )
                )
            }

            Spacer(Modifier.height(6.dp))

            if (sub.configs.isEmpty()) {
                Text(
                    T.noConfigsInSub,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sub.configs, key = { it.raw }) { c ->
                        val cfg = remember(c.raw) { sub.toConfig(c) }
                        ConfigRow(
                            config = cfg,
                            ping = c.pingMs,
                            selected = cfg.id == selectedId,
                            onSelect = { onSelect(cfg) },
                            onConnect = { onConnect(cfg) }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ConfigRow(
    config: NaranConfig,
    ping: Long,
    selected: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) NaranColors.Raise else NaranColors.Surface)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) NaranColors.Cyan.copy(alpha = 0.65f) else NaranColors.Edge,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onSelect)
            .padding(start = 14.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                config.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(config.protocol, style = MaterialTheme.typography.bodySmall)
                Text("  ·  ", style = MaterialTheme.typography.bodySmall)
                Text(
                    when {
                        ping > 0 -> T.num(ping) + " ms"
                        ping == 0L -> T.untested
                        else -> "—"
                    },
                    fontSize = 13.sp,
                    color = when {
                        ping > 0 && ping < 300 -> NaranColors.Live
                        ping > 0 -> NaranColors.Warn
                        ping < 0 -> NaranColors.Dead
                        else -> NaranColors.Muted
                    }
                )
                if (selected) {
                    Text("  ·  ", style = MaterialTheme.typography.bodySmall)
                    Text(T.selected, fontSize = 12.sp, color = NaranColors.Cyan)
                }
            }
        }

        // اتصال مستقیم: انتخاب می‌کند و به صفحه‌ی اصلی می‌برد
        OutlinedButton(
            onClick = onConnect,
            shape = RoundedCornerShape(11.dp),
            border = BorderStroke(1.dp, NaranColors.Glow.copy(alpha = 0.55f)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) { Text(T.connectNow, fontSize = 12.sp, color = NaranColors.Glow) }
    }
}

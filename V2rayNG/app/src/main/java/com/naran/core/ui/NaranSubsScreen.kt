package com.naran.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naran.core.*
import kotlinx.coroutines.launch

/**
 * سابسکریپشن‌ها.
 *
 * لینک فقط از دامنه‌های مجاز پذیرفته می‌شود؛ اجازه را سرور تعیین می‌کند
 * نه اپ، تا فهرست بدون APK جدید قابل تغییر بماند.
 */
@Composable
fun SubsScreen(
    onBack: () -> Unit,
    onPingAll: (Subscription) -> Unit,
    pingingId: String?
) {
    val subs by NaranSubs.subs.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var allowedHint by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirmRemove by remember { mutableStateOf<Subscription?>(null) }
    val scope = rememberCoroutineScope()

    confirmRemove?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            containerColor = NaranColors.Surface,
            title = { Text(T.removeSub, style = MaterialTheme.typography.titleMedium) },
            text = { Text(T.removeSubBody, style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(onClick = {
                    NaranSubs.remove(target.id); confirmRemove = null
                }) { Text(T.delete, color = NaranColors.Dead) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) {
                    Text(T.cancel, color = NaranColors.Muted)
                }
            }
        )
    }

    ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                    Text(T.back, color = NaranColors.Glow)
                }
                Spacer(Modifier.weight(1f))
                Text(T.subscriptions, style = MaterialTheme.typography.titleLarge)
            }

            Spacer(Modifier.height(18.dp))

            subs.forEach { sub ->
                SubCard(
                    sub = sub,
                    pinging = pingingId == sub.id,
                    onRefresh = {
                        scope.launch {
                            when (NaranSubs.refresh(sub.id)) {
                                is SubResult.Ok -> error = null
                                is SubResult.Failed -> error = T.subFetchFailed
                                else -> {}
                            }
                        }
                    },
                    onPingAll = { onPingAll(sub) },
                    onToggleSort = { NaranSubs.setSort(sub.id, it) },
                    onRemove = { confirmRemove = sub }
                )
                Spacer(Modifier.height(12.dp))
            }

            if (subs.isEmpty() && !showAdd) {
                Text(
                    T.noConfigsYet,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 30.dp),
                    textAlign = TextAlign.Center
                )
            }

            if (showAdd) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(NaranColors.Surface)
                        .border(1.dp, NaranColors.Edge, RoundedCornerShape(16.dp))
                        .padding(15.dp)
                ) {
                    Text(T.subUrl, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; error = null },
                        placeholder = {
                            Text("https://…", color = NaranColors.Muted, fontSize = 14.sp)
                        },
                        singleLine = true,
                        isError = error != null,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NaranColors.Cyan,
                            unfocusedBorderColor = NaranColors.Edge,
                            focusedContainerColor = NaranColors.Night,
                            unfocusedContainerColor = NaranColors.Night,
                            cursorColor = NaranColors.Cyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (error != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            error!!, color = NaranColors.Dead,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (allowedHint.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                T.subAllowedList + " " + allowedHint.joinToString("، "),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedButton(
                            onClick = { showAdd = false; url = ""; error = null },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, NaranColors.Edge),
                            modifier = Modifier.weight(1f)
                        ) { Text(T.cancel, color = NaranColors.Muted, fontSize = 13.sp) }

                        Button(
                            onClick = {
                                busy = true; error = null; allowedHint = emptyList()
                                scope.launch {
                                    when (val r = NaranSubs.add(url)) {
                                        is SubResult.Ok -> {
                                            showAdd = false; url = ""
                                        }
                                        is SubResult.NotAllowed -> {
                                            error = T.subNotAllowed(r.domain)
                                            allowedHint = r.allowed
                                        }
                                        is SubResult.Failed -> error = r.message
                                    }
                                    busy = false
                                }
                            },
                            enabled = !busy && url.length > 10,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NaranColors.Cyan
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (busy) {
                                CircularProgressIndicator(
                                    Modifier.size(16.dp), strokeWidth = 2.dp,
                                    color = NaranColors.Night
                                )
                            } else {
                                Text(T.save, color = NaranColors.Night, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { showAdd = true },
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, NaranColors.CyanDim),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) { Text(T.addSub, color = NaranColors.Cyan) }
            }

            Spacer(Modifier.height(34.dp))
        }
    }
}

@Composable
private fun SubCard(
    sub: Subscription,
    pinging: Boolean,
    onRefresh: () -> Unit,
    onPingAll: () -> Unit,
    onToggleSort: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    val daysLeft = remember(sub.expiresAt) {
        if (sub.expiresAt <= 0) -1
        else ((sub.expiresAt - System.currentTimeMillis() / 1000) / 86400)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(NaranColors.Surface)
            .border(1.dp, NaranColors.Edge, RoundedCornerShape(17.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    sub.title.ifBlank { sub.domain },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${T.num(sub.configs.size)} ${T.servers}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (daysLeft >= 0) {
                Text(
                    if (daysLeft > 0) T.subExpires(daysLeft) else T.subExpired,
                    fontSize = 12.sp,
                    color = when {
                        daysLeft <= 0 -> NaranColors.Dead
                        daysLeft < 4 -> NaranColors.Warn
                        else -> NaranColors.Live
                    }
                )
            }
        }

        // نوار مصرف
        if (sub.totalBytes > 0) {
            Spacer(Modifier.height(12.dp))
            val ratio = (sub.usedBytes.toFloat() / sub.totalBytes).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(NaranColors.Night)
            ) {
                // هر دو شاخه باید Brush باشند، وگرنه تایپ‌ها نمی‌خوانند
                Box(
                    Modifier
                        .fillMaxWidth(ratio)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (ratio > 0.9f)
                                Brush.linearGradient(
                                    listOf(NaranColors.Dead, NaranColors.Dead)
                                )
                            else NaranColors.powerOn
                        )
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                T.subUsage(
                    NaranTraffic.bytes(sub.usedBytes),
                    NaranTraffic.bytes(sub.totalBytes)
                ),
                style = MaterialTheme.typography.bodySmall
            )
        } else if (sub.usedBytes > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                T.subUsed(NaranTraffic.bytes(sub.usedBytes)) + " · " + T.subUnlimited,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(T.sortByBest, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f))
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction(
                if (pinging) T.searching else T.pingAll,
                NaranColors.Cyan, Modifier.weight(1f), !pinging, onPingAll
            )
            SmallAction(T.updateNow, NaranColors.Glow, Modifier.weight(1f), true, onRefresh)
            SmallAction(T.delete, NaranColors.Dead, Modifier.weight(0.7f), true, onRemove)
        }
    }
}

@Composable
private fun SmallAction(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(NaranColors.Night)
            .border(1.dp, NaranColors.Edge, RoundedCornerShape(11.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.sp, color = if (enabled) color else NaranColors.Muted)
    }
}

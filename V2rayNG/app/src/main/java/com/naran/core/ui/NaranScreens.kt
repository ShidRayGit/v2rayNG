package com.naran.core.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naran.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * صفحات اپ.
 *
 * هیچ‌کدام کانفیگ خام را نشان نمی‌دهند — کاربر فقط نام، پرچم و پینگ
 * می‌بیند. صفحه‌ی افزودن و اشتراک‌گذاری در فورک حذف شده.
 */

// ────────────────────────── ورود کد ──────────────────────────

@Composable
fun LicenseScreen(
    canGoBack: Boolean,
    onBack: () -> Unit,
    onActivated: () -> Unit,
    onOpenChannel: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val (channelName, channelUrl) = remember { NaranManager.channel() }
    val ads = NaranManager.adsFor("license")

    ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            // اگر کاربر از جای دیگری آمده، راه برگشت داشته باشد
            Row(Modifier.fillMaxWidth()) {
                if (canGoBack) BackButton(onBack)
            }

            Spacer(Modifier.height(if (canGoBack) 30.dp else 44.dp))
            Lamp(big = true)
            Spacer(Modifier.height(20.dp))

            Text(T.appName, style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp))
            Spacer(Modifier.height(8.dp))
            Text(
                T.licenseSub(channelName),
                style = MaterialTheme.typography.bodyMedium,
                color = NaranColors.Muted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().take(24); error = null },
                placeholder = {
                    Text(
                        T.licenseHint, color = NaranColors.Muted,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )
                },
                singleLine = true,
                isError = error != null,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters
                ),
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    textAlign = TextAlign.Center, letterSpacing = 6.sp,
                    color = NaranColors.Text
                ),
                shape = RoundedCornerShape(15.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NaranColors.Glow,
                    unfocusedBorderColor = NaranColors.Edge,
                    focusedContainerColor = NaranColors.Surface,
                    unfocusedContainerColor = NaranColors.Surface,
                    cursorColor = NaranColors.Glow
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    error!!, color = NaranColors.Dead,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(20.dp))

            NeonButton(
                label = T.activate,
                enabled = !busy && code.length >= 4,
                busy = busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                busy = true; error = null
                scope.launch {
                    when (val r = NaranManager.activate(code)) {
                        is ActivateResult.Ok -> { busy = false; onActivated() }
                        is ActivateResult.Rejected -> { busy = false; error = r.message }
                        is ActivateResult.Offline -> { busy = false; error = r.message }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            TextButton(onClick = { onOpenChannel(channelUrl) }) {
                Text(T.openChannel(channelName), color = NaranColors.Glow)
            }

            Spacer(Modifier.height(24.dp))
            ads.forEach { AdBanner(it, onOpenChannel) }
            Spacer(Modifier.height(30.dp))
        }
    }
}

// ────────────────────────── اتصال ──────────────────────────

@Composable
fun ConnectScreen(
    connected: Boolean,
    connecting: Boolean,
    failed: Boolean,
    errorText: String,
    selected: NaranConfig?,
    isPublic: Boolean,
    onToggle: () -> Unit,
    onPickServer: () -> Unit,
    onRefreshProbe: () -> Unit,
    onPing: () -> Boolean,
    onSettings: () -> Unit,
    onOpenChannel: (String) -> Unit
) {
    val traffic by NaranTraffic.flow.collectAsState()
    val licenses by NaranManager.licenses.collectAsState()
    val probe by NaranProbe.result.collectAsState()
    var tick by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) { tick = System.currentTimeMillis(); delay(1000) }
    }

    val lic = remember(licenses, selected, tick) {
        licenses.firstOrNull { it.config.id == selected?.id }
    }

    ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Lamp()
                    Spacer(Modifier.width(10.dp))
                    Text(T.appName, style = MaterialTheme.typography.titleLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    lic?.let {
                        val left = NaranManager.remaining(it)
                        Pill(
                            text = if (left >= 3_600_000) T.hoursLeft(left / 3_600_000)
                                   else T.minutesLeft(left / 60_000),
                            color = if (left < 3_600_000) NaranColors.Dead
                                    else NaranColors.Live
                        )
                    }
                    IconButton(onClick = onSettings, modifier = Modifier.size(42.dp)) {
                        GearMark()
                    }
                }
            }

            Spacer(Modifier.height(34.dp))

            PowerButton(connected = connected, connecting = connecting, onClick = onToggle)

            Spacer(Modifier.height(22.dp))

            Text(
                when {
                    connecting -> T.connecting
                    failed -> T.failed
                    connected -> T.connected
                    else -> T.notConnected
                },
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    failed -> NaranColors.Dead
                    connected -> NaranColors.Live
                    else -> NaranColors.Muted
                }
            )

            if (failed) {
                Spacer(Modifier.height(6.dp))
                Text(
                    errorText.ifBlank { T.failedHint },
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            Spacer(Modifier.height(26.dp))

            // انتخاب سرور
            Surface(
                onClick = onPickServer,
                shape = RoundedCornerShape(17.dp),
                color = NaranColors.Surface,
                border = BorderStroke(
                    1.dp, if (isPublic) NaranColors.CyanDim else NaranColors.Edge
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                selected?.let { "${it.flag} ${it.name}" } ?: T.noServer,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (isPublic) {
                                Spacer(Modifier.width(8.dp))
                                Pill(T.publicServers, NaranColors.Cyan)
                            }
                        }
                        Text(
                            selected?.location?.ifBlank { "—" } ?: T.tapToPick,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(T.change, color = NaranColors.Glow, fontSize = 14.sp)
                }
            }

            if (connected) {
                Spacer(Modifier.height(12.dp))
                ProbeRow(probe, onRefreshProbe)

                Spacer(Modifier.height(10.dp))
                LatencyRow(onPing)

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metric(T.download, NaranTraffic.speed(traffic.downBps),
                        NaranColors.Cyan, Modifier.weight(1f))
                    Metric(T.upload, NaranTraffic.speed(traffic.upBps),
                        NaranColors.Glow, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metric(
                        T.sessionUsage,
                        NaranTraffic.bytes(traffic.sessionUp + traffic.sessionDown),
                        NaranColors.Muted, Modifier.weight(1f)
                    )
                    Metric(T.duration, NaranTraffic.duration(traffic.elapsedMs),
                        NaranColors.Muted, Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(20.dp))
            NaranManager.adsFor("connect").forEach { AdBanner(it, onOpenChannel) }
            Spacer(Modifier.height(30.dp))
        }
    }
}

/**
 * تست تأخیر سرور فعلی.
 *
 * هسته فقط تونل فعال را می‌سنجد، پس این فقط وقتی وصل باشیم معنی دارد و
 * برای همین اینجاست، نه در فهرست سرورها.
 */
@Composable
private fun LatencyRow(onPing: () -> Boolean) {
    var pinging by remember { mutableStateOf(false) }
    var ms by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(pinging) {
        if (!pinging) return@LaunchedEffect
        val r = withTimeoutOrNull(12_000) { NaranServiceState.ping.first() }
        ms = r ?: -1L
        pinging = false
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(NaranColors.Surface)
            .border(1.dp, NaranColors.Edge, RoundedCornerShape(15.dp))
            .clickable(enabled = !pinging) {
                ms = null
                pinging = onPing()
            }
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PingMark()
            Spacer(Modifier.width(11.dp))
            Text(T.testPing, style = MaterialTheme.typography.titleMedium)
        }
        when {
            pinging -> CircularProgressIndicator(
                Modifier.size(17.dp), strokeWidth = 2.dp, color = NaranColors.Muted
            )
            ms != null -> Text(
                if (ms!! > 0) T.num(ms!!) + " ms" else "—",
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    ms!! <= 0 -> NaranColors.Dead
                    ms!! < 300 -> NaranColors.Live
                    else -> NaranColors.Warn
                }
            )
            else -> Text("—", color = NaranColors.Muted)
        }
    }
}

/**
 * نوار وضعیت واقعی.
 *
 * «متصل» بودن سرویس یعنی هسته بالا آمده، نه اینکه ترافیک رد می‌شود.
 * این نوار همان را می‌سنجد.
 */
@Composable
private fun ProbeRow(probe: NaranProbe.Result, onRefresh: () -> Unit) {
    val good = probe.verified
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(NaranColors.Surface)
            .border(
                1.dp,
                if (good) NaranColors.CyanDim else NaranColors.Edge,
                RoundedCornerShape(15.dp)
            )
            .padding(start = 15.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            when {
                probe.checking -> Text(
                    T.checking, style = MaterialTheme.typography.titleMedium
                )
                good && probe.ip.isNotBlank() -> {
                    Text(
                        "${probe.flag} ${probe.ip}",
                        style = MaterialTheme.typography.titleMedium,
                        color = NaranColors.Cyan
                    )
                    Text(
                        probe.note.ifBlank { probe.country.ifBlank { T.verified } },
                        style = MaterialTheme.typography.bodySmall,
                        color = NaranColors.Muted
                    )
                }
                good -> {
                    Text(T.internetOpen, style = MaterialTheme.typography.titleMedium)
                    Text(T.noExitIp, style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    Text(
                        T.tunnelUnverified,
                        style = MaterialTheme.typography.titleMedium,
                        color = NaranColors.Dead
                    )
                    Text(
                        probe.note.ifBlank { T.tryAnotherServer },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        IconButton(onClick = onRefresh, enabled = !probe.checking) {
            if (probe.checking) {
                CircularProgressIndicator(
                    Modifier.size(17.dp), strokeWidth = 2.dp, color = NaranColors.Muted
                )
            } else {
                RefreshMark()
            }
        }
    }
}

// ────────────────────────── انتخاب سرور ──────────────────────────

@Composable
fun ServerSheet(
    configs: List<NaranConfig>,
    publicConfigs: List<NaranPublicConfig>,
    selectedId: Int?,
    searching: Boolean,
    onPick: (NaranConfig) -> Unit,
    onPing: (NaranConfig) -> Boolean,
    onForget: (NaranConfig) -> Unit,
    onDiscover: () -> Unit,
    onDonate: () -> Unit,
    onSubs: () -> Unit,
    onAddCode: () -> Unit
) {
    Column(
        Modifier
            .background(NaranColors.Surface)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(T.servers, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(14.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 420.dp)
        ) {
            // ── سرورهای شخصی ──
            item(key = "own-header") {
                SectionHeader(T.yourServers, T.yourServersSub, NaranColors.Glow)
            }
            if (configs.isEmpty()) {
                item(key = "own-empty") {
                    Text(
                        T.noConfigsYet,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                items(configs, key = { "own-" + it.id }) { c ->
                    ServerRow(c, c.id == selectedId, null, false, onPing, onForget) {
                        onPick(c)
                    }
                }
            }

            // ── سرورهای عمومی ──
            item(key = "pub-header") {
                Spacer(Modifier.height(8.dp))
                SectionHeader(T.publicServers, T.publicSub, NaranColors.Cyan)
            }
            items(publicConfigs, key = { "pub-" + it.config.id }) { p ->
                ServerRow(
                    p.config,
                    p.config.id == selectedId,
                    if (p.donor.isNotBlank()) T.byDonor(p.donor) else null,
                    true,
                    onPing,
                    null
                ) { onPick(p.config) }
            }
            item(key = "discover") {
                DiscoverButton(searching, onDiscover)
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedButton(
                onClick = onAddCode,
                shape = RoundedCornerShape(13.dp),
                border = BorderStroke(1.dp, NaranColors.Edge),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text(
                    T.addCodeShort, color = NaranColors.Text, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = onSubs,
                shape = RoundedCornerShape(13.dp),
                border = BorderStroke(1.dp, NaranColors.Edge),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text(
                    T.subsShort, color = NaranColors.Text, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = onDonate,
                shape = RoundedCornerShape(13.dp),
                border = BorderStroke(1.dp, NaranColors.CyanDim),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text(
                    T.donateShort, color = NaranColors.Cyan, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, accent: Color) {
    Row(
        Modifier.padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(3.dp, 15.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(Modifier.width(9.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                color = accent
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DiscoverButton(searching: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(NaranColors.publicTint)
            .border(1.dp, NaranColors.CyanDim, RoundedCornerShape(13.dp))
            .clickable(enabled = !searching, onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (searching) {
            CircularProgressIndicator(
                Modifier.size(16.dp), strokeWidth = 2.dp, color = NaranColors.Cyan
            )
            Spacer(Modifier.width(10.dp))
            Text(T.searching, color = NaranColors.Cyan, fontSize = 14.sp)
        } else {
            SearchMark()
            Spacer(Modifier.width(10.dp))
            Text(T.findServer, color = NaranColors.Cyan, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ServerRow(
    c: NaranConfig,
    selected: Boolean,
    badge: String?,
    isPublic: Boolean,
    onPing: (NaranConfig) -> Boolean,
    onForget: ((NaranConfig) -> Unit)?,
    onClick: () -> Unit
) {
    // پینگ فقط روی سرور متصل معنی دارد، چون هسته تأخیر اتصال فعلی را
    // می‌سنجد نه هر سروری را.
    var pinging by remember(c.id) { mutableStateOf(false) }
    var pingMs by remember(c.id) { mutableStateOf<Long?>(null) }
    var confirmForget by remember(c.id) { mutableStateOf(false) }

    LaunchedEffect(pinging) {
        if (!pinging) return@LaunchedEffect
        val ms = withTimeoutOrNull(12_000) { NaranServiceState.ping.first() }
        pingMs = ms ?: -1L
        pinging = false
    }

    if (confirmForget && onForget != null) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            containerColor = NaranColors.Surface,
            title = { Text(T.forgetTitle, style = MaterialTheme.typography.titleMedium) },
            text = { Text(T.forgetBody, style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(onClick = { confirmForget = false; onForget(c) }) {
                    Text(T.delete, color = NaranColors.Dead)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) {
                    Text(T.cancel, color = NaranColors.Muted)
                }
            }
        )
    }

    val accent = if (isPublic) NaranColors.Cyan else NaranColors.Glow

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) NaranColors.Raise else NaranColors.Night)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) accent.copy(alpha = 0.65f) else NaranColors.Edge,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text("${c.flag} ${c.name}", style = MaterialTheme.typography.titleMedium)
            Text(
                badge ?: c.location.ifBlank { c.protocol },
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Box(
                    Modifier
                        .defaultMinSize(minWidth = 46.dp, minHeight = 38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !pinging) {
                            pingMs = null
                            pinging = onPing(c)
                            if (!pinging) pingMs = -1L
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        pinging -> CircularProgressIndicator(
                            Modifier.size(15.dp), strokeWidth = 2.dp,
                            color = NaranColors.Muted
                        )
                        pingMs != null -> Text(
                            if (pingMs!! > 0) T.num(pingMs!!) else "—",
                            fontSize = 13.sp,
                            color = when {
                                pingMs!! <= 0 -> NaranColors.Dead
                                pingMs!! < 300 -> NaranColors.Live
                                else -> NaranColors.Warn
                            }
                        )
                        else -> PingMark()
                    }
                }
            }
            if (onForget != null) {
                IconButton(
                    onClick = { confirmForget = true },
                    modifier = Modifier.size(38.dp)
                ) { TrashMark() }
            }
        }
    }
}

// ────────────────────────── اجزای مشترک ──────────────────────────

/**
 * دکمه‌ی بازگشت.
 *
 * قبلاً یک TextButton ساده بود و در صفحه گم می‌شد. حالا کادر و
 * پس‌زمینه دارد تا جای زدنش مشخص باشد.
 */
@Composable
fun BackButton(onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NaranColors.Raise)
            .border(1.dp, NaranColors.Edge, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (T.isRtl) "→" else "←",
            color = NaranColors.Glow,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(7.dp))
        Text(T.back, color = NaranColors.Text, fontSize = 14.sp)
    }
}

/** پس‌زمینه‌ی همه‌ی صفحات: شب عمیق با تابش ملایم از بالا. */
@Composable
fun ScreenBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(NaranColors.Night)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .align(Alignment.TopCenter)
                .background(NaranColors.screenGlow)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .background(NaranColors.screenGlowLow)
        )
        content()
    }
}

@Composable
fun NeonButton(
    label: String,
    enabled: Boolean = true,
    busy: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .height(54.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(
                if (enabled) NaranColors.powerOn
                else Brush.linearGradient(listOf(NaranColors.Raise, NaranColors.Raise))
            )
            .clickable(enabled = enabled && !busy, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            CircularProgressIndicator(
                Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF1A1002)
            )
        } else {
            Text(
                label,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (enabled) Color(0xFF1A1002) else NaranColors.Muted
            )
        }
    }
}

@Composable
private fun PowerButton(connected: Boolean, connecting: Boolean, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.30f, targetValue = 0.80f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "glow"
    )
    val ring by pulse.animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            tween(2400, easing = LinearEasing), RepeatMode.Reverse
        ),
        label = "ring"
    )

    val lit = connected || connecting
    val alpha = if (lit) glow else 0.10f

    Box(contentAlignment = Alignment.Center) {
        // هاله‌ی بیرونی
        Box(
            Modifier
                .size((236 * ring).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            NaranColors.Glow.copy(alpha = alpha * 0.34f),
                            NaranColors.Violet.copy(alpha = alpha * 0.22f),
                            NaranColors.Cyan.copy(alpha = alpha * 0.10f),
                            Color.Transparent
                        )
                    )
                )
                .blur(28.dp)
        )
        // حلقه‌ی نئون
        Box(
            Modifier
                .size(184.dp)
                .clip(CircleShape)
                .background(
                    if (lit) NaranColors.haloOn
                    else Brush.linearGradient(listOf(NaranColors.Edge, NaranColors.Edge))
                )
        )
        // دکمه
        Box(
            Modifier
                .size(168.dp)
                .clip(CircleShape)
                .background(
                    if (connected) NaranColors.powerOn
                    else Brush.linearGradient(
                        listOf(NaranColors.Surface, NaranColors.Night)
                    )
                )
                .clickable(enabled = !connecting, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (connecting) {
                CircularProgressIndicator(
                    Modifier.size(34.dp), strokeWidth = 2.5.dp, color = NaranColors.Glow
                )
            } else {
                Text(
                    if (connected) T.disconnect else T.connect,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (connected) Color(0xFF1A1002) else NaranColors.Text
                )
            }
        }
    }
}

@Composable
private fun Lamp(big: Boolean = false) {
    val s = if (big) 18.dp else 11.dp
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(s * 4)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            NaranColors.Glow.copy(alpha = 0.38f),
                            NaranColors.Violet.copy(alpha = 0.20f),
                            NaranColors.Cyan.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
                .blur(10.dp)
        )
        Box(
            Modifier
                .size(s)
                .clip(CircleShape)
                .background(NaranColors.powerOn)
        )
    }
}

@Composable
private fun Metric(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(15.dp))
            .background(NaranColors.Surface)
            .border(1.dp, NaranColors.Edge, RoundedCornerShape(15.dp))
            .padding(14.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = accent)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text,
        fontSize = 12.sp,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 11.dp, vertical = 4.dp)
    )
}

/**
 * بنر تبلیغاتی.
 *
 * تصویر ممکن است نیاید — دامنه فیلتر شده باشد یا بنر تصویر نداشته باشد.
 * در آن حالت فقط متن نشان داده می‌شود و کادر خالی نمی‌ماند.
 */
@Composable
private fun AdBanner(ad: NaranAd, onOpen: (String) -> Unit) {
    val url = remember(ad) { ad.fullImageUrl(NaranStore.mediaBase) }
    var imageFailed by remember(ad.id) { mutableStateOf(false) }
    val hasText = ad.title.isNotBlank() || ad.body.isNotBlank()

    if (url.isBlank() && !hasText) return

    Surface(
        onClick = { if (ad.link.isNotBlank()) onOpen(ad.link) },
        shape = RoundedCornerShape(15.dp),
        color = NaranColors.Surface,
        border = BorderStroke(1.dp, NaranColors.Edge),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Column {
            if (url.isNotBlank() && !imageFailed) {
                coil.compose.AsyncImage(
                    model = url,
                    contentDescription = ad.title.ifBlank { null },
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    onError = { imageFailed = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
                )
            }
            if (hasText) {
                Column(Modifier.padding(15.dp)) {
                    if (ad.title.isNotBlank()) {
                        Text(ad.title, style = MaterialTheme.typography.titleMedium)
                    }
                    if (ad.body.isNotBlank()) {
                        Text(ad.body, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// ────────────────────────── نشانه‌ها ──────────────────────────

/** موج پینگ — سه کمان هم‌مرکز. */
@Composable
private fun PingMark() {
    Canvas(Modifier.size(17.dp)) {
        val c = Offset(size.width * 0.15f, size.height * 0.85f)
        val stroke = Stroke(width = size.width * 0.11f, cap = StrokeCap.Round)
        listOf(0.42f, 0.68f, 0.94f).forEach { r ->
            drawArc(
                color = NaranColors.Muted,
                startAngle = -90f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(c.x - size.width * r, c.y - size.height * r),
                size = Size(size.width * r * 2, size.height * r * 2),
                style = stroke
            )
        }
        drawCircle(NaranColors.Glow, radius = size.width * 0.09f, center = c)
    }
}

/** سطل زباله. */
@Composable
private fun TrashMark() {
    Canvas(Modifier.size(16.dp)) {
        val w = size.width
        val sw = w * 0.11f
        drawLine(NaranColors.Muted, Offset(w * 0.1f, w * 0.24f),
            Offset(w * 0.9f, w * 0.24f), sw, cap = StrokeCap.Round)
        drawLine(NaranColors.Muted, Offset(w * 0.38f, w * 0.24f),
            Offset(w * 0.42f, w * 0.10f), sw, cap = StrokeCap.Round)
        drawLine(NaranColors.Muted, Offset(w * 0.62f, w * 0.24f),
            Offset(w * 0.58f, w * 0.10f), sw, cap = StrokeCap.Round)
        drawLine(NaranColors.Muted, Offset(w * 0.42f, w * 0.10f),
            Offset(w * 0.58f, w * 0.10f), sw, cap = StrokeCap.Round)
        drawLine(NaranColors.Muted, Offset(w * 0.2f, w * 0.3f),
            Offset(w * 0.27f, w * 0.9f), sw, cap = StrokeCap.Round)
        drawLine(NaranColors.Muted, Offset(w * 0.8f, w * 0.3f),
            Offset(w * 0.73f, w * 0.9f), sw, cap = StrokeCap.Round)
        drawLine(NaranColors.Muted, Offset(w * 0.27f, w * 0.9f),
            Offset(w * 0.73f, w * 0.9f), sw, cap = StrokeCap.Round)
    }
}

/** چرخ‌دنده‌ی تنظیمات. */
@Composable
fun GearMark(tint: Color = NaranColors.Muted) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val c = Offset(w / 2, w / 2)
        val sw = w * 0.1f
        drawCircle(tint, radius = w * 0.24f, center = c, style = Stroke(width = sw))
        repeat(8) { i ->
            val a = Math.toRadians(i * 45.0)
            val inner = w * 0.34f
            val outer = w * 0.46f
            drawLine(
                tint,
                Offset(
                    c.x + (Math.cos(a) * inner).toFloat(),
                    c.y + (Math.sin(a) * inner).toFloat()
                ),
                Offset(
                    c.x + (Math.cos(a) * outer).toFloat(),
                    c.y + (Math.sin(a) * outer).toFloat()
                ),
                sw, cap = StrokeCap.Round
            )
        }
    }
}

/** تازه‌سازی — کمان با نوک پیکان. */
@Composable
private fun RefreshMark() {
    Canvas(Modifier.size(17.dp)) {
        val sw = size.width * 0.13f
        val pad = size.width * 0.12f
        drawArc(
            color = NaranColors.Muted,
            startAngle = 40f, sweepAngle = 285f, useCenter = false,
            topLeft = Offset(pad, pad),
            size = Size(size.width - pad * 2, size.height - pad * 2),
            style = Stroke(width = sw, cap = StrokeCap.Round)
        )
        val tip = Offset(size.width * 0.87f, size.height * 0.40f)
        drawLine(NaranColors.Muted, tip,
            Offset(tip.x - size.width * 0.05f, tip.y - size.height * 0.22f),
            sw, cap = StrokeCap.Round)
        drawLine(NaranColors.Muted, tip,
            Offset(tip.x + size.width * 0.14f, tip.y - size.height * 0.06f),
            sw, cap = StrokeCap.Round)
    }
}

/** ذره‌بین جستجو. */
@Composable
private fun SearchMark() {
    Canvas(Modifier.size(16.dp)) {
        val w = size.width
        val sw = w * 0.12f
        drawCircle(
            NaranColors.Cyan,
            radius = w * 0.32f,
            center = Offset(w * 0.42f, w * 0.42f),
            style = Stroke(width = sw)
        )
        drawLine(
            NaranColors.Cyan,
            Offset(w * 0.66f, w * 0.66f),
            Offset(w * 0.92f, w * 0.92f),
            sw, cap = StrokeCap.Round
        )
    }
}

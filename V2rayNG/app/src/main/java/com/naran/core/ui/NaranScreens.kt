package com.naran.core.ui

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naran.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * سه صفحه‌ی اصلی اپ.
 *
 * هیچ‌کدام کانفیگ را نشان نمی‌دهند: کاربر فقط نام و پرچم و پینگ می‌بیند.
 * صفحه‌ی افزودن/ویرایش/اشتراک‌گذاری کانفیگ در فورک حذف شده — به PATCHES.md
 * نگاه کنید.
 */

private fun fa(s: Any) = NaranTraffic.fa(s.toString())

// ────────────────────────── ورود کد ──────────────────────────

@Composable
fun LicenseScreen(
    onActivated: () -> Unit,
    onOpenChannel: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val (channelName, channelUrl) = remember { NaranManager.channel() }
    val ads = NaranManager.adsFor("license")

    Column(
        Modifier
            .fillMaxSize()
            .background(NaranColors.Night)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))
        Lamp(big = true)
        Spacer(Modifier.height(18.dp))

        Text("ناران", style = MaterialTheme.typography.displayLarge.copy(fontSize = 30.sp))
        Spacer(Modifier.height(6.dp))
        Text(
            "کد امروز را از $channelName بردارید و اینجا بگذارید",
            style = MaterialTheme.typography.bodyMedium,
            color = NaranColors.Muted,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(30.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().take(24); error = null },
            placeholder = { Text("کد لایسنس", color = NaranColors.Muted) },
            singleLine = true,
            isError = error != null,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            textStyle = MaterialTheme.typography.titleLarge.copy(
                textAlign = TextAlign.Center, letterSpacing = 6.sp
            ),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NaranColors.Glow,
                unfocusedBorderColor = NaranColors.Edge,
                focusedContainerColor = NaranColors.Surface,
                unfocusedContainerColor = NaranColors.Surface
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error!!, color = NaranColors.Dead, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = {
                busy = true; error = null
                scope.launch {
                    when (val r = NaranManager.activate(code)) {
                        is ActivateResult.Ok -> { busy = false; onActivated() }
                        is ActivateResult.Rejected -> { busy = false; error = r.message }
                        is ActivateResult.Offline -> { busy = false; error = r.message }
                    }
                }
            },
            enabled = !busy && code.length >= 4,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NaranColors.Glow),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(
                    Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF241703)
                )
            } else {
                Text("فعال کن", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = { onOpenChannel(channelUrl) }) {
            Text("رفتن به $channelName", color = NaranColors.Glow)
        }

        Spacer(Modifier.height(24.dp))
        ads.forEach { AdBanner(it, onOpenChannel) }
    }
}

// ────────────────────────── اتصال ──────────────────────────

@Composable
fun ConnectScreen(
    connected: Boolean,
    connecting: Boolean,
    selected: NaranConfig?,
    onToggle: () -> Unit,
    onPickServer: () -> Unit,
    onOpenChannel: (String) -> Unit
) {
    val traffic by NaranTraffic.flow.collectAsState()
    val licenses by NaranManager.licenses.collectAsState()
    var tick by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) { tick = System.currentTimeMillis(); delay(1000) }
    }

    val lic = remember(licenses, selected, tick) {
        licenses.firstOrNull { it.config.id == selected?.id }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(NaranColors.Night)
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
                Spacer(Modifier.width(9.dp))
                Text("ناران", style = MaterialTheme.typography.titleLarge)
            }
            lic?.let {
                Pill(
                    text = "${fa(NaranManager.remaining(it) / 3_600_000)} ساعت مانده",
                    color = if (NaranManager.remaining(it) < 3_600_000)
                        NaranColors.Dead else NaranColors.Live
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        PowerButton(connected = connected, connecting = connecting, onClick = onToggle)

        Spacer(Modifier.height(22.dp))

        Text(
            when {
                connecting -> "در حال اتصال…"
                connected -> "متصل هستید"
                else -> "قطع"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (connected) NaranColors.Live else NaranColors.Muted
        )

        Spacer(Modifier.height(26.dp))

        // انتخاب سرور
        Surface(
            onClick = onPickServer,
            shape = RoundedCornerShape(16.dp),
            color = NaranColors.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, NaranColors.Edge),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        selected?.let { "${it.flag} ${it.name}" } ?: "سروری انتخاب نشده",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        selected?.location?.ifBlank { "—" } ?: "برای انتخاب بزنید",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text("تغییر", color = NaranColors.Glow, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (connected) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("دریافت", NaranTraffic.speed(traffic.downBps), Modifier.weight(1f))
                Metric("ارسال", NaranTraffic.speed(traffic.upBps), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("مصرف این اتصال",
                    NaranTraffic.bytes(traffic.sessionUp + traffic.sessionDown),
                    Modifier.weight(1f))
                Metric("مدت", NaranTraffic.duration(traffic.elapsedMs), Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(20.dp))
        NaranManager.adsFor("connect").forEach { AdBanner(it, onOpenChannel) }
        Spacer(Modifier.height(30.dp))
    }
}

// ────────────────────────── انتخاب سرور ──────────────────────────

@Composable
fun ServerSheet(
    configs: List<NaranConfig>,
    selectedId: Int?,
    onPick: (NaranConfig) -> Unit,
    onAddCode: () -> Unit
) {
    Column(
        Modifier
            .background(NaranColors.Surface)
            .padding(20.dp)
    ) {
        Text("سرورها", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "هر کدی که وارد می‌کنید یک سرور به این فهرست اضافه می‌کند",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))

        if (configs.isEmpty()) {
            Text(
                "هنوز سروری ندارید",
                style = MaterialTheme.typography.bodyMedium,
                color = NaranColors.Muted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 30.dp),
                textAlign = TextAlign.Center
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 380.dp)
            ) {
                items(configs, key = { it.id }) { c ->
                    val on = c.id == selectedId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (on) NaranColors.Raise else NaranColors.Night)
                            .border(
                                1.dp,
                                if (on) NaranColors.GlowDim else NaranColors.Edge,
                                RoundedCornerShape(13.dp)
                            )
                            .clickable { onPick(c) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("${c.flag} ${c.name}",
                                style = MaterialTheme.typography.titleMedium)
                            Text(c.location.ifBlank { c.protocol },
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (on) Text("فعال", color = NaranColors.Glow, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onAddCode,
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NaranColors.Raise),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) { Text("وارد کردن کد جدید", color = NaranColors.Text) }
    }
}

// ────────────────────────── اجزای کوچک ──────────────────────────

@Composable
private fun PowerButton(connected: Boolean, connecting: Boolean, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.35f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "glow"
    )
    val alpha = if (connected || connecting) glow else 0.12f

    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(214.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(NaranColors.Glow.copy(alpha = alpha * 0.35f), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier
                .size(158.dp)
                .clip(CircleShape)
                .background(if (connected) NaranColors.Glow else NaranColors.Surface)
                .border(
                    1.5.dp,
                    if (connected) Color.Transparent else NaranColors.Edge,
                    CircleShape
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
                    if (connected) "قطع" else "اتصال",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (connected) Color(0xFF241703) else NaranColors.Text
                )
            }
        }
    }
}

@Composable
private fun Lamp(big: Boolean = false) {
    val s = if (big) 16.dp else 11.dp
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(s * 3)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(NaranColors.Glow.copy(alpha = 0.30f), Color.Transparent)
                    )
                )
        )
        Box(Modifier.size(s).clip(CircleShape).background(NaranColors.Glow))
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(NaranColors.Surface)
            .border(1.dp, NaranColors.Edge, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium)
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
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 11.dp, vertical = 4.dp)
    )
}

/**
 * بنر تبلیغاتی.
 *
 * تصویر ممکن است نیاید — دامنه فیلتر شده باشد یا بنر اصلاً تصویر نداشته
 * باشد. در آن حالت فقط متن نشان داده می‌شود و کادر خالی نمی‌ماند.
 * برای بارگذاری تصویر Coil لازم است:
 *   implementation("io.coil-kt:coil-compose:2.6.0")
 */
@Composable
private fun AdBanner(ad: NaranAd, onOpen: (String) -> Unit) {
    val url = remember(ad) { ad.fullImageUrl(NaranStore.mediaBase) }
    var imageFailed by remember(ad.id) { mutableStateOf(false) }
    val hasText = ad.title.isNotBlank() || ad.body.isNotBlank()

    if (url.isBlank() && !hasText) return

    Surface(
        onClick = { if (ad.link.isNotBlank()) onOpen(ad.link) },
        shape = RoundedCornerShape(14.dp),
        color = NaranColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, NaranColors.Edge),
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

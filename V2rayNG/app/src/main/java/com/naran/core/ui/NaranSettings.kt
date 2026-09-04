package com.naran.core.ui

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naran.core.NaranManager
import com.naran.core.NaranRelease
import com.naran.core.NaranStore

/**
 * تنظیمات.
 *
 * فقط چیزهایی که کاربر واقعاً لازم دارد. هرچه بیشتر بگذاریم، احتمال
 * اینکه کسی جایی را خراب کند و بعد فکر کند اپ خراب است بیشتر می‌شود.
 */
@Composable
fun SettingsScreen(
    versionName: String,
    update: NaranRelease?,
    onBack: () -> Unit,
    onPerApp: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onCheckUpdate: () -> Unit,
    onLog: () -> Unit
) {
    var auto by remember { mutableStateOf(NaranStore.autoConnect) }
    val (channelName, channelUrl) = remember { NaranManager.channel() }
    val licenses by NaranManager.licenses.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(NaranColors.Night)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("بازگشت", color = NaranColors.Glow)
            }
            Spacer(Modifier.weight(1f))
            Text("تنظیمات", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(22.dp))

        Group("اتصال") {
            ToggleRow(
                title = "اتصال خودکار",
                subtitle = "با باز شدن اپ، به آخرین سرور وصل شود",
                checked = auto,
                onChange = { auto = it; NaranStore.autoConnect = it }
            )
            Divider()
            ActionRow(
                title = "اپ‌های خارج از تونل",
                subtitle = "اپ‌های ایرانی و بانکی از تونل رد نشوند",
                onClick = onPerApp
            )
        }

        Spacer(Modifier.height(14.dp))

        Group("سرورها") {
            InfoRow("سرورهای شما", "${fa(licenses.size)} سرور فعال")
            Divider()
            ActionRow(
                title = channelName,
                subtitle = "گرفتن کد تازه",
                onClick = { onOpenChannel(channelUrl) }
            )
        }

        Spacer(Modifier.height(14.dp))

        Group("درباره") {
            if (update != null) {
                ActionRow(
                    title = "نسخه‌ی ${update.versionName} آمده",
                    subtitle = update.changelog.ifBlank { "برای دریافت بزنید" },
                    highlight = true,
                    onClick = { onOpenChannel(update.apkUrl) }
                )
                Divider()
            }
            ActionRow(
                title = "گزارش",
                subtitle = "برای وقتی چیزی کار نمی‌کند",
                onClick = onLog
            )
            Divider()
            ActionRow(
                title = "نسخه‌ی فعلی",
                subtitle = fa(versionName),
                onClick = onCheckUpdate
            )
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "ناران",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(30.dp))
    }
}

// ── اجزا ──

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.bodySmall,
        color = NaranColors.Muted,
        modifier = Modifier.padding(start = 4.dp, bottom = 7.dp)
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NaranColors.Surface)
            .border(1.dp, NaranColors.Edge, RoundedCornerShape(16.dp)),
        content = content
    )
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp)
            .height(1.dp)
            .background(NaranColors.Edge)
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NaranColors.Night,
                checkedTrackColor = NaranColors.Glow,
                uncheckedThumbColor = NaranColors.Muted,
                uncheckedTrackColor = NaranColors.Night,
                uncheckedBorderColor = NaranColors.Edge
            )
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (highlight) NaranColors.Glow else NaranColors.Text
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Text("‹", color = NaranColors.Muted, fontSize = 20.sp)
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naran.core.NaranManager
import com.naran.core.NaranRelease
import com.naran.core.NaranStore
import com.naran.core.T

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
    onLog: () -> Unit,
    onDonate: () -> Unit,
    onSubs: () -> Unit,
    onLanguageChanged: () -> Unit
) {
    var auto by remember { mutableStateOf(NaranStore.autoConnect) }
    var lang by remember { mutableStateOf(T.lang) }
    val (channelName, channelUrl) = remember(lang) { NaranManager.channel() }
    val licenses by NaranManager.licenses.collectAsState()

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
                Text(T.settings, style = MaterialTheme.typography.titleLarge)
            }

            Spacer(Modifier.height(22.dp))

            Group(T.connection, NaranColors.Glow) {
                ToggleRow(
                    title = T.autoConnect,
                    subtitle = T.autoConnectSub,
                    checked = auto,
                    onChange = { auto = it; NaranStore.autoConnect = it }
                )
                Line()
                ActionRow(T.perApp, T.perAppSub, onClick = onPerApp)
            }

            Spacer(Modifier.height(14.dp))

            Group(T.language, NaranColors.Cyan) {
                Row(
                    Modifier.padding(13.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    LangChoice("English", lang == T.Lang.EN, Modifier.weight(1f)) {
                        T.set(T.Lang.EN); lang = T.Lang.EN; onLanguageChanged()
                    }
                    LangChoice("فارسی", lang == T.Lang.FA, Modifier.weight(1f)) {
                        T.set(T.Lang.FA); lang = T.Lang.FA; onLanguageChanged()
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Group(T.servers, NaranColors.Glow) {
                InfoRow(T.yourServers, "${T.num(licenses.size)} ${T.active}")
                Line()
                ActionRow(T.subscriptions, T.addSub, onClick = onSubs)
                Line()
                ActionRow(T.donate, T.donateSub, accent = NaranColors.Cyan,
                    onClick = onDonate)
                Line()
                ActionRow(channelName, T.tapToGet,
                    onClick = { onOpenChannel(channelUrl) })
            }

            Spacer(Modifier.height(14.dp))

            Group(T.about, NaranColors.Glow) {
                if (update != null) {
                    ActionRow(
                        title = T.updateReady(update.versionName),
                        subtitle = update.changelog.ifBlank { T.tapToGet },
                        accent = NaranColors.Glow,
                        onClick = { onOpenChannel(update.apkUrl) }
                    )
                    Line()
                }
                ActionRow(T.report, T.reportSub, onClick = onLog)
                Line()
                ActionRow(T.currentVersion, T.num(versionName), onClick = onCheckUpdate)
            }

            Spacer(Modifier.height(28.dp))
            Text(
                T.appName,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}

// ── اجزا ──

@Composable
private fun Group(
    title: String,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(
        Modifier.padding(start = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(3.dp, 13.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.bodySmall, color = accent)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(NaranColors.Surface)
            .border(1.dp, NaranColors.Edge, RoundedCornerShape(17.dp)),
        content = content
    )
}

@Composable
private fun Line() {
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
    accent: Color = NaranColors.Text,
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
            Text(title, style = MaterialTheme.typography.titleMedium, color = accent)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Text(if (T.isRtl) "‹" else "›", color = NaranColors.Muted, fontSize = 20.sp)
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
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LangChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) NaranColors.Raise else NaranColors.Night)
            .border(
                1.dp,
                if (selected) NaranColors.Cyan else NaranColors.Edge,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = if (selected) NaranColors.Cyan else NaranColors.Muted
        )
    }
}

package com.naran.core.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naran.core.NaranLog
import com.naran.core.NaranStore

/**
 * نمایش لاگ.
 *
 * همه‌ی خطوط از فیلتر سانسور رد شده‌اند، ولی این ضمانت مطلق نیست — به
 * همین دلیل بالای صفحه هشدارش هست. لاگ هسته پیش‌فرض خاموش است.
 */
@Composable
fun LogScreen(onBack: () -> Unit) {
    val entries by NaranLog.entries.collectAsState()
    val listState = rememberLazyListState()
    val ctx = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    var coreOn by remember { mutableStateOf(NaranStore.coreLog) }

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(NaranColors.Night)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("بازگشت", color = NaranColors.Glow)
            }
            Spacer(Modifier.weight(1f))
            Text("گزارش", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(14.dp))

        Text(
            "آدرس سرور، شناسه‌ها و کلیدها از این متن پاک می‌شوند. با این حال " +
                "قبل از فرستادن برای کسی، یک نگاه بیندازید.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NaranColors.Surface)
                .border(1.dp, NaranColors.Edge, RoundedCornerShape(12.dp))
                .padding(13.dp)
        )

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NaranColors.Surface)
                .border(1.dp, NaranColors.Edge, RoundedCornerShape(12.dp))
                .padding(start = 13.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("گزارش هسته", style = MaterialTheme.typography.titleMedium)
                Text(
                    "فقط برای عیب‌یابی. بعدش خاموشش کنید.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = coreOn,
                onCheckedChange = {
                    coreOn = it
                    NaranStore.coreLog = it
                    NaranLog.i("گزارش", if (it) "گزارش هسته روشن شد" else "خاموش شد")
                    if (it) {
                        NaranLog.captureCoreLog().forEach { line ->
                            NaranLog.i("هسته", line)
                        }
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NaranColors.Night,
                    checkedTrackColor = NaranColors.Glow,
                    uncheckedThumbColor = NaranColors.Muted,
                    uncheckedTrackColor = NaranColors.Night,
                    uncheckedBorderColor = NaranColors.Edge
                )
            )
        }

        Spacer(Modifier.height(12.dp))

        Box(Modifier.weight(1f)) {
            if (entries.isEmpty()) {
                Text(
                    "هنوز چیزی ثبت نشده",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 50.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(entries) { e ->
                        Text(
                            e.format(),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 17.sp,
                            color = when (e.level) {
                                NaranLog.Level.ERROR -> NaranColors.Dead
                                NaranLog.Level.WARN -> NaranColors.Warn
                                else -> NaranColors.Muted
                            }
                        )
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("naran", NaranLog.dump()))
                    copied = true
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NaranColors.Raise),
                modifier = Modifier.weight(1f)
            ) { Text(if (copied) "کپی شد" else "کپی", color = NaranColors.Text) }

            Button(
                onClick = { NaranLog.clear(); copied = false },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NaranColors.Raise),
                modifier = Modifier.weight(1f)
            ) { Text("پاک کردن", color = NaranColors.Muted) }
        }
    }
}

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.naran.core.DonateResult
import com.naran.core.NaranManager
import com.naran.core.NaranStore
import com.naran.core.T
import kotlinx.coroutines.launch

/**
 * فرم اهدای کانفیگ.
 *
 * اسم و تلگرام از دفعه‌ی قبل پر می‌شوند تا کسی که چند سرور می‌دهد هر بار
 * تایپ نکند.
 */
@Composable
fun DonateScreen(
    minGb: Int,
    onBack: () -> Unit,
    onDone: (String) -> Unit
) {
    var raw by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var donor by remember { mutableStateOf(NaranStore.donorName) }
    var tg by remember { mutableStateOf(NaranStore.donorTelegram) }
    var capacity by remember { mutableStateOf("10") }
    var unlimited by remember { mutableStateOf(true) }
    var limitGb by remember { mutableStateOf(minGb.toString()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(NaranColors.Night)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Spacer(Modifier.weight(1f))
            Text(T.donate, style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(10.dp))
        Text(T.donateSub, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(20.dp))

        Field(T.donateConfig, raw, { raw = it; error = null },
            placeholder = "vless://…", multiline = true, ltr = true)

        Field(T.donateName, name, { name = it }, placeholder = T.donateNameHint)

        Field(T.donorName, donor, { donor = it }, placeholder = T.donorNameHint)

        Field("${T.telegram} (${T.optional})", tg, { tg = it.removePrefix("@") },
            placeholder = "username", ltr = true)

        Field(T.capacity, capacity,
            { capacity = it.filter(Char::isDigit).take(4) },
            placeholder = "10", numeric = true)

        Spacer(Modifier.height(18.dp))
        Text(T.volumeLimit, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Choice(T.unlimited, unlimited, Modifier.weight(1f)) { unlimited = true }
            Choice(T.limitedGb(minGb), !unlimited, Modifier.weight(1f)) { unlimited = false }
        }

        if (!unlimited) {
            Field(T.gigabytes, limitGb,
                { limitGb = it.filter(Char::isDigit).take(7) },
                placeholder = minGb.toString(), numeric = true)
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = NaranColors.Dead,
                style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(22.dp))

        Button(
            onClick = {
                busy = true; error = null
                scope.launch {
                    val r = NaranManager.donate(
                        raw = raw,
                        configName = name.ifBlank { "—" },
                        donorName = donor,
                        telegram = tg,
                        capacity = capacity.toIntOrNull() ?: 10,
                        limitGb = limitGb.toIntOrNull() ?: minGb,
                        unlimited = unlimited
                    )
                    busy = false
                    when (r) {
                        is DonateResult.Ok -> onDone(r.message)
                        is DonateResult.Rejected -> error = r.message
                        is DonateResult.Offline -> error = r.message
                    }
                }
            },
            enabled = !busy && raw.length > 12,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NaranColors.Glow),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(
                    Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF1A1002))
            } else {
                Text(T.sendDonation, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(36.dp))
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    multiline: Boolean = false,
    numeric: Boolean = false,
    ltr: Boolean = false
) {
    Spacer(Modifier.height(16.dp))
    Text(label, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(7.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, color = NaranColors.Muted, fontSize = 14.sp) },
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text
        ),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = NaranColors.Text,
            textDirection = if (ltr) androidx.compose.ui.text.style.TextDirection.Ltr
                            else androidx.compose.ui.text.style.TextDirection.Content
        ),
        shape = RoundedCornerShape(13.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NaranColors.Glow,
            unfocusedBorderColor = NaranColors.Edge,
            focusedContainerColor = NaranColors.Surface,
            unfocusedContainerColor = NaranColors.Surface,
            cursorColor = NaranColors.Glow
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Choice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) NaranColors.Raise else NaranColors.Surface)
            .border(
                1.dp,
                if (selected) NaranColors.Glow else NaranColors.Edge,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (selected) NaranColors.Glow else NaranColors.Muted
        )
    }
}

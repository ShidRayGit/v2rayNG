package com.naran.core.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.naran.core.*
import com.v2ray.ang.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * تنها ورودی اپ.
 *
 * اگر کاربر هیچ سروری ندارد، صفحه‌ی کد لایسنس می‌آید. اگر دارد، صفحه‌ی
 * اتصال. صفحات خود v2rayNG هیچ‌وقت باز نمی‌شوند.
 */
class NaranActivity : ComponentActivity() {

    private var pendingConfig: NaranConfig? = null

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) doStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // جلوی اسکرین‌شات و ضبط صفحه را می‌گیرد
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        NaranManager.init(this, BuildConfig.VERSION_NAME)
        NaranServiceState.register(this)

        setContent { NaranTheme { Root() } }

        lifecycleScope.launch { NaranManager.sync(force = true) }
    }

    override fun onDestroy() {
        NaranServiceState.unregister(this)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        NaranManager.refreshLocal()
        NaranManager.refreshPublic()
        lifecycleScope.launch { NaranManager.sync() }
    }

    private fun doStart() {
        pendingConfig ?: return
        NaranServiceState.markConnecting()
        NaranBridge.start(this)
    }

    private fun connect(config: NaranConfig) {
        pendingConfig = config
        NaranServiceState.markConnecting()
        val intent = NaranBridge.prepareConnect(this, config)
        if (intent == null) doStart() else vpnPermission.launch(intent)
    }

    private fun disconnect() {
        NaranServiceState.markStopping()
        NaranBridge.stop(this)
        NaranTraffic.stop()
        NaranProbe.clear()
    }

    private fun openLink(url: String) {
        if (url.isBlank()) return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Root() {
        val licenses by NaranManager.licenses.collectAsState()
        val publics by NaranManager.publicConfigs.collectAsState()

        var selected by remember { mutableStateOf<NaranConfig?>(null) }
        var showPicker by remember { mutableStateOf(false) }
        var showLicense by remember { mutableStateOf(false) }
        val own = remember(licenses) { licenses.map { it.config } }
        val all = remember(own, publics) { own + publics.map { it.config } }

        // وضعیت از broadcast خود سرویس می‌آید، نه از پول کردن. سرویس در
        // پروسه‌ی جداست و خواندن مستقیمش همیشه «خاموش» می‌داد.
        val svcState by NaranServiceState.state.collectAsState()
        val running = svcState == NaranServiceState.State.ON
        val connecting = svcState == NaranServiceState.State.CONNECTING
        val failed = svcState == NaranServiceState.State.FAILED

        LaunchedEffect(running) {
            if (running) {
                NaranTraffic.start(lifecycleScope)
                delay(1500)          // فرصت به تونل تا بالا بیاید
                NaranProbe.run()
            } else {
                NaranTraffic.stop()
                NaranProbe.clear()
            }
        }

        // اگر سرور انتخاب‌شده منقضی شد، برو سراغ اولی
        LaunchedEffect(all) {
            if (selected == null || all.none { it.id == selected!!.id }) {
                selected = all.firstOrNull()
            }
            NaranBridge.pruneRemoved(all.map { it.id }.toSet())
        }

        val needsCode = all.isEmpty()

        Box(Modifier.fillMaxSize()) {
            if (needsCode || showLicense) {
                LicenseScreen(
                    onActivated = { showLicense = false },
                    onOpenChannel = ::openLink
                )
            } else {
                ConnectScreen(
                    connected = running,
                    connecting = connecting,
                    failed = failed,
                    selected = selected,
                    onToggle = {
                        if (running) disconnect() else selected?.let { connect(it) }
                    },
                    onPickServer = { showPicker = true },
                    onRefreshProbe = { lifecycleScope.launch { NaranProbe.run() } },
                    onOpenChannel = ::openLink
                )
            }

            if (showPicker) {
                ModalBottomSheet(
                    onDismissRequest = { showPicker = false },
                    sheetState = rememberModalBottomSheetState(true),
                    containerColor = NaranColors.Surface
                ) {
                    ServerSheet(
                        configs = own,
                        publicConfigs = publics,
                        selectedId = selected?.id,
                        onPick = {
                            selected = it
                            showPicker = false
                            if (running) disconnect()
                        },
                        onPing = { NaranServiceState.requestPing(this@NaranActivity) },
                        onAddCode = { showPicker = false; showLicense = true }
                    )
                }
            }
        }
    }
}

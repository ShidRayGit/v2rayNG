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
        when (val p = NaranBridge.prepareConnect(this, config)) {
            is NaranBridge.Prepared.Ready -> doStart()
            is NaranBridge.Prepared.NeedsPermission -> vpnPermission.launch(p.intent)
            is NaranBridge.Prepared.Failed -> NaranServiceState.fail(p.reason)
        }
    }

    private fun disconnect() {
        NaranServiceState.markStopping()
        NaranBridge.stop(this)
        NaranTraffic.stop()
        NaranProbe.clear()
    }

    /** صفحه‌ی انتخاب اپ‌های خارج از تونل — از خود v2rayNG. */
    private fun openPerAppProxy() {
        runCatching {
            startActivity(
                Intent(
                    this,
                    com.v2ray.ang.ui.perappproxy.PerAppProxyActivity::class.java
                )
            )
        }
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
        var showSettings by remember { mutableStateOf(false) }
        var autoTried by remember { mutableStateOf(false) }
        val own = remember(licenses) { licenses.map { it.config } }
        val all = remember(own, publics) { own + publics.map { it.config } }

        // وضعیت از broadcast خود سرویس می‌آید، نه از پول کردن. سرویس در
        // پروسه‌ی جداست و خواندن مستقیمش همیشه «خاموش» می‌داد.
        val svcState by NaranServiceState.state.collectAsState()
        val running = svcState == NaranServiceState.State.ON
        val connecting = svcState == NaranServiceState.State.CONNECTING
        val failed = svcState == NaranServiceState.State.FAILED
        val errorText by NaranServiceState.error.collectAsState()

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

        // انتخاب سرور: آخرین انتخاب کاربر، وگرنه اولی
        LaunchedEffect(all) {
            if (selected == null || all.none { it.id == selected!!.id }) {
                selected = all.firstOrNull { it.id == NaranStore.lastServer }
                    ?: all.firstOrNull()
            }
            NaranBridge.pruneRemoved(all.map { it.id }.toSet())
        }

        // اتصال خودکار، فقط یک بار در هر بار باز شدن اپ
        LaunchedEffect(selected, svcState) {
            if (!autoTried && NaranStore.autoConnect &&
                selected != null && svcState == NaranServiceState.State.OFF
            ) {
                autoTried = true
                selected?.let { connect(it) }
            }
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
                    errorText = errorText,
                    selected = selected,
                    onToggle = {
                        if (running) disconnect() else selected?.let { connect(it) }
                    },
                    onPickServer = { showPicker = true },
                    onRefreshProbe = { lifecycleScope.launch { NaranProbe.run() } },
                    onSettings = { showSettings = true },
                    onOpenChannel = ::openLink
                )
            }

            if (showSettings) {
                SettingsScreen(
                    versionName = BuildConfig.VERSION_NAME,
                    update = NaranManager.updateAvailable(BuildConfig.VERSION_CODE),
                    onBack = { showSettings = false },
                    onPerApp = { openPerAppProxy() },
                    onOpenChannel = ::openLink,
                    onCheckUpdate = {
                        lifecycleScope.launch { NaranManager.sync(force = true) }
                    }
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
                            NaranStore.lastServer = it.id
                            showPicker = false
                            if (running) disconnect()
                        },
                        onPing = { NaranServiceState.requestPing(this@NaranActivity) },
                        onForget = { cfg ->
                            if (running && selected?.id == cfg.id) disconnect()
                            NaranManager.forget(cfg.id)
                        },
                        onAddCode = { showPicker = false; showLicense = true }
                    )
                }
            }
        }
    }
}

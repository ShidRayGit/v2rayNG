package com.naran.core.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import android.content.Context
import android.os.PowerManager
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

    /** برای اینکه تغییر زبان بلافاصله دیده شود. */
    private val langTick = mutableStateOf(0)

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) doStart()
        else NaranServiceState.markStopping()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // حافظه باید قبل از خواندن هر تنظیمی آماده باشد. این تابع
        // idempotent است، پس صدا زدنش دوباره در NaranManager.init ضرری
        // ندارد — ولی نبودنش اینجا یعنی کرش در همان لحظه‌ی باز شدن.
        NaranStore.init(this)

        // ممنوعیت اسکرین‌شات از پنل کنترل می‌شود، پیش‌فرض آزاد. چون این
        // پرچم فقط موقع ساخته شدن پنجره اعمال می‌شود، تغییرش در پنل بعد
        // از بستن و باز کردن اپ دیده می‌شود.
        if (NaranStore.blockScreenshot) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        // فایل‌های مسیریابی را از APK به app_assets کپی می‌کند. v2rayNG این
        // را در MainActivity انجام می‌دهد و چون آن را دور زدیم، هسته
        // geosite.dat را پیدا نمی‌کرد و همان لحظه می‌مرد.
        runCatching {
            com.v2ray.ang.handler.SettingsManager.initAssets(this, assets)
        }.onFailure { NaranLog.e("راه‌اندازی", "کپی فایل‌های مسیریابی ناموفق") }

        NaranManager.init(this, BuildConfig.VERSION_NAME)
        NaranServiceState.register(this)
        NaranTest.register(this)

        setContent {
            key(langTick.value) {
                NaranTheme { Root() }
            }
        }

        lifecycleScope.launch {
            NaranManager.sync(force = true)
            NaranManager.flushUsage()
            NaranSubs.refreshAll(force = true)     // هر بار باز شدن اپ
        }
    }

    /** وقتی صفحه قفل است به‌روزرسانی نمی‌کنیم — باتری بی‌خود می‌رود. */
    private fun screenOn(): Boolean = runCatching {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
    }.getOrDefault(true)

    override fun onDestroy() {
        NaranTest.unregister()
        NaranServiceState.unregister(this)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        NaranManager.refreshLocal()
        NaranManager.refreshPublic()
        lifecycleScope.launch {
            NaranManager.sync()
            NaranManager.flushUsage()
            NaranSubs.refreshAll(force = true)
        }
    }

    override fun onPause() {
        // مصرف تجمیع‌شده را از دست ندهیم اگر اپ بسته شود
        lifecycleScope.launch { NaranManager.flushUsage() }
        super.onPause()
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
        lifecycleScope.launch { NaranManager.flushUsage() }
    }

    /** صفحه‌ی انتخاب اپ‌های خارج از تونل — از خود v2rayNG. */
    private fun openPerAppProxy() {
        runCatching {
            startActivity(
                Intent(this, com.v2ray.ang.ui.perappproxy.PerAppProxyActivity::class.java)
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
        var showLog by remember { mutableStateOf(false) }
        var showDonate by remember { mutableStateOf(false) }
        var showSubs by remember { mutableStateOf(false) }
        var searching by remember { mutableStateOf(false) }
        var pingingSub by remember { mutableStateOf<String?>(null) }
        var autoTried by remember { mutableStateOf(false) }

        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        val subs by NaranSubs.subs.collectAsState()
        val notices by NaranManager.notices.collectAsState()

        val own = remember(licenses) { licenses.map { it.config } }
        // کانفیگ‌های ساب هم قابل انتخاب‌اند. شناسه‌شان از هش لینک می‌آید
        // تا با کانفیگ‌های لایسنسی برخورد نکند.
        val subConfigs = remember(subs) {
            subs.flatMap { s ->
                s.configs.map { c ->
                    NaranConfig(
                        id = 700_000 + Math.abs((s.id + c.raw).hashCode() % 200_000),
                        name = c.name,
                        location = s.title.ifBlank { s.domain },
                        flag = "", protocol = c.raw.substringBefore("://"),
                        raw = c.raw
                    )
                }
            }
        }
        val all = remember(own, publics, subConfigs) {
            own + subConfigs + publics.map { it.config }
        }

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

        // اتصال ناموفق روی سرور اهدایی را به سرور گزارش می‌کنیم تا کنار
        // گذاشته شود و بقیه گرفتارش نشوند.
        LaunchedEffect(failed) {
            if (failed) selected?.let { NaranManager.trackFailure(it) }
        }

        // مصرف را هر دقیقه تجمیع و هر ۵ دقیقه ارسال می‌کنیم
        LaunchedEffect(running, selected) {
            if (!running || selected == null) return@LaunchedEffect
            var lastTotal = 0L
            var ticks = 0
            while (true) {
                delay(60_000)
                val snap = NaranTraffic.flow.value
                val total = snap.sessionUp + snap.sessionDown
                val delta = total - lastTotal
                if (delta > 0) {
                    selected?.let { NaranManager.trackUsage(it, delta) }
                    lastTotal = total
                }
                if (++ticks >= 5) { ticks = 0; NaranManager.flushUsage() }
            }
        }

        // به‌روزرسانی دوره‌ای ساب، فقط وقتی صفحه روشن است
        LaunchedEffect(Unit) {
            while (true) {
                delay(5 * 60_000)
                if (screenOn()) NaranSubs.refreshAll()
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

        val selectedIsPublic = selected?.let { s ->
            publics.any { it.config.id == s.id }
        } ?: false

        Box(Modifier.fillMaxSize()) {
            when {
                showDonate -> DonateScreen(
                    minGb = 500,
                    onBack = { showDonate = false },
                    onDone = { msg ->
                        showDonate = false
                        scope.launch { snackbar.showSnackbar(msg) }
                    }
                )

                showLog -> LogScreen(onBack = { showLog = false })

                showSettings -> SettingsScreen(
                    versionName = BuildConfig.VERSION_NAME,
                    update = NaranManager.updateAvailable(BuildConfig.VERSION_CODE),
                    onBack = { showSettings = false },
                    onPerApp = { openPerAppProxy() },
                    onOpenChannel = ::openLink,
                    onCheckUpdate = {
                        lifecycleScope.launch { NaranManager.sync(force = true) }
                    },
                    onLog = { showLog = true },
                    onDonate = { showDonate = true },
                    onSubs = { showSubs = true },
                    onLanguageChanged = { langTick.value++ }
                )

                showSubs -> SubsScreen(
                    onBack = { showSubs = false },
                    pingingId = pingingSub,
                    onPingAll = { sub ->
                        // CoreTestService هر کانفیگ را مستقل بالا می‌آورد،
                        // پس نیازی به وصل بودن نیست.
                        pingingSub = sub.id
                        scope.launch {
                            NaranTest.pingAll(this@NaranActivity, sub)
                            // تا وقتی سرویس تمام‌شدن را اعلام کند
                            while (NaranTest.progress.value.running) delay(500)
                            pingingSub = null
                            NaranSubs.applySort(sub.id)
                        }
                    }
                )

                showLicense -> LicenseScreen(
                    canGoBack = true,
                    onBack = { showLicense = false },
                    onActivated = { showLicense = false },
                    onOpenChannel = ::openLink
                )

                else -> ConnectScreen(
                    connected = running,
                    connecting = connecting,
                    failed = failed,
                    errorText = errorText,
                    selected = selected,
                    isPublic = selectedIsPublic,
                    onToggle = {
                        if (running) disconnect() else selected?.let { connect(it) }
                    },
                    onPickServer = { showPicker = true },
                    onRefreshProbe = { lifecycleScope.launch { NaranProbe.run() } },
                    onPing = { NaranServiceState.requestPing(this@NaranActivity) },
                    onSettings = { showSettings = true },
                    onOpenChannel = ::openLink
                )
            }

            // اطلاعیه‌ها روی همه‌چیز
            notices.firstOrNull()?.let { n ->
                NoticeDialog(n) { NaranManager.dismissNotice(n.id) }
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
                        searching = searching,
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
                        onDiscover = {
                            if (!searching) {
                                searching = true
                                scope.launch {
                                    when (val r = NaranManager.discover()) {
                                        is DiscoverResult.Ok -> {
                                            selected = r.config.config
                                            NaranStore.lastServer = r.config.config.id
                                        }
                                        is DiscoverResult.None ->
                                            snackbar.showSnackbar(r.message)
                                        is DiscoverResult.Offline ->
                                            snackbar.showSnackbar(r.message)
                                    }
                                    searching = false
                                }
                            }
                        },
                        onDonate = { showPicker = false; showDonate = true },
                        onSubs = { showPicker = false; showSubs = true },
                        onAddCode = { showPicker = false; showLicense = true }
                    )
                }
            }

            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

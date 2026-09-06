package com.naran.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * تست تأخیر بدون نیاز به اتصال.
 *
 * v2rayNG سرویس جدایی به اسم CoreTestService دارد که هر کانفیگ را
 * مستقل بالا می‌آورد و می‌سنجد — برخلاف measureDelay که فقط تونل فعلی
 * را می‌سنجد و بدون اتصال کار نمی‌کند.
 *
 * نتیجه در MSG_MEASURE_CONFIG_SUCCESS فقط guid را می‌دهد؛ عدد تأخیر
 * جداگانه در ServerAffiliationInfo نوشته می‌شود و از آنجا خوانده می‌شود.
 *
 * فقط کانفیگ‌های ساب این‌طور تست می‌شوند. کانفیگ‌های لایسنسی وارد مخزن
 * دائمی نمی‌شوند و برای همان مسیر قدیمی می‌مانند: اول وصل شو، بعد پینگ.
 */
object NaranTest {

    private const val SUB_ID = "naran_sub"

    data class Progress(
        val running: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        val note: String = ""
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress

    /** نگاشت guid به لینک خام، تا نتیجه را به کانفیگ درست نسبت بدهیم. */
    private val guidToRaw = mutableMapOf<String, String>()

    private var registered = false
    private var appCtx: Context? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> {
                    val guid = intent.getStringExtra("content").orEmpty()
                    val raw = guidToRaw[guid] ?: return
                    val ms = runCatching {
                        MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis
                    }.getOrNull() ?: -1L
                    NaranSubs.recordPing(raw, ms)
                    _progress.value = _progress.value.copy(
                        done = _progress.value.done + 1
                    )
                }

                AppConfig.MSG_MEASURE_CONFIG_NOTIFY -> {
                    _progress.value = _progress.value.copy(
                        note = intent.getStringExtra("content").orEmpty()
                    )
                }

                AppConfig.MSG_MEASURE_CONFIG_FINISH -> {
                    NaranLog.i("تست", "تمام شد — ${_progress.value.done} کانفیگ")
                    _progress.value = Progress()
                    cleanup()
                }
            }
        }
    }

    fun register(context: Context) {
        if (registered) return
        appCtx = context.applicationContext
        runCatching {
            ContextCompat.registerReceiver(
                appCtx!!, receiver,
                IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
                Utils.receiverFlags()
            )
            registered = true
        }
    }

    fun unregister() {
        if (!registered) return
        runCatching { appCtx?.unregisterReceiver(receiver) }
        registered = false
    }

    /**
     * همه‌ی کانفیگ‌های یک ساب را تست می‌کند.
     *
     * کانفیگ‌ها موقتاً وارد مخزن v2rayNG می‌شوند چون سرویس تست فقط با
     * guid کار می‌کند. بعد از پایان پاک می‌شوند.
     */
    suspend fun pingAll(context: Context, sub: Subscription): Boolean =
        withContext(Dispatchers.IO) {
            if (sub.configs.isEmpty()) return@withContext false
            register(context)
            cleanup()

            val guids = mutableListOf<String>()
            sub.configs.take(60).forEach { c ->
                val before = runCatching {
                    MmkvManager.decodeServerList(SUB_ID).toSet()
                }.getOrDefault(emptySet())

                val ok = runCatching {
                    AngConfigManager.importBatchConfig(c.raw, SUB_ID, true).first > 0
                }.getOrDefault(false)
                if (!ok) return@forEach

                val after = runCatching {
                    MmkvManager.decodeServerList(SUB_ID)
                }.getOrDefault(emptyList())
                val fresh = after.firstOrNull { it !in before } ?: return@forEach

                guidToRaw[fresh] = c.raw
                guids.add(fresh)
            }

            if (guids.isEmpty()) {
                NaranLog.w("تست", "هیچ کانفیگی وارد نشد")
                return@withContext false
            }

            _progress.value = Progress(running = true, done = 0, total = guids.size)
            NaranLog.i("تست", "شروع تست ${guids.size} کانفیگ")

            runCatching {
                MessageHelper.sendMsg2TestService(
                    context.applicationContext,
                    TestServiceMessage(
                        key = AppConfig.MSG_MEASURE_CONFIG_START,
                        subscriptionId = SUB_ID,
                        serverGuids = guids
                    )
                )
                true
            }.getOrElse {
                NaranLog.e("تست", "سرویس تست شروع نشد")
                _progress.value = Progress()
                cleanup()
                false
            }
        }

    fun cancel(context: Context) {
        runCatching {
            MessageHelper.sendMsg2TestService(
                context.applicationContext,
                TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL)
            )
        }
        _progress.value = Progress()
        cleanup()
    }

    /** کانفیگ‌های موقت را از مخزن v2rayNG برمی‌دارد. */
    private fun cleanup() {
        runCatching {
            MmkvManager.decodeServerList(SUB_ID).forEach { MmkvManager.removeServer(it) }
        }
        guidToRaw.clear()
    }
}

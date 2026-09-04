package com.naran.core

import android.app.Activity
import android.content.Context
import android.net.VpnService
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager

/**
 * پل بین ناران و هسته‌ی v2rayNG.
 *
 * کانفیگ‌های ما را به مخزن خودشان تزریق می‌کند و سرویس را با همان مسیری
 * که خودشان استفاده می‌کنند بالا می‌آورد. یعنی هیچ‌جای سرویس VPN دست
 * نمی‌خورد — فقط منبع کانفیگ عوض می‌شود.
 *
 * گروه ناران با subscriptionId ثابت از بقیه جدا نگه داشته می‌شود تا
 * پاک کردنش بقیه را خراب نکند.
 */
object NaranBridge {

    private const val SUB_ID = "naran"

    /** نگاشت شناسه‌ی کانفیگ ناران به guid داخلی v2rayNG. */
    private val guidCache = mutableMapOf<Int, String>()

    /**
     * کانفیگ را در مخزن v2rayNG می‌نشاند و guid می‌دهد.
     * اگر قبلاً وارد شده باشد، همان guid قبلی برمی‌گردد.
     */
    fun ensureImported(config: NaranConfig): String? {
        guidCache[config.id]?.let { existing ->
            if (MmkvManager.decodeServerConfig(existing) != null) return existing
            guidCache.remove(config.id)
        }

        val before = MmkvManager.decodeServerList(SUB_ID).toSet()
        val (count, _) = AngConfigManager.importBatchConfig(config.raw, SUB_ID, true)
        if (count <= 0) {
            NaranLog.e("پل", "کانفیگ پذیرفته نشد — قالبش را بررسی کنید")
            return null
        }

        val after = MmkvManager.decodeServerList(SUB_ID)
        val fresh = after.firstOrNull { it !in before } ?: after.lastOrNull() ?: return null

        // اگر ذخیره شده ولی قابل خواندن نیست، سرویس همان لحظه
        // «Failed to decode server config» می‌دهد. بهتر است اینجا بفهمیم.
        if (MmkvManager.decodeServerConfig(fresh) == null) {
            NaranLog.e("پل", "کانفیگ ذخیره شد ولی خوانده نمی‌شود")
            return null
        }
        NaranLog.i("پل", "کانفیگ «${config.name}» وارد شد")

        guidCache[config.id] = fresh
        return fresh
    }

    /**
     * نتیجه‌ی آماده‌سازی اتصال.
     *
     * قبلاً همه‌ی این حالت‌ها null برمی‌گشت، پس «کانفیگ وارد نشد» با
     * «مجوز لازم نیست» یکی می‌شد و خطا بی‌صدا رد می‌شد.
     */
    sealed class Prepared {
        data object Ready : Prepared()
        data class NeedsPermission(val intent: android.content.Intent) : Prepared()
        data class Failed(val reason: String) : Prepared()
    }

    fun prepareConnect(activity: Activity, config: NaranConfig): Prepared {
        val guid = ensureImported(config)
            ?: return Prepared.Failed("کانفیگ این سرور خوانده نشد")

        MmkvManager.setSelectServer(guid)

        // تأیید کن که واقعاً نشست؛ وگرنه سرویس با «No server selected» می‌میرد
        if (MmkvManager.getSelectServer() != guid) {
            return Prepared.Failed("سرور انتخاب نشد")
        }

        val intent = VpnService.prepare(activity)
        return if (intent == null) Prepared.Ready else Prepared.NeedsPermission(intent)
    }

    fun start(context: Context) {
        LauncherManager.startService(context)
    }

    fun stop(context: Context) {
        LauncherManager.stopService(context)
    }

    fun isRunning(): Boolean =
        runCatching { CoreServiceManager.isRunning() }.getOrDefault(false)

    /**
     * کانفیگ‌هایی که دیگر لایسنس معتبری ندارند از مخزن v2rayNG هم پاک
     * می‌شوند. بدون این، کانفیگ منقضی روی دستگاه می‌ماند.
     */
    fun pruneRemoved(aliveIds: Set<Int>) {
        val stale = guidCache.filterKeys { it !in aliveIds }
        stale.forEach { (id, guid) ->
            runCatching { MmkvManager.removeServer(guid) }
            guidCache.remove(id)
        }
    }
}

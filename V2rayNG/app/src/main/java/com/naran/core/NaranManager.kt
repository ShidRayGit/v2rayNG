package com.naran.core

import android.content.Context
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * مغز کار.
 *
 * تنها جایی که کانفیگ وارد اپ می‌شود و تنها جایی که تصمیم می‌گیرد کِی پاک شود.
 */
object NaranManager {

    private const val SYNC_INTERVAL_MS = 30 * 60 * 1000L
    private const val CLOCK_TOLERANCE_MS = 10 * 60 * 1000L

    private val _licenses = MutableStateFlow<List<NaranLicense>>(emptyList())
    val licenses: StateFlow<List<NaranLicense>> = _licenses

    private val _ads = MutableStateFlow<List<NaranAd>>(emptyList())
    val ads: StateFlow<List<NaranAd>> = _ads

    /** کانفیگ‌های عمومی. خالی بودنش یعنی بخش «اتصال عمومی» اصلاً نباید دیده شود. */
    private val _publicConfigs = MutableStateFlow<List<NaranPublicConfig>>(emptyList())
    val publicConfigs: StateFlow<List<NaranPublicConfig>> = _publicConfigs

    private val _notices = MutableStateFlow<List<NaranNotice>>(emptyList())
    val notices: StateFlow<List<NaranNotice>> = _notices

    private val _blocks = MutableStateFlow<List<NaranBlock>>(emptyList())
    val blocks: StateFlow<List<NaranBlock>> = _blocks

    private var appVersion: String = "1.0.0"
    private var latestRelease: NaranRelease? = null

    fun init(ctx: Context, versionName: String) {
        NaranStore.init(ctx)
        NaranDirect.init(ctx)
        T.init(ctx)
        appVersion = versionName
        _ads.value = NaranStore.ads()
        NaranSubs.load()
        refreshLocal()
        refreshPublic()
    }

    // ── وضعیت محلی ──

    /**
     * لایسنس‌های مرده را پاک می‌کند و بقیه را منتشر می‌کند.
     * هیچ تماس شبکه‌ای ندارد، پس قطعی سرور کاربر را بی‌کار نمی‌کند.
     */
    fun refreshLocal() {
        val alive = NaranStore.licenses().filterNot { isDead(it) }
        if (alive.size != NaranStore.licenses().size) NaranStore.saveLicenses(alive)
        _licenses.value = alive
    }

    /**
     * کانفیگ‌های عمومیِ منقضی‌شده را کنار می‌گذارد.
     *
     * انقضا لوکال حساب می‌شود، پس اگر سرور در دسترس نباشد هم کانفیگ سر
     * وقتش برداشته می‌شود.
     */
    fun refreshPublic() {
        val nowSec = (System.currentTimeMillis() + NaranStore.serverSkew) / 1000
        val alive = NaranStore.publicConfigs().filter { it.isAlive(nowSec) }
        if (alive.size != NaranStore.publicConfigs().size) {
            NaranStore.savePublicConfigs(alive)
        }
        _publicConfigs.value = alive
    }

    /**
     * لایسنس مرده است اگر باطل شده باشد، وقتش تمام شده باشد، یا نشانه‌ی
     * دستکاری ساعت دیده شود.
     *
     * دو سنجه داریم: زمان دیواری (که کاربر می‌تواند عقب ببرد) و
     * elapsedRealtime (که فقط با ری‌بوت صفر می‌شود). اگر این دو با هم
     * نخوانند، یعنی ساعت دست خورده و لایسنس را مرده حساب می‌کنیم.
     */
    fun isDead(lic: NaranLicense): Boolean {
        if (lic.revoked) return true

        val wallNow = System.currentTimeMillis() + NaranStore.serverSkew
        if (wallNow >= lic.expiresAtWall) return true

        // ساعت به قبل از لحظه‌ی فعال‌سازی برگشته
        if (wallNow < lic.activatedWall - CLOCK_TOLERANCE_MS) return true

        // بدون ری‌بوت: مدت سپری‌شده‌ی واقعی را با ساعت دیواری بسنج
        val bootNow = SystemClock.elapsedRealtime()
        if (bootNow >= lic.activatedBoot) {
            val realElapsed = bootNow - lic.activatedBoot
            if (realElapsed >= lic.durationMs) return true

            val claimedElapsed = wallNow - lic.activatedWall
            if (claimedElapsed + CLOCK_TOLERANCE_MS < realElapsed) return true
        }
        return false
    }

    fun remaining(lic: NaranLicense): Long {
        val wallLeft = lic.expiresAtWall - (System.currentTimeMillis() + NaranStore.serverSkew)
        val bootNow = SystemClock.elapsedRealtime()
        val bootLeft = if (bootNow >= lic.activatedBoot)
            lic.durationMs - (bootNow - lic.activatedBoot) else Long.MAX_VALUE
        return maxOf(0L, minOf(wallLeft, bootLeft))
    }

    /** کانفیگ‌های شخصی کاربر — از کدهایی که وارد کرده. */
    fun activeConfigs(): List<NaranConfig> = _licenses.value.map { it.config }

    /** همه‌ی آنچه کاربر می‌تواند به آن وصل شود، شخصی و عمومی. */
    fun allConnectable(): List<NaranConfig> =
        activeConfigs() + _publicConfigs.value.map { it.config }

    /**
     * حذف دستی یک سرور توسط کاربر.
     *
     * فقط از این دستگاه پاک می‌شود؛ لایسنس در پنل دست‌نخورده می‌ماند و
     * کاربر می‌تواند همان کد را دوباره وارد کند. کانفیگ‌های عمومی حذف
     * نمی‌شوند چون از سرور می‌آیند و در sync بعدی برمی‌گردند.
     */
    fun forget(configId: Int): Boolean {
        val target = _licenses.value.firstOrNull { it.config.id == configId }
            ?: return false
        NaranStore.removeLicense(target.id)
        refreshLocal()
        return true
    }

    fun isPublic(configId: Int): Boolean =
        _publicConfigs.value.any { it.config.id == configId }

    // ── فعال‌سازی ──

    suspend fun activate(code: String): ActivateResult = withContext(Dispatchers.IO) {
        val clean = code.trim().uppercase().replace(" ", "")
        if (clean.isEmpty()) {
            return@withContext ActivateResult.Rejected("empty", "کد را وارد کنید")
        }

        val body = JSONObject().apply {
            put("code", clean)
            put("device_id", NaranStore.deviceId())
            put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("app_version", appVersion)
        }

        val json = try {
            NaranApi.race("/api/v1/activate", body, NaranStore.endpoints(), configOnly = true).first
        } catch (e: Exception) {
            return@withContext ActivateResult.Offline(
                "به سرور وصل نشد. اینترنت را بررسی کنید و دوباره بزنید."
            )
        }

        if (!json.optBoolean("ok")) {
            NaranLog.w("لایسنس", "رد شد: ${json.optString("error")}")
            return@withContext ActivateResult.Rejected(
                json.optString("error", "unknown"),
                json.optString("message", "کد پذیرفته نشد")
            )
        }

        val serverNow = json.optLong("server_time") * 1000L
        NaranStore.serverSkew = serverNow - System.currentTimeMillis()

        val expiresWall = json.optLong("expires_at") * 1000L
        val lic = NaranLicense(
            id = json.optInt("license_id"),
            code = clean,
            config = NaranConfig.from(json.getJSONObject("config")),
            expiresAtWall = expiresWall,
            activatedWall = serverNow,
            activatedBoot = SystemClock.elapsedRealtime(),
            durationMs = (expiresWall - serverNow).coerceAtLeast(0L)
        )
        NaranStore.upsertLicense(lic)
        refreshLocal()
        NaranLog.i("لایسنس", "کد پذیرفته شد — سرور «${lic.config.name}»")
        ActivateResult.Ok(lic)
    }

    // ── همگام‌سازی ──

    suspend fun sync(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - NaranStore.lastSync < SYNC_INTERVAL_MS) return@withContext true

        val ids = JSONArray()
        NaranStore.licenses().forEach { ids.put(it.id) }
        val subDomains = JSONArray()
        NaranSubs.domains().forEach { subDomains.put(it) }
        val body = JSONObject().apply {
            put("license_ids", ids)
            put("sub_domains", subDomains)
        }

        val (json, source) = try {
            NaranApi.race("/api/v1/sync", body, NaranStore.endpoints())
        } catch (e: Exception) {
            return@withContext false
        }
        // آدرس تصویر بنرها نسبی است (/media/…) — به همان سروری که جواب
        // داد می‌چسبانیمش، وگرنه اگر دامنه‌ای فیلتر شود تصویرش هم نمی‌آید.
        NaranStore.mediaBase = source

        NaranStore.lastSync = now
        NaranStore.serverSkew = json.optLong("server_time") * 1000L - now

        // لایسنس‌های باطل‌شده: کانفیگشان همین‌جا از دستگاه پاک می‌شود
        json.optJSONArray("licenses")?.let { arr ->
            val revoked = mutableSetOf<Int>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("status") != "active") revoked.add(o.optInt("id"))
            }
            if (revoked.isNotEmpty()) {
                NaranStore.saveLicenses(NaranStore.licenses().filterNot { it.id in revoked })
            }
        }

        json.optJSONArray("endpoints")?.let { arr ->
            val list = (0 until arr.length()).map { NaranEndpoint.from(arr.getJSONObject(it)) }
            if (list.isNotEmpty()) NaranStore.saveEndpoints(list)
        }

        // کانفیگ‌های عمومی: هرچه سرور گفت، همان. اگر آرایه خالی بیاید یعنی
        // در پنل خاموش شده و باید از دستگاه هم برداشته شود.
        json.optJSONArray("public_configs")?.let { arr ->
            val list = (0 until arr.length()).mapNotNull {
                runCatching { NaranPublicConfig.from(arr.getJSONObject(it)) }.getOrNull()
            }
            NaranStore.savePublicConfigs(list)
            _publicConfigs.value = list
        }

        json.optJSONArray("ads")?.let { arr ->
            NaranStore.saveAds(arr)
            _ads.value = (0 until arr.length()).map { NaranAd.from(arr.getJSONObject(it)) }
        }

        json.optJSONObject("channel")?.let {
            NaranStore.saveChannel(it.optString("name"), it.optString("url"))
        }

        json.optJSONObject("release")?.let { latestRelease = NaranRelease.from(it) }

        json.optJSONArray("notices")?.let { arr ->
            val seen = NaranStore.seenNotices()
            _notices.value = (0 until arr.length())
                .map { NaranNotice.from(arr.getJSONObject(it)) }
                .filterNot { it.id in seen }
        }

        json.optJSONArray("blocklist")?.let { arr ->
            _blocks.value = (0 until arr.length())
                .map { NaranBlock.from(arr.getJSONObject(it)) }
        }

        json.optJSONObject("sub")?.let { o ->
            NaranSubs.updateMinutes = o.optInt("update_minutes", 60)
            NaranSubs.sortDefault = o.optBoolean("sort_default", true)
            NaranSubs.pingTieMs = o.optLong("ping_tie_ms", 5L)
        }

        refreshLocal()
        refreshPublic()
        true
    }

    // ── اهدای کانفیگ ──

    suspend fun donate(
        raw: String,
        configName: String,
        donorName: String,
        telegram: String,
        capacity: Int,
        limitGb: Int,
        unlimited: Boolean
    ): DonateResult = withContext(Dispatchers.IO) {
        val clean = raw.trim()
        if (clean.isEmpty() || !clean.contains("://")) {
            return@withContext DonateResult.Rejected("bad_config", T.donateBadLink)
        }

        // دفعه‌ی بعد دوباره نپرسیم
        NaranStore.donorName = donorName.trim()
        NaranStore.donorTelegram = telegram.trim().removePrefix("@")

        val body = JSONObject().apply {
            put("raw", clean)
            put("config_name", configName.trim())
            put("donor_name", donorName.trim())
            put("telegram", telegram.trim().removePrefix("@"))
            put("capacity", capacity)
            put("limit_gb", if (unlimited) 0 else limitGb)
            put("unlimited", unlimited)
            put("device_id", NaranStore.deviceId())
        }

        val json = try {
            NaranApi.race("/api/v1/donate", body, NaranStore.endpoints()).first
        } catch (e: Exception) {
            return@withContext DonateResult.Offline(T.offline)
        }

        if (!json.optBoolean("ok")) {
            NaranLog.w("اهدا", "رد شد: " + json.optString("error"))
            return@withContext DonateResult.Rejected(
                json.optString("error", "unknown"),
                json.optString("message", T.donateBadLink)
            )
        }
        NaranLog.i("اهدا", "کانفیگ فرستاده شد")
        DonateResult.Ok(json.optString("message", T.donateThanks))
    }

    // ── جستجوی سرور عمومی ──

    suspend fun discover(): DiscoverResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("device_id", NaranStore.deviceId())

        val json = try {
            NaranApi.race("/api/v1/discover", body, NaranStore.endpoints(),
                configOnly = true).first
        } catch (e: Exception) {
            return@withContext DiscoverResult.Offline(T.offline)
        }

        if (!json.optBoolean("ok")) {
            return@withContext DiscoverResult.None(
                json.optString("message", T.noneAvailable))
        }

        val cfgJson = json.optJSONObject("config")
            ?: return@withContext DiscoverResult.None(T.noneAvailable)

        val found = NaranPublicConfig.from(cfgJson)
        val list = NaranStore.publicConfigs()
            .filterNot { it.config.id == found.config.id } + found
        NaranStore.savePublicConfigs(list)
        _publicConfigs.value = list
        NaranLog.i("جستجو", "سرور «" + found.config.name + "» پیدا شد")
        DiscoverResult.Ok(found)
    }

    // ── گزارش مصرف ──

    /**
     * مصرف را جمع می‌کند تا دور بعدِ گزارش.
     *
     * کلید یا "c:<configId>" است یا "d:<donationId>" — چون سرور این دو
     * را جدا حساب می‌کند.
     */
    fun trackUsage(config: NaranConfig, deltaBytes: Long) {
        if (deltaBytes <= 0) return
        val pub = _publicConfigs.value.firstOrNull { it.config.id == config.id }
        val key = if (pub != null && pub.donationId > 0) "d:" + pub.donationId
                  else "c:" + config.id
        val m = NaranStore.pendingUsage()
        m[key] = (m[key] ?: 0L) + deltaBytes
        NaranStore.savePendingUsage(m)
    }

    /** اگر اتصال شکست خورد، به سرور بگو تا سرور مرده را کنار بگذارد. */
    fun trackFailure(config: NaranConfig) {
        val pub = _publicConfigs.value.firstOrNull { it.config.id == config.id } ?: return
        if (pub.donationId <= 0) return
        val m = NaranStore.pendingUsage()
        m["f:" + pub.donationId] = (m["f:" + pub.donationId] ?: 0L) + 1
        NaranStore.savePendingUsage(m)
    }

    suspend fun flushUsage(): Boolean = withContext(Dispatchers.IO) {
        val pending = NaranStore.pendingUsage()
        if (pending.isEmpty()) return@withContext true

        val items = JSONArray()
        pending.forEach { (key, value) ->
            val parts = key.split(":")
            if (parts.size != 2) return@forEach
            val id = parts[1].toIntOrNull() ?: return@forEach
            items.put(JSONObject().apply {
                when (parts[0]) {
                    "d" -> { put("donation_id", id); put("bytes", value); put("ok", true) }
                    "f" -> { put("donation_id", id); put("bytes", 0); put("ok", false) }
                    else -> { put("config_id", id); put("bytes", value); put("ok", true) }
                }
            })
        }
        if (items.length() == 0) return@withContext true

        val body = JSONObject().apply {
            put("device_id", NaranStore.deviceId())
            put("items", items)
        }

        try {
            NaranApi.race("/api/v1/report", body, NaranStore.endpoints()).first
        } catch (e: Exception) {
            return@withContext false      // نگهش دار تا دور بعد
        }

        NaranStore.savePendingUsage(emptyMap())
        true
    }

    fun dismissNotice(id: Int) {
        NaranStore.markNoticeSeen(id)
        _notices.value = _notices.value.filterNot { it.id == id }
    }

    /**
     * الگوهای مسدود که باید به هسته داده شوند.
     *
     * موارد اجباری همیشه هستند؛ اختیاری‌ها اگر کاربر خاموششان نکرده باشد.
     */
    fun activeBlockPatterns(): List<String> {
        val off = NaranStore.disabledBlocks()
        return _blocks.value
            .filter { !it.optional || it.id !in off }
            .map { it.pattern }
    }

    fun toggleBlock(id: Int, enabled: Boolean) {
        val off = NaranStore.disabledBlocks().toMutableSet()
        if (enabled) off.remove(id) else off.add(id)
        NaranStore.saveDisabledBlocks(off)
        _blocks.value = _blocks.value.toList()   // برای بازکشیدن UI
    }

    fun adsFor(placement: String): List<NaranAd> =
        _ads.value.filter { it.placement == placement }

    fun channel(): Pair<String, String> = NaranStore.channel()

    /** اگر نسخه‌ی تازه‌تری هست برمی‌گرداند، وگرنه null. */
    fun updateAvailable(currentCode: Int): NaranRelease? =
        latestRelease?.takeIf { it.versionCode > currentCode }
}

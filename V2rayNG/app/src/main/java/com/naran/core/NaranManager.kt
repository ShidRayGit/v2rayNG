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

    private var appVersion: String = "1.0.0"
    private var latestRelease: NaranRelease? = null

    fun init(ctx: Context, versionName: String) {
        NaranStore.init(ctx)
        appVersion = versionName
        _ads.value = NaranStore.ads()
        refreshLocal()
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

    fun activeConfigs(): List<NaranConfig> = _licenses.value.map { it.config }

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
        ActivateResult.Ok(lic)
    }

    // ── همگام‌سازی ──

    suspend fun sync(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - NaranStore.lastSync < SYNC_INTERVAL_MS) return@withContext true

        val ids = JSONArray()
        NaranStore.licenses().forEach { ids.put(it.id) }
        val body = JSONObject().put("license_ids", ids)

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

        json.optJSONArray("ads")?.let { arr ->
            NaranStore.saveAds(arr)
            _ads.value = (0 until arr.length()).map { NaranAd.from(arr.getJSONObject(it)) }
        }

        json.optJSONObject("channel")?.let {
            NaranStore.saveChannel(it.optString("name"), it.optString("url"))
        }

        json.optJSONObject("release")?.let { latestRelease = NaranRelease.from(it) }

        refreshLocal()
        true
    }

    fun adsFor(placement: String): List<NaranAd> =
        _ads.value.filter { it.placement == placement }

    fun channel(): Pair<String, String> = NaranStore.channel()

    /** اگر نسخه‌ی تازه‌تری هست برمی‌گرداند، وگرنه null. */
    fun updateAvailable(currentCode: Int): NaranRelease? =
        latestRelease?.takeIf { it.versionCode > currentCode }
}

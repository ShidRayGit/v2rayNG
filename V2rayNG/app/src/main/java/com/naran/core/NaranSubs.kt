package com.naran.core

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * سابسکریپشن.
 *
 * لینک فقط از دامنه‌هایی که در پنل ثبت شده پذیرفته می‌شود. اپ خودش
 * دامنه را چک نمی‌کند — از سرور می‌پرسد، چون فهرست ممکن است عوض شود و
 * نمی‌خواهیم برای هر تغییر APK جدید بدهیم.
 */

data class SubConfig(
    val raw: String,
    val name: String,
    var pingMs: Long = -1,       // -1 یعنی هنوز تست نشده
    var okCount: Int = 0,        // تاریخچه‌ی اتصال موفق
    var failCount: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("raw", raw); put("name", name); put("pingMs", pingMs)
        put("okCount", okCount); put("failCount", failCount)
    }

    companion object {
        fun from(o: JSONObject) = SubConfig(
            o.optString("raw"), o.optString("name"),
            o.optLong("pingMs", -1), o.optInt("okCount"), o.optInt("failCount")
        )
    }
}

data class Subscription(
    val id: String,
    val url: String,
    val domain: String,
    var title: String = "",
    var configs: List<SubConfig> = emptyList(),
    var usedBytes: Long = 0,
    var totalBytes: Long = 0,     // ۰ یعنی نامحدود یا نامعلوم
    var expiresAt: Long = 0,      // ثانیه یونیکس، ۰ یعنی نامعلوم
    var lastUpdate: Long = 0,
    var sortByBest: Boolean = true
) {
    val remainingBytes: Long get() = if (totalBytes > 0) totalBytes - usedBytes else -1

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("url", url); put("domain", domain); put("title", title)
        put("usedBytes", usedBytes); put("totalBytes", totalBytes)
        put("expiresAt", expiresAt); put("lastUpdate", lastUpdate)
        put("sortByBest", sortByBest)
        put("configs", JSONArray().also { a -> configs.forEach { a.put(it.toJson()) } })
    }

    companion object {
        fun from(o: JSONObject): Subscription {
            val arr = o.optJSONArray("configs") ?: JSONArray()
            return Subscription(
                o.optString("id"), o.optString("url"), o.optString("domain"),
                o.optString("title"),
                (0 until arr.length()).map { SubConfig.from(arr.getJSONObject(it)) },
                o.optLong("usedBytes"), o.optLong("totalBytes"),
                o.optLong("expiresAt"), o.optLong("lastUpdate"),
                o.optBoolean("sortByBest", true)
            )
        }
    }
}

sealed class SubResult {
    data class Ok(val sub: Subscription) : SubResult()
    data class NotAllowed(val domain: String, val allowed: List<String>) : SubResult()
    data class Failed(val message: String) : SubResult()
}

object NaranSubs {

    private val _subs = MutableStateFlow<List<Subscription>>(emptyList())
    val subs: StateFlow<List<Subscription>> = _subs

    /** آخرین تنظیماتی که از سرور آمده. */
    @Volatile var updateMinutes: Int = 60
    @Volatile var sortDefault: Boolean = true
    @Volatile var pingTieMs: Long = 5

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    fun load() {
        _subs.value = NaranStore.subscriptions()
    }

    private fun persist(list: List<Subscription>) {
        _subs.value = list
        NaranStore.saveSubscriptions(list)
    }

    /** دامنه‌های سابی که کاربر دارد — برای اطلاعیه‌ی هدفمند. */
    fun domains(): List<String> = _subs.value.map { it.url }

    // ── افزودن ──

    suspend fun add(url: String): SubResult = withContext(Dispatchers.IO) {
        val clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            return@withContext SubResult.Failed(T.subBadUrl)
        }

        // اجازه را از سرور می‌پرسیم، نه از فهرست محلی
        val check = try {
            NaranApi.race("/api/v1/subcheck", JSONObject().put("url", clean),
                NaranStore.endpoints()).first
        } catch (e: Exception) {
            return@withContext SubResult.Failed(T.offline)
        }

        if (!check.optBoolean("ok")) {
            val arr = check.optJSONArray("allowed") ?: JSONArray()
            return@withContext SubResult.NotAllowed(
                check.optString("domain"),
                (0 until arr.length()).map { arr.getString(it) }
            )
        }

        val domain = check.optString("domain")
        val id = "sub_" + Math.abs(clean.hashCode()).toString()
        val existing = _subs.value.firstOrNull { it.url == clean }
        val sub = existing ?: Subscription(id, clean, domain, sortByBest = sortDefault)

        when (val r = fetch(sub)) {
            is SubResult.Ok -> {
                val list = _subs.value.filterNot { it.url == clean } + r.sub
                persist(list)
                NaranLog.i("ساب", "افزوده شد — " + r.sub.configs.size + " کانفیگ")
                SubResult.Ok(r.sub)
            }
            else -> r
        }
    }

    fun remove(id: String) {
        persist(_subs.value.filterNot { it.id == id })
    }

    fun setSort(id: String, enabled: Boolean) {
        persist(_subs.value.map { if (it.id == id) it.copy(sortByBest = enabled) else it })
    }

    // ── گرفتن و تحلیل ──

    private fun fetch(sub: Subscription): SubResult {
        val req = Request.Builder()
            .url(sub.url)
            .header("User-Agent", "v2rayNG/1.10")   // بعضی پنل‌ها بر اساس این جواب می‌دهند
            .build()

        val (bodyText, headers) = try {
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return SubResult.Failed(T.subFetchFailed)
                (res.body?.string().orEmpty()) to res.headers
            }
        } catch (e: Exception) {
            return SubResult.Failed(T.offline)
        }

        val configs = parse(bodyText)
        if (configs.isEmpty()) return SubResult.Failed(T.subEmpty)

        // پنل‌های سازگار با v2rayNG این هدر را می‌دهند
        val info = headers["subscription-userinfo"].orEmpty()
        var used = 0L; var total = 0L; var expire = 0L
        info.split(";").forEach { part ->
            val kv = part.trim().split("=")
            if (kv.size == 2) {
                val v = kv[1].trim().toLongOrNull() ?: 0L
                when (kv[0].trim()) {
                    "upload" -> used += v
                    "download" -> used += v
                    "total" -> total = v
                    "expire" -> expire = v
                }
            }
        }

        // تاریخچه‌ی پینگ کانفیگ‌های قبلی را نگه دار
        val old = sub.configs.associateBy { it.raw }
        val merged = configs.map { c -> old[c.raw]?.copy(name = c.name) ?: c }

        val out = sub.copy(
            title = headers["profile-title"]?.let { decodeTitle(it) }.orEmpty()
                .ifBlank { sub.title },
            configs = merged,
            usedBytes = used, totalBytes = total, expiresAt = expire,
            lastUpdate = System.currentTimeMillis() / 1000
        )
        return SubResult.Ok(if (out.sortByBest) out.copy(configs = rank(out.configs)) else out)
    }

    private fun decodeTitle(raw: String): String = runCatching {
        if (raw.startsWith("base64:")) {
            String(Base64.decode(raw.removePrefix("base64:"), Base64.DEFAULT))
        } else raw
    }.getOrDefault(raw)

    /**
     * محتوای ساب را می‌خواند.
     *
     * پنل‌ها سه جور جواب می‌دهند: متن خام، base64 استاندارد، یا base64
     * بدون padding. مورد آخر شایع است و همان بود که کار را می‌شکست —
     * Base64.decode اندروید بدون «=» انتهایی استثنا پرتاب می‌کند.
     *
     * الفبای URL-safe را هم جدا امتحان می‌کنیم، چون DEFAULT و URL_SAFE
     * را نمی‌شود با OR ترکیب کرد؛ نتیجه‌اش فقط URL_SAFE می‌شود و هرجا
     * «+» یا «/» در داده باشد خراب می‌کند.
     */
    private fun parse(body: String): List<SubConfig> {
        val text = body.trim()
        if (text.isEmpty()) {
            NaranLog.w("ساب", "پاسخ خالی بود")
            return emptyList()
        }

        val candidates = mutableListOf<String>()

        // ۱. متن خام
        if (text.contains("://")) candidates.add(text)

        // ۲. base64، با هر دو الفبا و با padding اصلاح‌شده
        val packed = text.filterNot { it == '\n' || it == '\r' || it == ' ' }
        val padded = packed + "=".repeat((4 - packed.length % 4) % 4)
        for (flags in listOf(
            Base64.NO_WRAP,
            Base64.NO_WRAP or Base64.URL_SAFE,
            Base64.DEFAULT
        )) {
            val out = runCatching { String(Base64.decode(padded, flags)) }.getOrNull()
            if (out != null && out.contains("://")) candidates.add(out)
        }

        // ۳. بعضی پنل‌ها هر خط را جدا base64 می‌کنند
        val perLine = text.lines().mapNotNull { line ->
            val t = line.trim()
            if (t.isEmpty()) return@mapNotNull null
            val pad = t + "=".repeat((4 - t.length % 4) % 4)
            runCatching { String(Base64.decode(pad, Base64.NO_WRAP)) }
                .getOrNull()?.takeIf { it.contains("://") }
        }
        if (perLine.isNotEmpty()) candidates.add(perLine.joinToString("\n"))

        if (candidates.isEmpty()) {
            // متن خام را کوتاه و سانسورشده ثبت کن تا عیب‌یابی ممکن باشد
            NaranLog.e("ساب", "خوانده نشد — " + text.length + " بایت، شروع: " +
                NaranLog.redact(text.take(80)))
            return emptyList()
        }

        // رمزگشایی اشتباه گاهی یک «://» تصادفی می‌سازد و اگر اولین را
        // برداریم برنده می‌شود. پس همه را می‌سنجیم و پرمحصول‌ترین را
        // برمی‌داریم.
        fun harvest(src: String): List<SubConfig> = src.lines()
            .map { it.trim() }
            .filter { it.contains("://") && it.length > 12 }
            .map { line ->
                val name = line.substringAfterLast("#", "").let {
                    runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
                }.ifBlank { line.substringAfter("://").take(14) }
                SubConfig(raw = line, name = name)
            }
            .distinctBy { it.raw }

        val out = candidates.map(::harvest).maxByOrNull { it.size } ?: emptyList()

        if (out.isEmpty()) {
            NaranLog.e("ساب", "رمزگشایی شد ولی کانفیگ معتبری نداشت")
            return emptyList()
        }

        NaranLog.i("ساب", out.size.toString() + " کانفیگ خوانده شد")
        return out
    }

    // ── مرتب‌سازی ──

    /**
     * بهترین کانفیگ اول.
     *
     * معیار اول پینگ است، ولی اختلاف چند میلی‌ثانیه معنادار نیست — با
     * pingTieMs مساوی حساب می‌شوند و آن‌وقت تاریخچه‌ی موفقیت تصمیم
     * می‌گیرد. کانفیگ تست‌نشده ته فهرست می‌رود، نه اول.
     */
    fun rank(list: List<SubConfig>): List<SubConfig> {
        val tie = pingTieMs.coerceAtLeast(1)
        return list.sortedWith(
            compareBy<SubConfig> { if (it.pingMs > 0) it.pingMs / tie else Long.MAX_VALUE }
                .thenByDescending { it.okCount - it.failCount }
                .thenBy { it.name.lowercase() }
        )
    }

    fun applySort(id: String) {
        persist(_subs.value.map {
            if (it.id == id && it.sortByBest) it.copy(configs = rank(it.configs)) else it
        })
    }

    /** نتیجه‌ی پینگ یک کانفیگ را ثبت می‌کند. */
    fun recordPing(raw: String, ms: Long) {
        persist(_subs.value.map { s ->
            val hit = s.configs.any { it.raw == raw }
            if (!hit) s else {
                val updated = s.configs.map {
                    if (it.raw != raw) it
                    else it.copy(
                        pingMs = ms,
                        okCount = it.okCount + if (ms > 0) 1 else 0,
                        failCount = it.failCount + if (ms > 0) 0 else 1
                    )
                }
                s.copy(configs = if (s.sortByBest) rank(updated) else updated)
            }
        })
    }

    // ── به‌روزرسانی ──

    suspend fun refresh(id: String): SubResult = withContext(Dispatchers.IO) {
        val sub = _subs.value.firstOrNull { it.id == id }
            ?: return@withContext SubResult.Failed(T.subEmpty)
        when (val r = fetch(sub)) {
            is SubResult.Ok -> {
                persist(_subs.value.map { if (it.id == id) r.sub else it })
                r
            }
            else -> r
        }
    }

    /**
     * به‌روزرسانی همه، اگر وقتش رسیده باشد.
     *
     * @param force بدون توجه به بازه
     */
    suspend fun refreshAll(force: Boolean = false): Int = withContext(Dispatchers.IO) {
        val nowSec = System.currentTimeMillis() / 1000
        val gap = updateMinutes.coerceAtLeast(5) * 60
        var count = 0
        _subs.value.forEach { s ->
            if (force || nowSec - s.lastUpdate >= gap) {
                if (refresh(s.id) is SubResult.Ok) count++
            }
        }
        if (count > 0) NaranLog.i("ساب", "به‌روزرسانی شد: " + count)
        count
    }

    /** همه‌ی کانفیگ‌های همه‌ی سابها، برای انتخاب سرور. */
    fun allConfigs(): List<Pair<Subscription, SubConfig>> =
        _subs.value.flatMap { s -> s.configs.map { s to it } }
}

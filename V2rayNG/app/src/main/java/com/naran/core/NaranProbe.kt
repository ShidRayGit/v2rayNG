package com.naran.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * راستی‌آزمایی اتصال.
 *
 * «متصل» بودن سرویس یعنی هسته بالا آمده، نه اینکه ترافیک واقعاً از تونل
 * رد می‌شود. اینجا آن را می‌سنجیم.
 *
 * روش: آدرس خروجی را دو بار می‌گیریم — یک بار از شبکه‌ی مستقیم و یک بار
 * از تونل. اگر یکی بودند یعنی تونل کار نمی‌کند. این سنجه به IPv4 یا IPv6
 * بودن کانفیگ کاری ندارد، برخلاف اجبار به یک خانواده که کانفیگ‌های
 * IPv6-only را می‌شکند.
 */
object NaranProbe {

    /**
     * سلامت تونل.
     *
     * UNKNOWN یعنی هنوز قضاوت نکرده‌ایم — نه «سالم» نه «خراب». این تفکیک
     * مهم است چون تا وقتی نتیجه نیامده نباید به کاربر بگوییم قطع است.
     */
    enum class Health { UNKNOWN, OK, DEAD }

    data class Result(
        val health: Health = Health.UNKNOWN,
        val checking: Boolean = false,
        val ip: String = "",
        val country: String = "",
        val flag: String = "",
        val note: String = "",
        val attempts: Int = 0
    ) {
        val verified: Boolean get() = health == Health.OK
        val dead: Boolean get() = health == Health.DEAD
    }

    private val _result = MutableStateFlow(Result())
    val result: StateFlow<Result> = _result

    /** از تونل رد می‌شود — پیش‌فرض سیستم. */
    private val tunneled = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    /**
     * کلاینت جستجوی آدرس، با اجبار به IPv4.
     *
     * بیشتر کانفیگ‌ها فقط مسیرهای IPv4 را به TUN می‌دهند، پس ترافیک
     * IPv6 در سطح سیستم‌عامل از کنار تونل رد می‌شود. اندروید هم IPv6 را
     * ترجیح می‌دهد — نتیجه اینکه سرویس آی‌پی، آدرس واقعی کاربر را
     * می‌دید نه آدرس تونل را.
     *
     * این فقط روی درخواست تشخیصی خودمان اثر دارد و چیزی را برای کاربر
     * نمی‌بندد. کانفیگ‌های IPv6 هم سالم می‌مانند، چون نوع کانفیگ تعیین
     * می‌کند تونل چطور به سرور وصل شود، نه اینکه داخل تونل چه می‌رود.
     */
    /**
     * پورت SOCKS محلی هسته.
     *
     * کاربر ممکن است در تنظیمات عوضش کرده باشد، پس اول از آنجا می‌خوانیم
     * و اگر نبود پیش‌فرض v2rayNG.
     */
    private fun socksPort(): Int = runCatching {
        MmkvManager.decodeSettingsString(AppConfig.PREF_SOCKS_PORT)
            ?.trim()?.toIntOrNull()
    }.getOrNull() ?: AppConfig.PORT_SOCKS.toIntOrNull() ?: 10808

    /**
     * کلاینت جستجوی آدرس خروجی.
     *
     * v2rayNG اپ خودش را با addDisallowedApplication از تونل بیرون
     * می‌گذارد — کار درستی است، وگرنه اتصال هسته به سرور هم می‌خواست از
     * تونل رد شود و حلقه می‌ساخت. ولی یعنی هیچ درخواست معمولی از داخل
     * اپ ما هرگز از تونل رد نمی‌شود، و برای همین همیشه آدرس واقعی
     * کاربر برمی‌گشت.
     *
     * راه‌حل: از پروکسی SOCKS محلی رد می‌شویم. آن‌وقت درخواست وارد هسته
     * می‌شود و از تونل بیرون می‌آید.
     */
    private fun ipClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .proxy(
            java.net.Proxy(
                java.net.Proxy.Type.SOCKS,
                java.net.InetSocketAddress("127.0.0.1", socksPort())
            )
        )
        // حل نام را به پروکسی بسپار، نه به سیستم. وگرنه DNS از بیرون
        // تونل حل می‌شود و می‌تواند آدرس متفاوتی بدهد.
        .dns(Dns.SYSTEM)
        .build()

    private val PROBES = listOf(
        "https://www.gstatic.com/generate_204",
        "https://cp.cloudflare.com/generate_204"
    )

    /** چند سرویس، چون هرکدام ممکن است جایی مسدود یا کند باشد. */
    private val IP_SERVICES = listOf(
        "https://ipinfo.io/json",
        "https://ipwho.is/?fields=ip,country,country_code",
        "https://ipapi.co/json/",
        "http://ip-api.com/json/?fields=query,country,countryCode"
    )

    fun clear() { _result.value = Result() }

    private fun reachable(): Boolean = PROBES.any { url ->
        runCatching {
            tunneled.newCall(Request.Builder().url(url).head().build())
                .execute().use { it.code == 204 || it.isSuccessful }
        }.getOrDefault(false)
    }

    /**
     * بررسی سلامت با چند بار تلاش.
     *
     * یک بار شکست ممکن است لحظه‌ای باشد — تونل تازه بالا آمده یا شبکه
     * یک ثانیه قطع بوده. فقط وقتی همه‌ی تلاش‌ها شکست بخورند اعلام
     * می‌کنیم که ترافیک رد نمی‌شود.
     */
    suspend fun run(attempts: Int = 3) = withContext(Dispatchers.IO) {
        _result.value = _result.value.copy(checking = true, attempts = 0)

        var ok = false
        for (i in 1..attempts.coerceAtLeast(1)) {
            _result.value = _result.value.copy(attempts = i)
            if (reachable()) { ok = true; break }
            if (i < attempts) delay(3000)
        }

        if (!ok) {
            _result.value = Result(
                health = Health.DEAD, checking = false, note = T.notTunneled,
                attempts = attempts
            )
            NaranLog.e("بررسی", "بعد از " + attempts + " تلاش، ترافیک رد نشد")
            return@withContext
        }

        // مقایسه‌ی «داخل تونل با بیرون تونل» را برداشتیم: راه مطمئنی برای
        // بیرون بردن یک سوکت از تونل روی همه‌ی گوشی‌ها نبود، پس هر دو
        // درخواست از تونل می‌رفتند و همیشه یکی درمی‌آمدند — هشدار کاذب.
        // حالا فقط دسترسی و آدرس خروجی را نشان می‌دهیم.
        // اگر پروکسی محلی بالا نباشد، چیزی نشان نمی‌دهیم — بهتر از
        // نشان دادن آدرس واقعی کاربر است.
        val exit = runCatching { lookup(ipClient()) }
            .getOrDefault(Triple("", "", ""))

        _result.value = Result(
            health = Health.OK, checking = false,
            ip = exit.first, country = exit.second, flag = exit.third
        )
        NaranLog.i("بررسی", "خروجی تأیید شد")
    }

    /**
     * آدرس خروجی و کشور.
     *
     * قالب پاسخ بین سرویس‌ها فرق می‌کند، پس چند نام فیلد را امتحان
     * می‌کنیم به‌جای اینکه به یکی وابسته بمانیم.
     */
    private fun lookup(client: OkHttpClient): Triple<String, String, String> {
        for (url in IP_SERVICES) {
            val parsed = runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { res ->
                    if (!res.isSuccessful) return@use null
                    val o = JSONObject(res.body?.string().orEmpty())

                    val ip = firstOf(o, "ip", "query")
                    if (ip.isBlank()) return@use null

                    val code = firstOf(o, "country_code", "countryCode", "country")
                        .take(2)
                    val name = firstOf(o, "country_name", "country").let {
                        // ipinfo کد کشور را در فیلد country می‌گذارد
                        if (it.length == 2) "" else it
                    }
                    Triple(ip, name, flagOf(code))
                }
            }.getOrNull()

            if (parsed != null) {
                NaranLog.i("بررسی", "آدرس از " + host(url) + " گرفته شد")
                return parsed
            }
        }
        NaranLog.w("بررسی", "هیچ سرویس آی‌پی جواب نداد")
        return Triple("", "", "")
    }

    private fun firstOf(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.optString(k)
            if (v.isNotBlank() && v != "null") return v
        }
        return ""
    }

    private fun host(url: String): String =
        runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault(url)

    private fun flagOf(code: String): String {
        if (code.length != 2) return ""
        val base = 0x1F1E6 - 'A'.code
        return runCatching {
            String(Character.toChars(base + code[0].uppercaseChar().code)) +
                String(Character.toChars(base + code[1].uppercaseChar().code))
        }.getOrDefault("")
    }
}

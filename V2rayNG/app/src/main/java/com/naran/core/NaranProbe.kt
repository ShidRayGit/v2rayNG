package com.naran.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.Inet4Address
import java.net.Inet6Address
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

    data class Result(
        val verified: Boolean = false,
        val checking: Boolean = false,
        val ip: String = "",
        val country: String = "",
        val flag: String = "",
        val ipv6Leak: Boolean = false,   // IPv6 از تونل رد نمی‌شود
        val note: String = ""
    )

    private val _result = MutableStateFlow(Result())
    val result: StateFlow<Result> = _result

    /** از تونل رد می‌شود — پیش‌فرض سیستم. */
    private val tunneled = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    /** بیرون از تونل، برای مقایسه. */
    private val direct = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .socketFactory(NaranDirect.socketFactory)
        .dns { host -> NaranDirect.resolve(host) }
        .build()

    private val PROBES = listOf(
        "https://www.gstatic.com/generate_204",
        "https://cp.cloudflare.com/generate_204"
    )

    // نسخه‌های خانواده‌محور، برای تشخیص نشت
    private const val V4_ONLY = "https://ipv4.icanhazip.com"
    private const val V6_ONLY = "https://ipv6.icanhazip.com"

    fun clear() { _result.value = Result() }

    suspend fun run() = withContext(Dispatchers.IO) {
        _result.value = _result.value.copy(checking = true)

        val reachable = PROBES.any { url ->
            runCatching {
                tunneled.newCall(Request.Builder().url(url).head().build())
                    .execute().use { it.code == 204 || it.isSuccessful }
            }.getOrDefault(false)
        }

        if (!reachable) {
            _result.value = Result(verified = false, checking = false,
                note = T.notTunneled)
            NaranLog.w("بررسی", "تونل جواب نداد")
            return@withContext
        }

        val throughTunnel = lookup(tunneled)
        val outsideTunnel = lookup(direct)

        // اگر هر دو یکی باشند، تونل عملاً بی‌اثر است
        if (throughTunnel.first.isNotBlank() &&
            throughTunnel.first == outsideTunnel.first
        ) {
            _result.value = Result(
                verified = false, checking = false,
                ip = throughTunnel.first,
                note = T.sameIp
            )
            NaranLog.w("بررسی", "آدرس داخل و بیرون تونل یکی است")
            return@withContext
        }

        val leak = detectIpv6Leak()
        if (leak) {
            NaranLog.w("بررسی", "نشت IPv6 — بسته شد")
            NaranDirect.blockIpv6 = true
        }

        _result.value = Result(
            verified = true, checking = false,
            ip = throughTunnel.first,
            country = throughTunnel.second,
            flag = throughTunnel.third,
            ipv6Leak = leak,
            note = if (leak) T.ipv6Blocked else ""
        )
        NaranLog.i("بررسی", "خروجی تأیید شد" + if (leak) " (IPv6 بسته شد)" else "")
    }

    /**
     * نشت IPv6: وقتی آدرس IPv6ای که از تونل می‌گیریم همان است که بدون
     * تونل داریم. یعنی ترافیک IPv6 دور تونل می‌زند.
     *
     * اگر خود کانفیگ IPv6 باشد این اتفاق نمی‌افتد، چون آن‌وقت آدرس‌ها
     * فرق می‌کنند — پس کانفیگ‌های IPv6-only بی‌دلیل مسدود نمی‌شوند.
     */
    private fun detectIpv6Leak(): Boolean {
        val viaTunnel = plainGet(tunneled, V6_ONLY) ?: return false
        val viaDirect = plainGet(direct, V6_ONLY) ?: return false
        return viaTunnel.isNotBlank() && viaTunnel == viaDirect
    }

    private fun plainGet(client: OkHttpClient, url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (res.isSuccessful) res.body?.string()?.trim() else null
        }
    }.getOrNull()

    /** آدرس خروجی و کشور. سه‌تایی: آی‌پی، کشور، پرچم. */
    private fun lookup(client: OkHttpClient): Triple<String, String, String> {
        for (url in listOf(
            "https://ipwho.is/?fields=ip,country,country_code",
            "http://ip-api.com/json/?fields=query,country,countryCode"
        )) {
            val parsed = runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { res ->
                    if (!res.isSuccessful) return@use null
                    val o = JSONObject(res.body?.string().orEmpty())
                    val ip = o.optString("ip").ifBlank { o.optString("query") }
                    val country = o.optString("country")
                    val code = o.optString("country_code").ifBlank { o.optString("countryCode") }
                    if (ip.isBlank()) null else Triple(ip, country, flagOf(code))
                }
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return Triple("", "", "")
    }

    private fun flagOf(code: String): String {
        if (code.length != 2) return ""
        val base = 0x1F1E6 - 'A'.code
        return runCatching {
            String(Character.toChars(base + code[0].uppercaseChar().code)) +
                String(Character.toChars(base + code[1].uppercaseChar().code))
        }.getOrDefault("")
    }
}

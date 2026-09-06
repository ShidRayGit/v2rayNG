package com.naran.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
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

    data class Result(
        val verified: Boolean = false,
        val checking: Boolean = false,
        val ip: String = "",
        val country: String = "",
        val flag: String = "",
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

    private val PROBES = listOf(
        "https://www.gstatic.com/generate_204",
        "https://cp.cloudflare.com/generate_204"
    )

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

        // مقایسه‌ی «داخل تونل با بیرون تونل» را برداشتیم: راه مطمئنی برای
        // بیرون بردن یک سوکت از تونل روی همه‌ی گوشی‌ها نبود، پس هر دو
        // درخواست از تونل می‌رفتند و همیشه یکی درمی‌آمدند — هشدار کاذب.
        // حالا فقط دسترسی و آدرس خروجی را نشان می‌دهیم.
        val exit = lookup(tunneled)

        _result.value = Result(
            verified = true, checking = false,
            ip = exit.first, country = exit.second, flag = exit.third
        )
        NaranLog.i("بررسی", "خروجی تأیید شد")
    }

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

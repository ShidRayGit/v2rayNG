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
 * راستی‌آزمایی اتصال و شناسایی آی‌پی خروجی.
 *
 * دو چیز جدا:
 *  - تونل واقعاً کار می‌کند؟  یک درخواست ۲۰۴ که سبک است و کم فیلتر می‌شود
 *  - از کجا بیرون می‌رویم؟   آی‌پی و کشور
 *
 * هر دو در سکوت شکست می‌خورند. اگر سرویس بیرونی جواب ندهد، بخش خالی
 * می‌ماند — پیام خطا نشان نمی‌دهیم چون کاربر فکر می‌کند اپ خراب است.
 */
object NaranProbe {

    data class Result(
        val verified: Boolean = false,   // تونل جواب داد
        val checking: Boolean = false,
        val ip: String = "",
        val country: String = "",
        val flag: String = ""
    )

    private val _result = MutableStateFlow(Result())
    val result: StateFlow<Result> = _result

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    // چند مقصد، چون هرکدام ممکن است جایی مسدود باشد
    private val PROBES = listOf(
        "https://www.gstatic.com/generate_204",
        "https://cp.cloudflare.com/generate_204",
        "https://connectivitycheck.gstatic.com/generate_204"
    )

    fun clear() { _result.value = Result() }

    suspend fun run() = withContext(Dispatchers.IO) {
        _result.value = _result.value.copy(checking = true)

        var ok = false
        for (url in PROBES) {
            ok = runCatching {
                client.newCall(Request.Builder().url(url).head().build())
                    .execute().use { it.code == 204 || it.isSuccessful }
            }.getOrDefault(false)
            if (ok) break
        }

        if (!ok) {
            _result.value = Result(verified = false, checking = false)
            return@withContext
        }

        val (ip, country, flag) = lookupIp()
        _result.value = Result(
            verified = true, checking = false,
            ip = ip, country = country, flag = flag
        )
    }

    private fun lookupIp(): Triple<String, String, String> {
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
                    val code = o.optString("country_code")
                        .ifBlank { o.optString("countryCode") }
                    if (ip.isBlank()) null else Triple(ip, country, flagOf(code))
                }
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return Triple("", "", "")
    }

    /** کد دو حرفی کشور را به ایموجی پرچم تبدیل می‌کند. */
    private fun flagOf(code: String): String {
        if (code.length != 2) return ""
        val base = 0x1F1E6 - 'A'.code
        return runCatching {
            String(Character.toChars(base + code[0].uppercaseChar().code)) +
                String(Character.toChars(base + code[1].uppercaseChar().code))
        }.getOrDefault("")
    }
}

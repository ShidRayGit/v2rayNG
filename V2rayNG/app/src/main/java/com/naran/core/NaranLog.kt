package com.naran.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * لاگ داخلی ناران.
 *
 * دو منبع دارد:
 *  - رویدادهای خودمان، که امن‌اند چون خودمان می‌نویسیمشان
 *  - لاگ هسته، که پیش‌فرض خاموش است و اگر روشن شود سانسور می‌شود
 *
 * درباره‌ی سانسور صادق باشیم: الگو هیچ‌وقت کامل نیست. هسته پیام‌هایی
 * می‌سازد که از قبل نمی‌شناسیم و ممکن است آدرس سرور را وسط جمله‌ای
 * غیرمنتظره بیاورد. برای همین لاگ هسته پیش‌فرض خاموش است و کاربر عادی
 * هیچ‌وقت با آن روبه‌رو نمی‌شود.
 */
object NaranLog {

    enum class Level { INFO, WARN, ERROR }

    data class Entry(
        val time: Long,
        val level: Level,
        val tag: String,
        val message: String
    ) {
        fun format(): String {
            val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(time))
            return "$t  $tag  $message"
        }
    }

    private const val MAX = 300

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    // ── سانسور ──

    private val PATTERNS = listOf(
        // لینک‌های کانفیگ، کامل
        Regex("""\b(vless|vmess|trojan|ss|ssr|hysteria2?|hy2|tuic|wireguard|socks|http)://\S+""",
            RegexOption.IGNORE_CASE) to "[کانفیگ]",
        // UUID
        Regex("""\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b""",
            RegexOption.IGNORE_CASE) to "[شناسه]",
        // IPv4 با پورت اختیاری
        Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(:\d{1,5})?\b""") to "[آی‌پی]",

        // دامنه با پورت
        Regex("""\b[a-z0-9][a-z0-9.-]*\.[a-z]{2,}(:\d{1,5})?\b""",
            RegexOption.IGNORE_CASE) to "[دامنه]",
        // رشته‌های base64 بلند — معمولاً کانفیگ یا کلید
        Regex("""\b[A-Za-z0-9+/=_-]{28,}\b""") to "[رشته]",
    )

    /** دامنه‌هایی که پنهان کردنشان فایده ندارد و خواندن لاگ را سخت می‌کند. */
    private val SAFE = setOf(
        "gstatic.com", "google.com", "cloudflare.com", "github.com", "localhost"
    )

    // IPv6 با regex شکننده است — شکل فشرده «::» از دستش در می‌رود.
    // به‌جایش توکن‌های حاوی دونقطه را جدا می‌کنیم و خودمان قضاوت می‌کنیم.
    private val COLON_TOKEN = Regex("[0-9a-fA-F:]{3,}")

    private fun looksIpv6(tok: String): Boolean {
        if (tok.count { it == ':' } < 2) return false
        if (Regex("::").findAll(tok).count() > 1) return false
        val parts = tok.split(":")
        if (parts.size > 9) return false
        val filled = parts.filter { it.isNotEmpty() }
        if (filled.isEmpty()) return false
        if (filled.any { it.length > 4 || !it.all { c -> c.isDigit() ||
                c in 'a'..'f' || c in 'A'..'F' } }) return false
        // ساعت مثل 12:30:45 را با آی‌پی اشتباه نگیریم
        if (!tok.contains("::") &&
            filled.all { it.length <= 2 && it.all(Char::isDigit) }) return false
        return true
    }

    fun redact(raw: String): String {
        var out = COLON_TOKEN.replace(raw) {
            if (looksIpv6(it.value)) "[آی‌پی]" else it.value
        }
        for ((re, replacement) in PATTERNS) {
            out = re.replace(out) { m ->
                if (SAFE.any { m.value.contains(it, ignoreCase = true) }) m.value
                else replacement
            }
        }
        return out
    }

    // ── نوشتن ──

    private fun add(level: Level, tag: String, message: String) {
        val entry = Entry(System.currentTimeMillis(), level, tag, redact(message))
        _entries.value = (_entries.value + entry).takeLast(MAX)
    }

    fun i(tag: String, msg: String) = add(Level.INFO, tag, msg)
    fun w(tag: String, msg: String) = add(Level.WARN, tag, msg)
    fun e(tag: String, msg: String) = add(Level.ERROR, tag, msg)

    fun clear() { _entries.value = emptyList() }

    /** کل لاگ به‌صورت متن، برای کپی کردن. از قبل سانسور شده. */
    fun dump(): String = _entries.value.joinToString("\n") { it.format() }

    // ── لاگ هسته ──

    /**
     * خواندن logcat خود پروسه.
     *
     * فقط وقتی معنی دارد که کاربر در تنظیمات روشنش کرده باشد، چون در آن
     * حالت loglevel هسته از none بالاتر رفته و ممکن است چیزهایی چاپ کند
     * که سانسور کاملشان نکند.
     */
    fun captureCoreLog(): List<String> = runCatching {
        val proc = Runtime.getRuntime().exec(
            arrayOf("logcat", "-d", "-t", "200", "-v", "brief")
        )
        proc.inputStream.bufferedReader().useLines { lines ->
            lines.filter { it.contains("v2ray", true) || it.contains("GoLog", true) }
                .map { redact(it) }
                .toList()
        }
    }.getOrDefault(emptyList())
}

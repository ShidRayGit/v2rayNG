package com.naran.core

import com.v2ray.ang.AppConfig

/**
 * ثابت‌هایی از v2rayNG که نامشان بین نسخه‌ها فرق می‌کند.
 *
 * ارجاع مستقیم به آن‌ها بیلد را می‌شکند اگر اسم عوض شده باشد. اینجا در
 * زمان اجرا چند نام محتمل را امتحان می‌کنیم: اگر پیدا شد استفاده می‌شود،
 * وگرنه فقط همان قابلیت خاموش می‌ماند و اپ سالم کار می‌کند.
 */
object NaranConst {

    private fun intOf(vararg names: String): Int? {
        val cls = AppConfig::class.java
        for (n in names) {
            val v = runCatching {
                val f = cls.getDeclaredField(n)
                f.isAccessible = true
                f.getInt(AppConfig)
            }.getOrNull()
            if (v != null) return v
        }
        return null
    }

    private fun strOf(vararg names: String): String? {
        val cls = AppConfig::class.java
        for (n in names) {
            val v = runCatching {
                val f = cls.getDeclaredField(n)
                f.isAccessible = true
                f.get(AppConfig) as? String
            }.getOrNull()
            if (v != null) return v
        }
        return null
    }

    /** پیام «همه‌ی کانفیگ‌ها را تست کن». */
    val MSG_MEASURE_ALL: Int? by lazy {
        intOf(
            "MSG_MEASURE_CONFIG",
            "MSG_MEASURE_CONFIG_START",
            "MSG_MEASURE_ALL_CONFIG",
            "MSG_MEASURE_CONFIG_ALL"
        )
    }

    /** کلید تنظیماتِ دامنه‌های مسدود در مسیریابی. */
    val PREF_ROUTING_BLOCKED: String? by lazy {
        strOf(
            "PREF_V2RAY_ROUTING_BLOCKED",
            "PREF_ROUTING_BLOCKED",
            "PREF_V2RAY_ROUTING_BLOCKED_DOMAIN",
            "PREF_ROUTING_DOMAIN_STRATEGY_BLOCKED"
        )
    }
}

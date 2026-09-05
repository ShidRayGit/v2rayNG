package com.naran.core

import org.json.JSONObject

data class NaranConfig(
    val id: Int,
    val name: String,
    val location: String,
    val flag: String,
    val protocol: String,
    val raw: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("location", location)
        put("flag", flag); put("protocol", protocol); put("raw", raw)
    }

    companion object {
        fun from(o: JSONObject) = NaranConfig(
            o.optInt("id"),
            o.optString("name"),
            o.optString("location"),
            o.optString("flag"),
            o.optString("protocol"),
            o.optString("raw")
        )
    }
}

/**
 * یک لایسنس فعال‌شده روی این دستگاه.
 *
 * سه مهر زمانی نگه می‌داریم تا دستکاری ساعت گوشی لو برود:
 *  expiresAtWall  — زمان دیواری انقضا، از سرور
 *  activatedWall  — زمان دیواری لحظه‌ی فعال‌سازی
 *  activatedBoot  — elapsedRealtime همان لحظه
 */
data class NaranLicense(
    val id: Int,
    val code: String,
    val config: NaranConfig,
    val expiresAtWall: Long,
    val activatedWall: Long,
    val activatedBoot: Long,
    val durationMs: Long,
    var revoked: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("code", code); put("config", config.toJson())
        put("expiresAtWall", expiresAtWall); put("activatedWall", activatedWall)
        put("activatedBoot", activatedBoot); put("durationMs", durationMs)
        put("revoked", revoked)
    }

    companion object {
        fun from(o: JSONObject) = NaranLicense(
            o.optInt("id"),
            o.optString("code"),
            NaranConfig.from(o.getJSONObject("config")),
            o.optLong("expiresAtWall"),
            o.optLong("activatedWall"),
            o.optLong("activatedBoot"),
            o.optLong("durationMs"),
            o.optBoolean("revoked")
        )
    }
}

data class NaranAd(
    val id: Int, val title: String, val body: String,
    val imageUrl: String, val link: String, val placement: String
) {
    /** پنل آدرس نسبی می‌دهد؛ اینجا کامل می‌شود. */
    fun fullImageUrl(base: String): String = when {
        imageUrl.isBlank() -> ""
        imageUrl.startsWith("http") -> imageUrl
        base.isBlank() -> ""
        else -> base.trimEnd('/') + imageUrl
    }

    companion object {
        fun from(o: JSONObject) = NaranAd(
            o.optInt("id"), o.optString("title"), o.optString("body"),
            o.optString("image_url"), o.optString("link"),
            o.optString("placement", "home")
        )
    }
}

data class NaranEndpoint(
    val url: String, val label: String,
    val servesConfigs: Boolean, val priority: Int
) {
    companion object {
        fun from(o: JSONObject) = NaranEndpoint(
            o.optString("url").trimEnd('/'),
            o.optString("label"),
            o.optInt("serves_configs", 1) == 1,
            o.optInt("priority", 100)
        )
    }
}

data class NaranRelease(
    val versionName: String, val versionCode: Int,
    val apkUrl: String, val changelog: String, val mandatory: Boolean
) {
    companion object {
        fun from(o: JSONObject) = NaranRelease(
            o.optString("version_name", "1.0.0"),
            o.optInt("version_code", 1),
            o.optString("apk_url"),
            o.optString("changelog"),
            o.optInt("mandatory") == 1
        )
    }
}

/**
 * کانفیگ عمومی — بدون کد لایسنس، برای همه‌ی نصب‌ها.
 *
 * در پنل روشن و خاموش می‌شود و می‌تواند مهلت زمانی داشته باشد.
 * expiresAt صفر یعنی تا وقتی که در پنل خاموش نشده.
 */
data class NaranPublicConfig(
    val config: NaranConfig,
    val expiresAt: Long,             // ثانیه‌ی یونیکس، ۰ = بی‌انقضا
    val donationId: Int = 0,         // اگر اهدایی باشد
    val donor: String = "",
    val trial: Boolean = false
) {
    fun isAlive(nowSec: Long): Boolean = expiresAt == 0L || expiresAt > nowSec

    fun toJson(): JSONObject = JSONObject().apply {
        put("config", config.toJson())
        put("expiresAt", expiresAt)
        put("donationId", donationId)
        put("donor", donor)
        put("trial", trial)
    }

    companion object {
        /** از پاسخ سرور: فیلدهای کانفیگ در همان سطح می‌آیند. */
        fun from(o: JSONObject) = NaranPublicConfig(
            NaranConfig.from(o),
            o.optLong("expires_at", 0L),
            o.optInt("donation_id", 0),
            o.optString("donor"),
            o.optBoolean("trial")
        )

        fun fromCache(o: JSONObject) = NaranPublicConfig(
            NaranConfig.from(o.getJSONObject("config")),
            o.optLong("expiresAt", 0L),
            o.optInt("donationId", 0),
            o.optString("donor"),
            o.optBoolean("trial")
        )
    }
}

/** نتیجه‌ی اهدای کانفیگ. */
/** اطلاعیه از پنل. */
data class NaranNotice(
    val id: Int, val title: String, val body: String, val level: String
) {
    val isWarning: Boolean get() = level == "warn"

    companion object {
        fun from(o: JSONObject) = NaranNotice(
            o.optInt("id"), o.optString("title"),
            o.optString("body"), o.optString("level", "info")
        )
    }
}

/** یک الگوی مسدودسازی. */
data class NaranBlock(
    val id: Int, val pattern: String, val label: String, val optional: Boolean
) {
    companion object {
        fun from(o: JSONObject) = NaranBlock(
            o.optInt("id"), o.optString("pattern"),
            o.optString("label"), o.optBoolean("optional", true)
        )
    }
}

sealed class DonateResult {
    data class Ok(val message: String) : DonateResult()
    data class Rejected(val code: String, val message: String) : DonateResult()
    data class Offline(val message: String) : DonateResult()
}

/** نتیجه‌ی جستجوی سرور عمومی. */
sealed class DiscoverResult {
    data class Ok(val config: NaranPublicConfig) : DiscoverResult()
    data class None(val message: String) : DiscoverResult()
    data class Offline(val message: String) : DiscoverResult()
}

sealed class ActivateResult {
    data class Ok(val license: NaranLicense) : ActivateResult()
    data class Rejected(val code: String, val message: String) : ActivateResult()
    data class Offline(val message: String) : ActivateResult()
}

package com.naran.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * ذخیره‌ی محلی.
 *
 * کانفیگ‌ها با کلیدی که در Android Keystore است رمز می‌شوند، پس با adb یا
 * فایل‌منیجر روت‌شده هم خواندنشان ساده نیست. هیچ‌جای دیگری روی دیسک نوشته
 * نمی‌شوند.
 */
object NaranStore {

    private const val FILE = "naran_secure"
    private const val K_DEVICE = "device_id"
    private const val K_LICENSES = "licenses"
    private const val K_ENDPOINTS = "endpoints"
    private const val K_ADS = "ads"
    private const val K_CHANNEL_URL = "channel_url"
    private const val K_CHANNEL_NAME = "channel_name"
    private const val K_LAST_SYNC = "last_sync"
    private const val K_SERVER_SKEW = "server_skew"
    private const val K_MEDIA_BASE = "media_base"
    private const val K_PUBLIC = "public_configs"
    private const val K_AUTOCONNECT = "auto_connect"
    private const val K_LAST_SERVER = "last_server"
    private const val K_CORE_LOG = "core_log"
    private const val K_LANG = "language"
    private const val K_PENDING = "pending_usage"
    private const val K_DONOR = "donor_name"
    private const val K_TG = "donor_telegram"
    private const val K_SUBS = "subscriptions"
    private const val K_BLOCKS_OFF = "blocks_disabled"
    private const val K_NOTICE_SEEN = "notices_seen"
    private const val K_BLOCK_SHOT = "block_screenshot"
    private const val K_NOTIFY_SPEED = "notify_speed"
    private const val K_NOTIFY_ASKED = "notify_asked"
    private const val K_MANUAL = "manual_configs"

    @Volatile private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            prefs = try {
                val key = MasterKey.Builder(ctx.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    ctx.applicationContext, FILE, key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                // روی چند دستگاه قدیمی Keystore خراب است؛ اپ نباید بمیرد
                ctx.applicationContext.getSharedPreferences(FILE + "_p", Context.MODE_PRIVATE)
            }
        }
    }

    private fun p(): SharedPreferences =
        prefs ?: throw IllegalStateException("NaranStore.init فراخوانی نشده")

    // ── شناسه دستگاه ──

    fun deviceId(): String {
        p().getString(K_DEVICE, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        p().edit().putString(K_DEVICE, id).apply()
        return id
    }

    // ── لایسنس‌ها ──

    fun licenses(): List<NaranLicense> {
        val raw = p().getString(K_LICENSES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull {
                runCatching { NaranLicense.from(arr.getJSONObject(it)) }.getOrNull()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveLicenses(list: List<NaranLicense>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        p().edit().putString(K_LICENSES, arr.toString()).apply()
    }

    fun upsertLicense(lic: NaranLicense) {
        val list = licenses().filterNot { it.id == lic.id }.toMutableList()
        list.add(0, lic)
        saveLicenses(list)
    }

    fun removeLicense(id: Int) = saveLicenses(licenses().filterNot { it.id == id })

    // ── اندپوینت‌ها ──

    fun endpoints(): List<NaranEndpoint> {
        val raw = p().getString(K_ENDPOINTS, null) ?: return NaranSeed.ENDPOINTS
        return try {
            val arr = JSONArray(raw)
            val list = (0 until arr.length()).map { NaranEndpoint.from(arr.getJSONObject(it)) }
            // بذر اولیه همیشه می‌ماند تا اگر همه‌ی آدرس‌های تازه مردند راه برگشت باشد
            (list + NaranSeed.ENDPOINTS).distinctBy { it.url }
        } catch (e: Exception) {
            NaranSeed.ENDPOINTS
        }
    }

    fun saveEndpoints(list: List<NaranEndpoint>) {
        if (list.isEmpty()) return
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("url", it.url); put("label", it.label)
                put("serves_configs", if (it.servesConfigs) 1 else 0)
                put("priority", it.priority)
            })
        }
        p().edit().putString(K_ENDPOINTS, arr.toString()).apply()
    }

    // ── کانفیگ‌های عمومی ──

    fun publicConfigs(): List<NaranPublicConfig> {
        val raw = p().getString(K_PUBLIC, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull {
                runCatching { NaranPublicConfig.fromCache(arr.getJSONObject(it)) }.getOrNull()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePublicConfigs(list: List<NaranPublicConfig>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        p().edit().putString(K_PUBLIC, arr.toString()).apply()
    }

    // ── تبلیغات و کانال ──

    fun ads(): List<NaranAd> {
        val raw = p().getString(K_ADS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { NaranAd.from(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAds(json: JSONArray) = p().edit().putString(K_ADS, json.toString()).apply()

    fun channel(): Pair<String, String> = Pair(
        p().getString(K_CHANNEL_NAME, NaranSeed.CHANNEL_NAME) ?: NaranSeed.CHANNEL_NAME,
        p().getString(K_CHANNEL_URL, NaranSeed.CHANNEL_URL) ?: NaranSeed.CHANNEL_URL
    )

    fun saveChannel(name: String, url: String) {
        if (url.isBlank()) return
        p().edit().putString(K_CHANNEL_NAME, name).putString(K_CHANNEL_URL, url).apply()
    }

    // ── زمان ──

    var lastSync: Long
        get() = p().getLong(K_LAST_SYNC, 0L)
        set(v) = p().edit().putLong(K_LAST_SYNC, v).apply()

    /** اختلاف ساعت گوشی با سرور، بر حسب میلی‌ثانیه. */
    var serverSkew: Long
        get() = p().getLong(K_SERVER_SKEW, 0L)
        set(v) = p().edit().putLong(K_SERVER_SKEW, v).apply()

    /** سروری که آخرین بار جواب داد؛ تصاویر بنر از همان گرفته می‌شوند. */
    var mediaBase: String
        get() = p().getString(K_MEDIA_BASE, "") ?: ""
        set(v) = p().edit().putString(K_MEDIA_BASE, v.trimEnd('/')).apply()

    /** اتصال خودکار هنگام باز شدن اپ. */
    var autoConnect: Boolean
        get() = p().getBoolean(K_AUTOCONNECT, false)
        set(v) = p().edit().putBoolean(K_AUTOCONNECT, v).apply()

    /** آخرین سروری که کاربر انتخاب کرده بود. */
    var lastServer: Int
        get() = p().getInt(K_LAST_SERVER, -1)
        set(v) = p().edit().putInt(K_LAST_SERVER, v).apply()

    /**
     * لاگ هسته. پیش‌فرض خاموش.
     *
     * روشن بودنش یعنی loglevel هسته از none بالاتر می‌رود و ممکن است
     * چیزهایی چاپ کند که سانسور کاملشان نکند. فقط برای عیب‌یابی.
     */
    var coreLog: Boolean
        get() = p().getBoolean(K_CORE_LOG, false)
        set(v) = p().edit().putBoolean(K_CORE_LOG, v).apply()

    /** "en" یا "fa" یا خالی (یعنی از زبان گوشی حدس بزن). */
    var language: String
        get() = p().getString(K_LANG, "") ?: ""
        set(v) = p().edit().putString(K_LANG, v).apply()

    /** اسم و تلگرام اهداکننده، تا هر بار دوباره نپرسیم. */
    var donorName: String
        get() = p().getString(K_DONOR, "") ?: ""
        set(v) = p().edit().putString(K_DONOR, v).apply()

    var donorTelegram: String
        get() = p().getString(K_TG, "") ?: ""
        set(v) = p().edit().putString(K_TG, v).apply()

    /**
     * مصرفی که هنوز به سرور گزارش نشده.
     *
     * اگر همان لحظه بفرستیم و شبکه قطع باشد، عدد از دست می‌رود. اینجا
     * جمع می‌شود تا دور بعد.
     */
    fun pendingUsage(): MutableMap<String, Long> {
        val raw = p().getString(K_PENDING, null) ?: return mutableMapOf()
        return try {
            val o = JSONObject(raw)
            val m = mutableMapOf<String, Long>()
            o.keys().forEach { k -> m[k] = o.optLong(k) }
            m
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    fun savePendingUsage(m: Map<String, Long>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        p().edit().putString(K_PENDING, o.toString()).apply()
    }

    // ── سابسکریپشن ──

    fun subscriptions(): List<Subscription> {
        val raw = p().getString(K_SUBS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull {
                runCatching { Subscription.from(arr.getJSONObject(it)) }.getOrNull()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveSubscriptions(list: List<Subscription>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        p().edit().putString(K_SUBS, arr.toString()).apply()
    }

    // ── بلاک‌لیستی که کاربر خاموش کرده ──

    fun disabledBlocks(): Set<Int> {
        val raw = p().getString(K_BLOCKS_OFF, null) ?: return emptySet()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getInt(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun saveDisabledBlocks(ids: Set<Int>) {
        val arr = JSONArray()
        ids.forEach { arr.put(it) }
        p().edit().putString(K_BLOCKS_OFF, arr.toString()).apply()
    }

    // ── اطلاعیه‌هایی که کاربر دیده ──

    fun seenNotices(): Set<Int> {
        val raw = p().getString(K_NOTICE_SEEN, null) ?: return emptySet()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getInt(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun markNoticeSeen(id: Int) {
        // Set ترتیب ندارد، پس اول به List تبدیل می‌شود تا بشود آخری‌ها را
        // نگه داشت و فهرست بی‌نهایت بزرگ نشود.
        val all = (seenNotices().toList() + id).distinct().takeLast(80)
        val arr = JSONArray()
        all.forEach { arr.put(it) }
        p().edit().putString(K_NOTICE_SEEN, arr.toString()).apply()
    }

    /** ممنوعیت اسکرین‌شات — از پنل می‌آید، پیش‌فرض آزاد. */
    var blockScreenshot: Boolean
        get() = p().getBoolean(K_BLOCK_SHOT, false)
        set(v) = p().edit().putBoolean(K_BLOCK_SHOT, v).apply()

    /** نمایش سرعت در نوتیفیکیشن. پیش‌فرض روشن. */
    var notifySpeed: Boolean
        get() = p().getBoolean(K_NOTIFY_SPEED, true)
        set(v) = p().edit().putBoolean(K_NOTIFY_SPEED, v).apply()

    /** یک بار مجوز اعلان را پرسیده‌ایم؛ دوباره مزاحم نشویم. */
    var notifyAsked: Boolean
        get() = p().getBoolean(K_NOTIFY_ASKED, false)
        set(v) = p().edit().putBoolean(K_NOTIFY_ASKED, v).apply()

    /**
     * کانفیگ‌هایی که کاربر خودش از کلیپ‌بورد چسبانده.
     *
     * جدا از لایسنس نگه داشته می‌شوند چون انقضا ندارند و از سرور
     * نمی‌آیند؛ فقط خود کاربر می‌تواند حذفشان کند.
     */
    fun manualConfigs(): List<NaranConfig> {
        val raw = p().getString(K_MANUAL, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull {
                runCatching { NaranConfig.from(arr.getJSONObject(it)) }.getOrNull()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveManualConfigs(list: List<NaranConfig>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        p().edit().putString(K_MANUAL, arr.toString()).apply()
    }

    fun wipe() = p().edit().clear().apply()
}

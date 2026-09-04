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

    fun wipe() = p().edit().clear().apply()
}

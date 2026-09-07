package com.naran.core

import kotlinx.coroutines.*
import okhttp3.*
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * لایه‌ی شبکه.
 *
 * همه‌ی اندپوینت‌ها هم‌زمان زده می‌شوند و اولین پاسخ درست برنده است.
 * یعنی فیلتر شدن یک دامنه کاربر را حتی کند هم نمی‌کند.
 */
object NaranApi {

    private const val TAG = "NaranApi"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            // مسیر عادی سیستم. قبلاً اینجا سوکت را عمداً به شبکه‌ی زیرین
            // می‌بستیم تا از تونل بیرون برود — آن تصمیم برای وقتی بود که
            // پنل روی خاک ایران بود. حالا که پنل خارج است، آن کار باعث
            // می‌شد وقتی هر VPNای روشن باشد درخواست از کنارش رد شود و
            // به فیلترینگ بخورد.
            // در نسخه‌ی نهایی pinning را روشن کنید — راهنما در PATCHES.md
            // .certificatePinner(NaranPinning.pinner())
            .build()
    }

    /**
     * پورت SOCKS محلی هسته. کاربر ممکن است عوضش کرده باشد.
     */
    private fun socksPort(): Int = runCatching {
        MmkvManager.decodeSettingsString(AppConfig.PREF_SOCKS_PORT)?.trim()?.toIntOrNull()
    }.getOrNull() ?: AppConfig.PORT_SOCKS.toIntOrNull() ?: 10808

    /**
     * کلاینت پشتیبان: از پروکسی محلی، یعنی از داخل تونل.
     *
     * v2rayNG اپ خودش را با addDisallowedApplication از تونل بیرون
     * می‌گذارد. وقتی تونل روشن است و مسیر مستقیم به پنل نمی‌رسد — که در
     * ایران زیاد پیش می‌آید — این تنها راه رسیدن است.
     *
     * هر بار ساخته می‌شود چون پورت ممکن است بین اجراها فرق کند.
     */
    private fun tunnelClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .proxy(
            java.net.Proxy(
                java.net.Proxy.Type.SOCKS,
                java.net.InetSocketAddress("127.0.0.1", socksPort())
            )
        )
        .build()

    class ApiError(val kind: Kind, message: String) : Exception(message) {
        enum class Kind { NETWORK, SIGNATURE, RATE_LIMIT, SERVER }
    }

    /** POST امضاشده به یک اندپوینت مشخص. */
    private suspend fun postTo(
        base: String,
        path: String,
        body: JSONObject,
        http: OkHttpClient
    ): JSONObject =
        withContext(Dispatchers.IO) {
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            val ts = (System.currentTimeMillis() / 1000).toString()
            val sign = NaranCrypto.sign("POST", path, ts, bytes)

            val req = Request.Builder()
                .url(base.trimEnd('/') + path)
                .addHeader("X-Naran-TS", ts)
                .addHeader("X-Naran-Sign", sign)
                .addHeader("User-Agent", "Naran")
                .post(bytes.toRequestBody(JSON))
                .build()

            http.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                when (res.code) {
                    200 -> JSONObject(text)
                    401 -> throw ApiError(ApiError.Kind.SIGNATURE, "امضا پذیرفته نشد")
                    429 -> throw ApiError(ApiError.Kind.RATE_LIMIT, "درخواست بیش از حد")
                    else -> throw ApiError(ApiError.Kind.SERVER, "خطای سرور ${res.code}")
                }
            }
        }

    /**
     * درخواست را به همه‌ی اندپوینت‌ها می‌فرستد و اولین موفق را برمی‌گرداند.
     *
     * @param configOnly فقط اندپوینت‌هایی که اجازه‌ی دادن کانفیگ دارند
     */
    suspend fun race(
        path: String,
        body: JSONObject,
        endpoints: List<NaranEndpoint>,
        configOnly: Boolean = false
    ): Pair<JSONObject, String> {
        val pool = endpoints
            .filter { !configOnly || it.servesConfigs }
            .sortedBy { it.priority }

        if (pool.isEmpty()) throw ApiError(ApiError.Kind.NETWORK, "آدرسی برای اتصال نیست")

        // اول مسیر عادی
        val direct = runCatching { raceWith(pool, path, body, client) }
        direct.getOrNull()?.let { return it }

        // اگر نشد و تونل خودمان بالاست، از پروکسی محلی. لازم است چون
        // v2rayNG اپ ما را از تونل بیرون می‌گذارد و مسیر عادی ممکن است
        // به پنل نرسد.
        if (NaranServiceState.state.value == NaranServiceState.State.ON) {
            val viaTunnel = runCatching { raceWith(pool, path, body, tunnelClient()) }
            viaTunnel.getOrNull()?.let {
                NaranLog.i("شبکه", "از پروکسی محلی رد شد")
                return it
            }
        }

        throw direct.exceptionOrNull() as? Exception
            ?: ApiError(ApiError.Kind.NETWORK, "هیچ سروری جواب نداد")
    }

    /** یک دور روی همه‌ی اندپوینت‌ها با کلاینت داده‌شده. */
    private suspend fun raceWith(
        pool: List<NaranEndpoint>,
        path: String,
        body: JSONObject,
        http: OkHttpClient
    ): Pair<JSONObject, String> = coroutineScope {
        val result = CompletableDeferred<Pair<JSONObject, String>>()
        val failures = java.util.concurrent.atomic.AtomicInteger(0)
        val lastError = java.util.concurrent.atomic.AtomicReference<Exception>(null)

        val jobs = pool.mapIndexed { index, ep ->
            launch {
                // چند ده میلی‌ثانیه فاصله تا اگر اولی سالم است بقیه بی‌خود زده نشوند
                delay(index * 120L)
                if (result.isCompleted) return@launch
                try {
                    val json = postTo(ep.url, path, body, http)
                    result.complete(json to ep.url)
                } catch (e: Exception) {
                    lastError.set(e)
                    if (failures.incrementAndGet() == pool.size) {
                        result.completeExceptionally(
                            lastError.get()
                                ?: ApiError(ApiError.Kind.NETWORK, "هیچ سروری جواب نداد")
                        )
                    }
                }
            }
        }

        try {
            withTimeout(20_000) { result.await() }
        } catch (e: TimeoutCancellationException) {
            throw ApiError(ApiError.Kind.NETWORK, "سرور جواب نداد")
        } finally {
            jobs.forEach { it.cancel() }
        }
    }

    /** پینگ ساده برای مرتب‌کردن اندپوینت‌ها. -1 یعنی در دسترس نیست. */
    suspend fun ping(base: String): Long = withContext(Dispatchers.IO) {
        val t0 = System.nanoTime()
        try {
            val req = Request.Builder()
                .url(base.trimEnd('/') + "/api/v1/health")
                .head()
                .build()
            client.newCall(req).execute().use {
                if (it.isSuccessful) (System.nanoTime() - t0) / 1_000_000 else -1L
            }
        } catch (e: IOException) {
            -1L
        }
    }
}

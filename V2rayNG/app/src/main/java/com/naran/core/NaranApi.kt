package com.naran.core

import kotlinx.coroutines.*
import okhttp3.*
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
            // بیرون از تونل. اگر از تونل می‌رفت، پنلِ روی خاک ایران از
            // دید سرور خارجی دور یا غیرقابل دسترس می‌شد.
            .socketFactory(NaranDirect.socketFactory)
            .dns { hostname -> NaranDirect.resolve(hostname) }
            // در نسخه‌ی نهایی pinning را روشن کنید — راهنما در PATCHES.md
            // .certificatePinner(NaranPinning.pinner())
            .build()
    }

    class ApiError(val kind: Kind, message: String) : Exception(message) {
        enum class Kind { NETWORK, SIGNATURE, RATE_LIMIT, SERVER }
    }

    /** POST امضاشده به یک اندپوینت مشخص. */
    private suspend fun postTo(base: String, path: String, body: JSONObject): JSONObject =
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

            client.newCall(req).execute().use { res ->
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
    ): Pair<JSONObject, String> = coroutineScope {
        val pool = endpoints
            .filter { !configOnly || it.servesConfigs }
            .sortedBy { it.priority }

        if (pool.isEmpty()) throw ApiError(ApiError.Kind.NETWORK, "آدرسی برای اتصال نیست")

        val result = CompletableDeferred<Pair<JSONObject, String>>()
        val failures = java.util.concurrent.atomic.AtomicInteger(0)
        val lastError = java.util.concurrent.atomic.AtomicReference<Exception>(null)

        val jobs = pool.mapIndexed { index, ep ->
            launch {
                // چند ده میلی‌ثانیه فاصله تا اگر اولی سالم است بقیه بی‌خود زده نشوند
                delay(index * 120L)
                if (result.isCompleted) return@launch
                try {
                    val json = postTo(ep.url, path, body)
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

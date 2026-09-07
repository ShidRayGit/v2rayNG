package com.naran.core

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * دانلود و نصب آپدیت از داخل اپ.
 *
 * اندروید اجازه نمی‌دهد اپ خودش را نصب کند — آخر کار همیشه صفحه‌ی نصب
 * سیستم می‌آید و کاربر باید تأیید کند. کاری که اینجا می‌کنیم فقط کوتاه
 * کردن مسیر است: به‌جای مرورگر و پوشه‌ی Downloads، دانلود داخل اپ و بعد
 * مستقیم صفحه‌ی نصب.
 *
 * هر جا شکست بخورد، لینک در مرورگر باز می‌شود تا کاربر بن‌بست نبیند.
 */
object NaranUpdate {

    sealed class State {
        data object Idle : State()
        data class Downloading(val percent: Int) : State()
        data class Ready(val file: File) : State()
        data class Failed(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun reset() { _state.value = State.Idle }

    /** آیا این لینک مستقیم به فایل می‌رسد یا یک صفحه‌ی وب است؟ */
    fun isDirectApk(url: String): Boolean {
        val u = url.trim().lowercase()
        if (!u.startsWith("http")) return false
        // تلگرام و امثالش صفحه می‌دهند نه فایل
        val page = listOf("t.me", "telegram.me", "://drive.google", "://mega.")
        if (page.any { it in u }) return false
        return u.substringBefore("?").endsWith(".apk")
    }

    /**
     * دانلود با گزارش پیشرفت.
     * @return فایل، یا null اگر نشد
     */
    suspend fun download(context: Context, url: String): File? =
        withContext(Dispatchers.IO) {
            _state.value = State.Downloading(0)

            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            // قبلی‌ها را پاک کن تا حافظه پر نشود
            dir.listFiles()?.forEach { runCatching { it.delete() } }
            val out = File(dir, "naran-update.apk")

            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { res ->
                    if (!res.isSuccessful) {
                        _state.value = State.Failed("HTTP " + res.code)
                        return@withContext null
                    }
                    val body = res.body ?: run {
                        _state.value = State.Failed("پاسخ خالی")
                        return@withContext null
                    }
                    val total = body.contentLength()
                    var read = 0L

                    body.byteStream().use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                output.write(buf, 0, n)
                                read += n
                                if (total > 0) {
                                    _state.value = State.Downloading(
                                        ((read * 100) / total).toInt().coerceIn(0, 100)
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                NaranLog.e("آپدیت", "دانلود نشد")
                _state.value = State.Failed(T.offline)
                runCatching { out.delete() }
                return@withContext null
            }

            // فایل کوچک یعنی احتمالاً صفحه‌ی خطا گرفته‌ایم نه APK
            if (out.length() < 1_000_000) {
                NaranLog.e("آپدیت", "فایل دانلودشده APK نیست")
                _state.value = State.Failed(T.updateNotApk)
                runCatching { out.delete() }
                return@withContext null
            }

            NaranLog.i("آپدیت", "دانلود شد: " + (out.length() / 1024 / 1024) + " مگ")
            _state.value = State.Ready(out)
            out
        }

    /**
     * صفحه‌ی نصب سیستم را باز می‌کند.
     *
     * از اندروید ۸ کاربر باید یک بار «نصب از این منبع» را برای ناران
     * فعال کند. اگر نکرده باشد، به همان صفحه‌ی تنظیمات می‌بریمش.
     */
    fun install(context: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            runCatching {
                context.startActivity(
                    Intent(
                        android.provider.Settings
                            .ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        ("package:" + context.packageName).toUri()
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return false
        }

        return runCatching {
            val uri = FileProvider.getUriForFile(
                context, context.packageName + ".naranprovider", file
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        }.onFailure {
            NaranLog.e("آپدیت", "صفحه‌ی نصب باز نشد")
        }.getOrDefault(false)
    }
}

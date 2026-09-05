package com.naran.core

import android.net.TrafficStats
import android.os.Process
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * مصرف و سرعت لحظه‌ای، فقط برای نمایش به کاربر.
 *
 * چون همه‌ی ترافیک تونل از UID خود اپ رد می‌شود، TrafficStats عدد درستی
 * می‌دهد. هیچ‌چیزی به سرور گزارش نمی‌شود.
 *
 * نکته: شمارنده‌های TrafficStats با ری‌بوت صفر می‌شوند، پس همه‌جا دلتا
 * نگه می‌داریم نه عدد خام.
 */
object NaranTraffic {

    data class Snapshot(
        val upBps: Long = 0,      // بایت بر ثانیه
        val downBps: Long = 0,
        val sessionUp: Long = 0,  // مجموع این اتصال
        val sessionDown: Long = 0,
        val elapsedMs: Long = 0
    )

    private val _flow = MutableStateFlow(Snapshot())
    val flow: StateFlow<Snapshot> = _flow

    private var job: Job? = null
    private val uid = Process.myUid()

    private var baseUp = 0L
    private var baseDown = 0L
    private var lastUp = 0L
    private var lastDown = 0L
    private var startedAt = 0L

    private fun rawUp() = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0)
    private fun rawDown() = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0)

    fun start(scope: CoroutineScope) {
        stop()
        baseUp = rawUp(); baseDown = rawDown()
        lastUp = baseUp; lastDown = baseDown
        startedAt = System.currentTimeMillis()
        _flow.value = Snapshot()

        job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                val up = rawUp()
                val down = rawDown()

                // ری‌بوت یا ریست شمارنده
                if (up < lastUp || down < lastDown) {
                    baseUp = up; baseDown = down
                    lastUp = up; lastDown = down
                    continue
                }

                _flow.value = Snapshot(
                    upBps = up - lastUp,
                    downBps = down - lastDown,
                    sessionUp = up - baseUp,
                    sessionDown = down - baseDown,
                    elapsedMs = System.currentTimeMillis() - startedAt
                )
                lastUp = up; lastDown = down
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    // ── قالب‌بندی، بر اساس زبان انتخابی ──

    fun fa(s: String): String = T.num(s)

    fun bytes(n: Long): String {
        val u = T.bytesUnits
        var v = n.toDouble(); var i = 0
        while (v >= 1024 && i < u.size - 1) { v /= 1024; i++ }
        val num = if (i == 0) "${'$'}{v.toInt()}" else String.format("%.1f", v)
        return T.num(num) + " " + u[i]
    }

    fun speed(bps: Long): String {
        val bits = bps * 8.0
        return when {
            bits >= 1_000_000 -> T.num(String.format("%.1f", bits / 1_000_000)) + " " + T.mbps
            bits >= 1_000 -> T.num(String.format("%.0f", bits / 1_000)) + " " + T.kbps
            else -> T.num(bps.toString()) + " " + T.bps
        }
    }

    fun duration(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600; val m = s % 3600 / 60; val sec = s % 60
        return T.num(
            if (h > 0) String.format("%d:%02d:%02d", h, m, sec)
            else String.format("%02d:%02d", m, sec)
        )
    }
}

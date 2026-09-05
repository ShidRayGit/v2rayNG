package com.naran.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * وضعیت واقعی سرویس.
 *
 * سرویس VPN در پروسه‌ی جداست، پس خواندن مستقیم CoreServiceManager.isRunning()
 * از اکتیویتی همیشه false می‌دهد — دلیل گیر کردن روی «در حال اتصال».
 * v2rayNG وضعیت را با broadcast پخش می‌کند و اینجا به همان گوش می‌دهیم.
 */
object NaranServiceState {

    enum class State { OFF, CONNECTING, ON, FAILED }

    private val _state = MutableStateFlow(State.OFF)
    val state: StateFlow<State> = _state

    /** پیام خطای سرویس. بدون این فقط «برقرار نشد» می‌بینیم، نه علتش. */
    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error

    /** نتیجه‌ی پینگ برای سروری که تست شده. میلی‌ثانیه، منفی یعنی ناموفق. */
    private val _ping = MutableSharedFlow<Long>(
        replay = 1, extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val ping: SharedFlow<Long> = _ping.asSharedFlow()

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING,
                AppConfig.MSG_STATE_START_SUCCESS -> {
                    _error.value = ""
                    if (_state.value != State.ON) NaranLog.i("اتصال", "برقرار شد")
                    _state.value = State.ON
                }

                AppConfig.MSG_STATE_NOT_RUNNING,
                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    if (_state.value == State.ON) NaranLog.w("اتصال", "قطع شد")
                    _state.value = State.OFF
                }

                // بدون این، اتصال ناموفق تا ابد روی «در حال اتصال» می‌ماند
                AppConfig.MSG_STATE_START_FAILURE -> {
                    val msg = intent.getStringExtra("content").orEmpty()
                    _error.value = msg
                    NaranLog.e("اتصال", "شکست: ${msg.ifBlank { "بدون توضیح" }}")
                    _state.value = State.FAILED
                }

                AppConfig.MSG_MEASURE_DELAY_RESULT -> {
                    val ms = runCatching {
                        val obj = intent.getSerializableExtra("content")
                        obj?.javaClass?.methods
                            ?.firstOrNull { it.name == "getElapsed" || it.name == "getTime" }
                            ?.invoke(obj) as? Long
                    }.getOrNull() ?: -1L
                    NaranLog.i("پینگ", if (ms > 0) "$ms ms" else "بی‌پاسخ")
                    _ping.tryEmit(ms)
                }
            }
        }
    }

    fun register(context: Context) {
        if (registered) return
        val app = context.applicationContext
        runCatching {
            ContextCompat.registerReceiver(
                app, receiver,
                IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
                Utils.receiverFlags()
            )
            MessageHelper.sendMsg2Service(app, AppConfig.MSG_REGISTER_CLIENT, "")
            registered = true
        }
    }

    fun unregister(context: Context) {
        if (!registered) return
        val app = context.applicationContext
        runCatching { MessageHelper.sendMsg2Service(app, AppConfig.MSG_UNREGISTER_CLIENT, "") }
        runCatching { app.unregisterReceiver(receiver) }
        registered = false
    }

    fun markConnecting() {
        _error.value = ""
        _state.value = State.CONNECTING
    }

    fun fail(message: String) {
        NaranLog.e("اتصال", message)
        _error.value = message
        _state.value = State.FAILED
    }
    fun markStopping() { _state.value = State.OFF }

    /**
     * تست همه‌ی کانفیگ‌ها.
     *
     * v2rayNG این را با MSG_MEASURE_CONFIG انجام می‌دهد و نتیجه‌ها را
     * تک‌تک با MSG_MEASURE_DELAY_RESULT می‌فرستد.
     */
    fun requestPingAll(context: Context) {
        runCatching {
            MessageHelper.sendMsg2Service(
                context.applicationContext, AppConfig.MSG_MEASURE_CONFIG, ""
            )
        }.onFailure { NaranLog.w("پینگ", "تست همه شروع نشد") }
    }

    /** درخواست پینگ سرور انتخاب‌شده. نتیجه از فلوی ping می‌آید. */
    fun requestPing(context: Context) {
        runCatching {
            MessageHelper.sendMsg2Service(
                context.applicationContext, AppConfig.MSG_MEASURE_DELAY, ""
            )
        }
    }
}

package com.naran.core

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * اتصال مستقیم، بیرون از تونل.
 *
 * وقتی VPN روشن است همه‌ی ترافیک اپ از تونل رد می‌شود. برای درخواست‌های
 * خودمان به پنل این بد است: اگر پنل روی سرور ایران باشد، از خارج سخت یا
 * غیرقابل دسترس می‌شود.
 *
 * راه‌حل: به‌جای شبکه‌ی پیش‌فرض (که VPN است)، شبکه‌ی زیرین را پیدا کن و
 * سوکت را مستقیم روی همان بساز. برخلاف VpnService.protect نیازی به
 * دسترسی به خود سرویس ندارد و از هر جای اپ کار می‌کند.
 */
object NaranDirect {

    @Volatile private var appContext: Context? = null

    /**
     * بستن IPv6 بعد از تشخیص نشت.
     *
     * وقتی روشن است، حل نام فقط آدرس‌های IPv4 برمی‌گرداند، پس ترافیک از
     * مسیر IPv6 دور تونل نمی‌زند. این فقط برای درخواست‌های خود اپ است؛
     * ترافیک بقیه‌ی اپ‌ها دست هسته است.
     */
    @Volatile var blockIpv6: Boolean = false

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** شبکه‌ی واقعی دستگاه — وای‌فای یا دیتا، نه VPN. */
    @SuppressLint("MissingPermission")
    private fun underlying(): Network? {
        val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return null

        return runCatching {
            cm.allNetworks.firstOrNull { net ->
                val caps = cm.getNetworkCapabilities(net) ?: return@firstOrNull false
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } ?: cm.allNetworks.firstOrNull { net ->
                // اگر هیچ شبکه‌ای VALIDATED نبود، سخت‌گیری را کم کن
                val caps = cm.getNetworkCapabilities(net) ?: return@firstOrNull false
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }.getOrNull()
    }

    /**
     * SocketFactory که از تونل رد نمی‌شود.
     *
     * اگر شبکه‌ی زیرین پیدا نشود، به SocketFactory پیش‌فرض برمی‌گردد —
     * یعنی بدترین حالت همان رفتار قبلی است، نه خرابی.
     */
    val socketFactory: SocketFactory = object : SocketFactory() {

        private fun make(): Socket =
            underlying()?.socketFactory?.createSocket() ?: Socket()

        override fun createSocket(): Socket = make()

        override fun createSocket(host: String, port: Int): Socket =
            make().apply { connect(java.net.InetSocketAddress(host, port)) }

        override fun createSocket(
            host: String, port: Int, localHost: InetAddress, localPort: Int
        ): Socket = make().apply {
            bind(java.net.InetSocketAddress(localHost, localPort))
            connect(java.net.InetSocketAddress(host, port))
        }

        override fun createSocket(host: InetAddress, port: Int): Socket =
            make().apply { connect(java.net.InetSocketAddress(host, port)) }

        override fun createSocket(
            address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int
        ): Socket = make().apply {
            bind(java.net.InetSocketAddress(localAddress, localPort))
            connect(java.net.InetSocketAddress(address, port))
        }
    }

    /** DNS از شبکه‌ی زیرین، چون DNS تونل هم ممکن است مقصد را حل نکند. */
    fun resolve(host: String): List<InetAddress> {
        val all = runCatching { underlying()?.getAllByName(host)?.toList() }.getOrNull()
            ?: InetAddress.getAllByName(host).toList()

        if (!blockIpv6) return all
        // اگر فیلتر کردن چیزی باقی نگذارد، همان فهرست اصلی بهتر از
        // شکست کامل است.
        val v4 = all.filterIsInstance<java.net.Inet4Address>()
        return v4.ifEmpty { all }
    }

    fun isTunnelActive(): Boolean {
        val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return false
        return runCatching {
            cm.allNetworks.any {
                cm.getNetworkCapabilities(it)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        }.getOrDefault(false)
    }
}

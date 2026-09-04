package com.naran.core

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * امضای درخواست‌ها.
 *
 * کلید از لایه‌ی native می‌آید و هیچ‌وقت به‌صورت رشته در کاتلین نیست.
 * برای ساخت libnaran.so به android/jni/ نگاه کنید.
 */
object NaranCrypto {

    init { System.loadLibrary("naran") }

    /** کلید مشترک با سرور. از JNI می‌آید تا با jadx مستقیم درنیاید. */
    private external fun apiSecret(): ByteArray

    fun sign(method: String, path: String, ts: String, body: ByteArray): String {
        val prefix = "$method|$path|$ts|".toByteArray(Charsets.UTF_8)
        val msg = ByteArray(prefix.size + body.size)
        System.arraycopy(prefix, 0, msg, 0, prefix.size)
        System.arraycopy(body, 0, msg, prefix.size, body.size)

        val key = apiSecret()
        try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(msg).joinToString("") { "%02x".format(it) }
        } finally {
            key.fill(0)   // از حافظه پاک شود
        }
    }
}

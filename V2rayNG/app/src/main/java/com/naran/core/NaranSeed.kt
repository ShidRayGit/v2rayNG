package com.naran.core

/**
 * مقادیر اولیه‌ای که با APK بیرون می‌روند.
 *
 * این‌ها فقط بذرند: بعد از اولین sync، لیست واقعی از سرور می‌آید و ذخیره
 * می‌شود. پس اگر فردا دامنه‌ی تازه‌ای خریدید، لازم نیست APK جدید بدهید —
 * فقط در پنل اضافه‌اش کنید.
 */
object NaranSeed {

    const val CHANNEL_NAME = "کانال ناران"
    const val CHANNEL_URL = "https://t.me/YOUR_CHANNEL"

    val ENDPOINTS = listOf(
        NaranEndpoint("https://shwkcia.baaq-yas.ir",    "اصلی",    true,  10),
        NaranEndpoint("https://pqhfmc.baaq-yas.ir",     "دوم",     true,  20),
        NaranEndpoint("https://dpccjwzn.xavino-shop.ir","سوم",     true,  30),
        NaranEndpoint("https://jeeofiah.chamehdan.ir",  "چهارم",   true,  40),
        NaranEndpoint("https://jdepcoajes.shidray.ir",  "پنجم",    true,  50),
        NaranEndpoint("https://sofpccb.cursedland.ir",  "ششم",     true,  60),
        NaranEndpoint("https://gddociser.borfema.ir",   "رزرو",    true,  70),

        // آخرین راه: اگر DNS هم کار نکرد. IP سرور را اینجا بگذارید.
        NaranEndpoint("http://0.0.0.0:8080",            "مستقیم",  true, 100)
    )
}

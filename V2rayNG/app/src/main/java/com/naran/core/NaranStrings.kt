package com.naran.core

import android.content.Context
import java.util.Locale

/**
 * متن‌های دو زبانه.
 *
 * پیش‌فرض انگلیسی است و فارسی کنارش. زبان در تنظیمات قابل تغییر است و
 * اگر کاربر دست نزده باشد، از زبان گوشی حدس زده می‌شود.
 *
 * عمداً از strings.xml استفاده نمی‌کنیم: تغییر زبان بدون ری‌استارت اپ
 * با منابع اندروید دردسر دارد، و این ماژول باید مستقل از فورک بماند.
 */
object T {

    enum class Lang { EN, FA }

    @Volatile var lang: Lang = Lang.EN

    val isRtl: Boolean get() = lang == Lang.FA

    fun init(ctx: Context) {
        val saved = NaranStore.language
        lang = when (saved) {
            "fa" -> Lang.FA
            "en" -> Lang.EN
            else -> if (Locale.getDefault().language == "fa") Lang.FA else Lang.EN
        }
    }

    fun set(l: Lang) {
        lang = l
        NaranStore.language = if (l == Lang.FA) "fa" else "en"
    }

    private fun pick(en: String, fa: String) = if (lang == Lang.FA) fa else en

    /** ارقام فارسی فقط وقتی زبان فارسی است. */
    fun num(v: Any): String {
        val s = v.toString()
        if (lang != Lang.FA) return s
        val digits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        return buildString { s.forEach { append(if (it in '0'..'9') digits[it - '0'] else it) } }
    }

    // ── عمومی ──
    val appName get() = "Naran"
    val back get() = pick("Back", "بازگشت")
    val cancel get() = pick("Cancel", "بی‌خیال")
    val save get() = pick("Save", "ذخیره")
    val delete get() = pick("Delete", "حذف")
    val retry get() = pick("Try again", "دوباره")
    val loading get() = pick("Loading…", "در حال بارگذاری…")
    val optional get() = pick("optional", "اختیاری")

    // ── لایسنس ──
    val licenseTitle get() = pick("Enter your code", "کد را وارد کنید")
    fun licenseSub(channel: String) =
        pick("Get today's code from $channel", "کد امروز را از $channel بگیرید")
    val licenseHint get() = pick("License code", "کد لایسنس")
    val activate get() = pick("Activate", "فعال کن")
    fun openChannel(name: String) = pick("Open $name", "رفتن به $name")
    val codeEmpty get() = pick("Enter the code", "کد را وارد کنید")
    val offline get() = pick(
        "Couldn't reach the server. Check your connection and try again.",
        "به سرور وصل نشد. اینترنت را بررسی کنید و دوباره بزنید."
    )

    // ── اتصال ──
    val connect get() = pick("Connect", "اتصال")
    val disconnect get() = pick("Stop", "قطع")
    val connecting get() = pick("Connecting…", "در حال اتصال…")
    val connected get() = pick("Connected", "متصل هستید")
    val notConnected get() = pick("Not connected", "قطع")
    val failed get() = pick("Couldn't connect", "اتصال برقرار نشد")
    val failedHint get() = pick(
        "Try another server or get a fresh code",
        "سرور دیگری را امتحان کنید یا کد تازه بگیرید"
    )
    val download get() = pick("Download", "دریافت")
    val upload get() = pick("Upload", "ارسال")
    val sessionUsage get() = pick("This session", "مصرف این اتصال")
    val duration get() = pick("Duration", "مدت")
    val change get() = pick("Change", "تغییر")
    val noServer get() = pick("No server selected", "سروری انتخاب نشده")
    val tapToPick get() = pick("Tap to choose", "برای انتخاب بزنید")
    fun hoursLeft(h: Any) = pick("${num(h)}h left", "${num(h)} ساعت مانده")
    fun minutesLeft(m: Any) = pick("${num(m)}m left", "${num(m)} دقیقه مانده")

    // ── بررسی اتصال ──
    val checking get() = pick("Checking…", "در حال بررسی…")
    val verified get() = pick("Traffic verified", "خروجی تأیید شد")
    val noExitIp get() = pick("Exit address unknown", "آدرس خروجی مشخص نشد")
    val internetOpen get() = pick("Internet is open", "اینترنت باز است")
    val tunnelUnverified get() = pick("Tunnel not verified", "تونل تأیید نشد")
    val notTunneled get() = pick("Traffic isn't going through", "ترافیک رد نمی‌شود")
    val connectedNoTraffic get() = pick(
        "Connected, but nothing gets through",
        "متصل شد، ولی ترافیک رد نمی‌شود"
    )
    val connectedNoTrafficHint get() = pick(
        "This server looks dead. Pick another one.",
        "این سرور جواب نمی‌دهد. سرور دیگری را انتخاب کنید."
    )
    val verifying get() = pick("Checking connection…", "در حال بررسی اتصال…")
    val refresh get() = pick("Refresh", "به‌روزرسانی")
    val add get() = pick("Add", "افزودن")
    val addTitle get() = pick("What do you want to add?", "چه چیزی اضافه کنیم؟")
    val addLicense get() = pick("License code", "کد لایسنس")
    val addLicenseSub get() = pick("The code from the channel", "کدی که در کانال است")
    val addSubLink get() = pick("Subscription link", "لینک سابسکریپشن")
    val addSubLinkSub get() = pick("A link from your provider", "لینکی از سرویس‌دهنده")
    val addClipboard get() = pick("Paste a config", "چسباندن کانفیگ")
    val addClipboardSub get() = pick(
        "Reads a config link from the clipboard",
        "لینک کانفیگ را از کلیپ‌بورد می‌خواند"
    )
    val clipboardEmpty get() = pick("Clipboard is empty", "کلیپ‌بورد خالی است")
    val clipboardBad get() = pick(
        "No config link found in the clipboard",
        "لینک کانفیگی در کلیپ‌بورد نبود"
    )
    fun clipboardAdded(n: Any) =
        pick("${num(n)} added", "${num(n)} کانفیگ اضافه شد")
    val mySubs get() = pick("My subscriptions", "سابسکریپشن‌های من")
    val connectNow get() = pick("Connect", "اتصال")
    val selected get() = pick("Selected", "انتخاب شد")
    val manage get() = pick("Manage", "مدیریت")
    val untested get() = pick("not tested", "تست نشده")
    val noConfigsInSub get() = pick(
        "This subscription has no servers yet",
        "این ساب هنوز سروری ندارد"
    )
    fun subServers(n: Any) = pick("${num(n)} servers", "${num(n)} سرور")
    val refreshing get() = pick("Refreshing…", "در حال به‌روزرسانی…")
    val refreshDone get() = pick("Up to date", "به‌روز شد")
    val notifyPermTitle get() = pick("Allow notifications?", "اعلان‌ها مجاز باشند؟")
    val notifyPermBody get() = pick(
        "Naran shows connection status and speed in a notification. " +
            "You can skip this — the app works either way.",
        "ناران وضعیت اتصال و سرعت را در اعلان نشان می‌دهد. " +
            "می‌توانید رد کنید؛ اپ به‌هرحال کار می‌کند."
    )
    val allow get() = pick("Allow", "اجازه بده")
    val notNow get() = pick("Not now", "الان نه")
    val sameIp get() = pick(
        "Your address hasn't changed — tunnel has no effect",
        "آدرس شما عوض نشده — تونل بی‌اثر است"
    )
    val ipv6Blocked get() = pick("IPv6 blocked to stop leaking", "IPv6 بسته شد تا نشت نکند")
    val tryAnotherServer get() = pick("Try another server", "سرور دیگری را امتحان کنید")

    // ── سرورها ──
    val servers get() = pick("Servers", "سرورها")
    val yourServers get() = pick("Your servers", "سرورهای شما")
    val yourServersSub get() = pick(
        "From codes you entered", "از کدهایی که وارد کرده‌اید"
    )
    val publicServers get() = pick("Public servers", "اتصال عمومی")
    val publicSub get() = pick("Shared by the community", "اهدایی کاربران")
    val noServers get() = pick("No servers yet", "هنوز سروری ندارید")
    val addCode get() = pick("Enter a new code", "وارد کردن کد جدید")
    // کوتاه، برای دکمه‌های کنار هم که جا تنگ است
    val addCodeShort get() = pick("Code", "کد")
    val subsShort get() = pick("Subs", "ساب")
    val donateShort get() = pick("Share", "اهدا")
    val findServer get() = pick("Find a server", "جستجوی کانفیگ")
    val searching get() = pick("Searching…", "در حال جستجو…")
    val active get() = pick("active", "فعال")
    val trial get() = pick("testing", "آزمایشی")
    val noneAvailable get() = pick(
        "No free server right now. Try again later.",
        "فعلاً سرور آزادی نیست. بعداً دوباره بزنید."
    )
    fun byDonor(name: String) = pick("by $name", "از $name")
    val forgetTitle get() = pick("Remove this server?", "این سرور حذف شود؟")
    val forgetBody get() = pick(
        "It will be removed from this device. If the code is still valid you can enter it again.",
        "از دستگاه شما پاک می‌شود. اگر کدش هنوز معتبر باشد، می‌توانید دوباره واردش کنید."
    )

    // ── اهدا ──
    val donate get() = pick("Share a server", "اهدای کانفیگ")
    val donateSub get() = pick(
        "Have a spare server? Share it and help others get online.",
        "سرور اضافه دارید؟ اهدا کنید تا بقیه هم وصل شوند."
    )
    val donateConfig get() = pick("Config link", "لینک کانفیگ")
    val donateName get() = pick("Name for this server", "اسم این سرور")
    val donateNameHint get() = pick("Frankfurt Fast", "مثلاً آلمان سریع")
    val donorName get() = pick("Your display name", "اسم نمایشی شما")
    val donorNameHint get() = pick("Shown publicly", "عمومی نمایش داده می‌شود")
    val telegram get() = pick("Telegram username", "آیدی تلگرام")
    val capacity get() = pick("How many users can it handle?", "چند کاربر را جواب می‌دهد؟")
    val volumeLimit get() = pick("Traffic limit", "محدودیت حجم")
    val unlimited get() = pick("Unlimited", "نامحدود")
    fun limitedGb(min: Any) = pick("Limited (min ${num(min)} GB)", "محدود (حداقل ${num(min)} گیگ)")
    val gigabytes get() = pick("Gigabytes", "گیگابایت")
    val sendDonation get() = pick("Send", "ارسال")
    val donateThanks get() = pick(
        "Thank you. It'll be reviewed and shared soon.",
        "ممنون. بررسی و به‌زودی در دسترس قرار می‌گیرد."
    )
    val donateBadLink get() = pick("That config link doesn't look right", "لینک کانفیگ درست نیست")

    // ── سابسکریپشن ──
    val subscriptions get() = pick("Subscriptions", "سابسکریپشن")
    val addSub get() = pick("Add subscription", "افزودن ساب")
    val subUrl get() = pick("Subscription link", "لینک سابسکریپشن")
    val subBadUrl get() = pick("That doesn't look like a link", "این یک لینک نیست")
    val subEmpty get() = pick("No configs found in that link", "کانفیگی در این لینک نبود")
    val subFetchFailed get() = pick("Couldn't load that link", "لینک باز نشد")
    fun subNotAllowed(domain: String) =
        pick("$domain isn't allowed", "لینک $domain مجاز نیست")
    val subAllowedList get() = pick("Allowed sources:", "منابع مجاز:")
    val sortByBest get() = pick("Sort by best", "مرتب‌سازی خودکار")
    val pingAll get() = pick("Ping all", "تست همه")
    fun pingProgress(done: Any, total: Any) =
        pick("Testing ${num(done)}/${num(total)}", "تست ${num(done)} از ${num(total)}")
    val pingNeedsConnect get() = pick(
        "Connect first to test this server",
        "برای تست این سرور اول وصل شوید"
    )
    val testPing get() = pick("Test latency", "تست تأخیر")
    val showSpeed get() = pick("Speed in notification", "سرعت در نوتیفیکیشن")
    val showSpeedSub get() = pick(
        "Updates every 3 seconds while connected",
        "هنگام اتصال هر ۳ ثانیه به‌روز می‌شود"
    )
    val hideSpeed get() = pick("Hide speed", "پنهان کردن سرعت")
    val disconnectAction get() = pick("Disconnect", "قطع اتصال")
    val updateNow get() = pick("Update now", "به‌روزرسانی")
    val updated get() = pick("Updated", "به‌روز شد")
    fun subUsage(used: String, total: String) =
        pick("$used of $total", "$used از $total")
    fun subUsed(used: String) = pick("$used used", "$used مصرف")
    fun subExpires(d: Any) = pick("${num(d)} days left", "${num(d)} روز مانده")
    val subExpired get() = pick("Expired", "منقضی")
    val subUnlimited get() = pick("Unlimited", "نامحدود")
    val removeSub get() = pick("Remove subscription?", "این ساب حذف شود؟")
    val removeSubBody get() = pick(
        "All servers from this link will be removed.",
        "همه‌ی سرورهای این لینک پاک می‌شوند."
    )
    val noConfigsYet get() = pick(
        "Add a code or a subscription to get started",
        "برای شروع یک کد یا لینک ساب اضافه کنید"
    )

    // ── اطلاعیه و مسدودسازی ──
    val notices get() = pick("Announcements", "اطلاعیه‌ها")
    val gotIt get() = pick("Got it", "متوجه شدم")
    val blocking get() = pick("Blocked sites", "سایت‌های مسدود")
    val blockingSub get() = pick(
        "Set by your provider. Optional ones you can turn off.",
        "توسط سرویس‌دهنده تعیین شده. موارد اختیاری را می‌توانید خاموش کنید."
    )
    val required get() = pick("required", "اجباری")

    // ── تنظیمات ──
    val settings get() = pick("Settings", "تنظیمات")
    val connection get() = pick("Connection", "اتصال")
    val autoConnect get() = pick("Auto connect", "اتصال خودکار")
    val autoConnectSub get() = pick(
        "Connect to the last server on launch",
        "با باز شدن اپ، به آخرین سرور وصل شود"
    )
    val perApp get() = pick("Apps outside the tunnel", "اپ‌های خارج از تونل")
    val perAppSub get() = pick(
        "Keep local apps off the VPN", "اپ‌های ایرانی از تونل رد نشوند"
    )
    val language get() = pick("Language", "زبان")
    val about get() = pick("About", "درباره")
    val currentVersion get() = pick("Current version", "نسخه فعلی")
    fun updateReady(v: String) = pick("Version $v is out", "نسخه‌ی $v آمده")
    val tapToGet get() = pick("Tap to download", "برای دریافت بزنید")
    val report get() = pick("Diagnostics", "گزارش")
    val reportSub get() = pick("For when something doesn't work", "برای وقتی چیزی کار نمی‌کند")

    // ── گزارش ──
    val reportTitle get() = pick("Diagnostics", "گزارش")
    val reportNotice get() = pick(
        "Server addresses, IDs and keys are stripped from this text. Still, give it a " +
            "glance before sharing it with anyone.",
        "آدرس سرور، شناسه‌ها و کلیدها از این متن پاک می‌شوند. با این حال قبل از " +
            "فرستادن برای کسی، یک نگاه بیندازید."
    )
    val coreLog get() = pick("Core log", "گزارش هسته")
    val coreLogSub get() = pick(
        "For troubleshooting only. Turn it off after.",
        "فقط برای عیب‌یابی. بعدش خاموشش کنید."
    )
    val logEmpty get() = pick("Nothing recorded yet", "هنوز چیزی ثبت نشده")
    val copy get() = pick("Copy", "کپی")
    val copied get() = pick("Copied", "کپی شد")
    val clear get() = pick("Clear", "پاک کردن")

    // ── واحدها ──
    val bytesUnits: Array<String>
        get() = if (lang == Lang.FA)
            arrayOf("بایت", "کیلوبایت", "مگابایت", "گیگابایت")
        else arrayOf("B", "KB", "MB", "GB")

    val mbps get() = pick("Mbps", "مگابیت")
    val kbps get() = pick("Kbps", "کیلوبیت")
    val bps get() = pick("B/s", "بایت")
}

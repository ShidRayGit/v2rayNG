#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
اصلاح فایل‌های خود v2rayNG که به MainActivity اشاره می‌کنند.

نوتیفیکیشن و تایل کنترل‌سنتر با کلیک، MainActivity را باز می‌کنند — همان
صفحه‌ای که از لانچر درش آوردیم. بدون این اصلاح، کاربر از نوتیفیکیشن به
رابط قدیمی v2rayNG می‌رسد.

  python3 tools/patch_source.py
"""
import re
import sys
from pathlib import Path

ROOT = Path("V2rayNG/app/src/main/java/com/v2ray/ang")
if not ROOT.is_dir():
    ROOT = Path("app/src/main/java/com/v2ray/ang")
if not ROOT.is_dir():
    sys.exit("پوشه‌ی سورس پیدا نشد")

NARAN = "com.naran.core.ui.NaranActivity"
# Class.forName چون ماژول ناران بعد از این فایل کامپایل می‌شود
CLASS_REF = 'Class.forName("' + NARAN + '")'
done = []

# فایل‌هایی که ممکن است MainActivity را باز کنند
for rel in ["handler/NotificationManager.kt", "service/QSTileService.kt",
            "ui/shortcut/", "service/CoreVpnService.kt"]:
    target = ROOT / rel
    files = sorted(target.rglob("*.kt")) if target.is_dir() else (
        [target] if target.is_file() else [])

    for f in files:
        s = f.read_text(encoding="utf-8")
        orig = s

        # Intent(ctx, MainActivity::class.java) → NaranActivity
        # یک جایگزینی کافی است و همه‌ی حالت‌ها را می‌گیرد
        s = s.replace("MainActivity::class.java", CLASS_REF)

        if s != orig:
            # ایمپورت بی‌استفاده مشکلی نمی‌سازد ولی تمیزش می‌کنیم
            if "MainActivity" not in s:
                s = re.sub(r"^import com\.v2ray\.ang\.ui\.main\.MainActivity\n", "",
                           s, flags=re.M)
            f.write_text(s, encoding="utf-8")
            done.append(f.relative_to(ROOT).as_posix())

# ── آیکون تایل در زمان اجرا ──
# QSTileService آیکون را با qsTile.icon بازنویسی می‌کند، پس عوض کردن
# منیفست تنهایی کافی نیست و آیکون بعد از یک لحظه به V برمی‌گشت.
tile = ROOT / "service/QSTileService.kt"
if tile.is_file():
    t = tile.read_text(encoding="utf-8")
    if "ic_naran_tile" not in t and "ic_stat_name" in t:
        t = t.replace("R.drawable.ic_stat_name", "R.drawable.ic_naran_tile")
        tile.write_text(t, encoding="utf-8")
        done.append("آیکون تایل در زمان اجرا")

# ── نگهبان MainActivity ──
# صفحات پرخطر از منیفست حذف می‌شوند، ولی MainActivity می‌ماند چون کدهای
# دیگری ممکن است به آن ارجاع بدهند. اگر باز شد، فوراً به ناران می‌رود.
# بدون این، هر مسیر ناشناخته‌ای یا رابط قدیمی را نشان می‌داد یا با
# ActivityNotFoundException کرش می‌کرد.
main = ROOT / "ui/main/MainActivity.kt"
if main.is_file():
    t = main.read_text(encoding="utf-8")
    if "NARAN_GUARD" not in t:
        m = re.search(r"(super\.onCreate\(savedInstanceState\)\n)", t)
        if m:
            # شرط روی isFinishing عمدی است: در زمان اجرا همیشه false
            # است پس نگهبان همیشه کار می‌کند، ولی کامپایلر نمی‌تواند
            # حلش کند — بنابراین کد بعدی «غیرقابل‌دسترس» شمرده نمی‌شود
            # و اگر پروژه هشدار را خطا حساب کند، بیلد نمی‌شکند.
            guard = (
                "\n        // NARAN_GUARD — این صفحه دیگر استفاده نمی‌شود\n"
                "        if (!isFinishing) {\n"
                "            runCatching {\n"
                "                startActivity(\n"
                "                    android.content.Intent(this, "
                'Class.forName("' + NARAN + '"))\n'
                "                        .addFlags(android.content.Intent"
                ".FLAG_ACTIVITY_CLEAR_TOP)\n"
                "                )\n"
                "            }\n"
                "            finish()\n"
                "            return\n"
                "        }\n"
            )
            t = t[:m.end()] + guard + t[m.end():]
            main.write_text(t, encoding="utf-8")
            done.append("MainActivity → نگهبان تغییر مسیر")

if done:
    for d in done:
        print(f"  {d}")
else:
    print("  چیزی لازم نبود")

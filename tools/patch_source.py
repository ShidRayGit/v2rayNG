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

if done:
    for d in done:
        print(f"  {d} → NaranActivity")
else:
    print("  چیزی لازم نبود")

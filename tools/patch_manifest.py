#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
اصلاح AndroidManifest برای ناران.

  python3 tools/patch_manifest.py

کارهایی که می‌کند:
  ۱. NaranActivity را لانچر می‌کند
  ۲. MainActivity قدیمی را از لانچر درمی‌آورد
  ۳. صفحاتی که کانفیگ را لو می‌دهند غیرفعال می‌شوند
  ۴. allowBackup را خاموش می‌کند
"""
import re
import sys
from pathlib import Path

MANIFEST = Path("V2rayNG/app/src/main/AndroidManifest.xml")
if not MANIFEST.is_file():
    MANIFEST = Path("app/src/main/AndroidManifest.xml")
if not MANIFEST.is_file():
    sys.exit("AndroidManifest.xml پیدا نشد")

src = MANIFEST.read_text(encoding="utf-8")
did = []

# ── ۱. لانچر بودن را از MainActivity بگیر ──
# فقط intent-filter مربوط به LAUNCHER حذف می‌شود، خود اکتیویتی می‌ماند
# چون کدهای دیگر ممکن است به آن ارجاع بدهند.
main_block = re.search(
    r'(<activity\s+android:name="\.ui\.main\.MainActivity".*?</activity>)',
    src, re.S)
if main_block:
    block = main_block.group(1)
    if "LAUNCHER" in block:
        cleaned = re.sub(
            r'<intent-filter>\s*<action\s+android:name="android\.intent\.action\.MAIN"\s*/>.*?</intent-filter>',
            '', block, flags=re.S)
        cleaned = cleaned.replace('android:exported="true"', 'android:exported="false"')
        src = src.replace(block, cleaned)
        did.append("MainActivity از لانچر درآمد")

# ── ۲. NaranActivity را لانچر کن ──
if "com.naran.core.ui.NaranActivity" not in src:
    entry = '''
        <activity
            android:name="com.naran.core.ui.NaranActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:screenOrientation="portrait"
            android:configChanges="orientation|screenSize|keyboardHidden|uiMode">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
'''
    m = re.search(r'(<application\b[^>]*>)', src)
    if not m:
        sys.exit("تگ application پیدا نشد")
    src = src[:m.end()] + entry + src[m.end():]
    did.append("NaranActivity لانچر شد")

# ── ۳. صفحاتی که کانفیگ را نشان می‌دهند ──
# حذفشان از منیفست باعث خطای کامپایل نمی‌شود، فقط از بیرون قابل باز شدن
# نیستند. کد داخلی هم دیگر صدایشان نمی‌زند چون MainActivity باز نمی‌شود.
RISKY = [
    ".ui.ScannerActivity",             # اسکن QR
    ".ui.logcat.LogcatActivity",       # لاگ هسته — کانفیگ در آن چاپ می‌شود
    ".ui.UrlSchemeActivity",           # ورود کانفیگ از لینک بیرونی
]
def drop_activity(text: str, name: str) -> tuple[str, bool]:
    """یک اکتیویتی را از منیفست برمی‌دارد.

    الگوی ساده‌ی regex خطرناک است چون `.*?</activity>` می‌تواند از یک
    اکتیویتی خودبسته رد شود و اکتیویتی بعدی را هم ببلعد. پس از ابتدای تگ
    دستی جلو می‌رویم تا اولین پایانی که واقعاً مال خودش است.
    """
    marker = f'android:name="{name}"'
    at = text.find(marker)
    if at == -1:
        return text, False

    start = text.rfind("<activity", 0, at)
    if start == -1:
        return text, False

    # اولین «>» بعد از شروع تگ
    gt = text.find(">", at)
    if gt == -1:
        return text, False

    if text[gt - 1] == "/":          # <activity ... />
        end = gt + 1
    else:                            # <activity ...> ... </activity>
        close = text.find("</activity>", gt)
        if close == -1:
            return text, False
        end = close + len("</activity>")

    return text[:start] + text[end:], True


for name in RISKY:
    src, ok = drop_activity(src, name)
    if ok:
        did.append(f"{name.rsplit('.', 1)[-1]} برداشته شد")

# ── ۴. سخت‌سازی ──
if 'android:allowBackup="true"' in src:
    src = src.replace('android:allowBackup="true"', 'android:allowBackup="false"')
    did.append("allowBackup خاموش شد")
elif 'android:allowBackup' not in src:
    src = re.sub(r'(<application\b)', r'\1\n        android:allowBackup="false"', src, count=1)
    did.append("allowBackup خاموش شد")

MANIFEST.write_text(src, encoding="utf-8")

if did:
    for d in did:
        print(f"  {d}")
else:
    print("چیزی لازم نبود — قبلاً اعمال شده")

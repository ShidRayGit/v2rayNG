#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
عوض کردن اسم اپ در همه‌ی فایل‌های strings.xml.

  python3 tools/set_app_name.py "Naran VPN"

v2rayNG اسم را با translatable="false" می‌نویسد، پس الگو باید ویژگی‌های
اختیاری را هم بپذیرد. sed ساده این را از دست می‌دهد.
"""
import re
import sys
from pathlib import Path

name = sys.argv[1] if len(sys.argv) > 1 else "Naran VPN"

res = Path("V2rayNG/app/src/main/res")
if not res.is_dir():
    res = Path("app/src/main/res")
if not res.is_dir():
    sys.exit("پوشه‌ی res پیدا نشد")

pattern = re.compile(
    r'(<string\s+name="app_name"[^>]*>)([^<]*)(</string>)'
)

done = 0
for f in sorted(res.glob("values*/strings.xml")):
    text = f.read_text(encoding="utf-8")
    new, n = pattern.subn(rf"\g<1>{name}\g<3>", text)
    if n:
        f.write_text(new, encoding="utf-8")
        done += n
        print(f"  {f.parent.name}/strings.xml → {name}")

if done:
    print(f"اسم اپ در {done} جا عوض شد")
else:
    print("app_name پیدا نشد — چیزی عوض نشد")
    sys.exit(1)

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
اصلاح build.gradle.kts برای ناران.

  python3 tools/patch_gradle.py [--deps]

بدون آرگومان فقط حجم APK را کم می‌کند (دو معماری به‌جای همه).
با --deps وابستگی‌های ماژول ناران را هم اضافه می‌کند.
"""
import re
import sys
from pathlib import Path

GRADLE = Path("V2rayNG/app/build.gradle.kts")
if not GRADLE.is_file():
    GRADLE = Path("app/build.gradle.kts")
if not GRADLE.is_file():
    sys.exit("build.gradle.kts پیدا نشد")

src = GRADLE.read_text(encoding="utf-8")
changed = []

# ── فقط دو معماری رایج ──
# با --slim فعال می‌شود. پیش‌فرض خاموش است چون hev-socks5-tunnel از مسیر
# جدا کپی می‌شود و فیلتر کردن ABI ممکن است ناقصش کند؛ آن وقت هسته بالا
# می‌آید ولی موقع وصل کردن TUN می‌میرد.
if "--slim" in sys.argv and "abiFilters" not in src:
    m = re.search(r"defaultConfig\s*\{", src)
    if m:
        src = src[:m.end()] + '''
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }''' + src[m.end():]
        changed.append("abiFilters")

if "--deps" in sys.argv:
    m = re.search(r"^dependencies\s*\{", src, re.M)
    if not m:
        sys.exit("بلوک dependencies پیدا نشد")

    wanted = [
        ('androidx.security:security-crypto',
         '    implementation("androidx.security:security-crypto:1.1.0-alpha06")'),
        ('compose-bom',
         '    implementation(platform("androidx.compose:compose-bom:2024.09.03"))'),
        ('compose.material3',
         '    implementation("androidx.compose.material3:material3")'),
        ('activity-compose',
         '    implementation("androidx.activity:activity-compose:1.9.2")'),
        ('compose.ui:ui-tooling-preview',
         '    implementation("androidx.compose.ui:ui-tooling-preview")'),
        ('coil-compose',
         '    implementation("io.coil-kt:coil-compose:2.6.0")'),
    ]
    add = [line for key, line in wanted if key not in src]
    if add:
        src = src[:m.end()] + "\n" + "\n".join(add) + src[m.end():]
        changed.append(f"{len(add)} وابستگی")

    # فعال کردن Compose
    if "compose = true" not in src:
        m = re.search(r"buildFeatures\s*\{", src)
        if m:
            src = src[:m.end()] + "\n        compose = true" + src[m.end():]
        else:
            m = re.search(r"^android\s*\{", src, re.M)
            src = src[:m.end()] + """
    buildFeatures {
        compose = true
    }""" + src[m.end():]
        changed.append("compose")

    # CMake برای naran.c
    if "jni/CMakeLists.txt" not in src:
        m = re.search(r"^android\s*\{", src, re.M)
        src = src[:m.end()] + """
    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
        }
    }""" + src[m.end():]
        changed.append("cmake")

GRADLE.write_text(src, encoding="utf-8")
print("اعمال شد: " + (", ".join(changed) if changed else "چیزی لازم نبود"))

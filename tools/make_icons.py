#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ساخت آیکون‌های اندروید از یک تصویر.

  python3 tools/make_icons.py [مسیر تصویر] [مسیر پروژه]

پیش‌فرض: branding/logo.* را برمی‌دارد و در res/mipmap-* می‌ریزد.
هر ابعادی بدهید کار می‌کند — مربع می‌شود، اندازه می‌گیرد، و برای آیکون
تطبیقی حاشیه‌ی امن می‌گذارد تا لانچرها گوشه‌اش را نبرند.
"""

import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    sys.exit("Pillow لازم است:  pip install Pillow")

BG = (0x0D, 0x12, 0x20, 255)          # شب سرمه‌ای ناران

# آیکون معمولی: نسبت اندازه‌ها در چگالی‌های مختلف
LEGACY = {
    "mipmap-mdpi": 48, "mipmap-hdpi": 72, "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144, "mipmap-xxxhdpi": 192,
}
# آیکون تطبیقی: بوم بزرگ‌تر است چون لانچر برشش می‌دهد
ADAPTIVE = {
    "mipmap-mdpi": 108, "mipmap-hdpi": 162, "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324, "mipmap-xxxhdpi": 432,
}

# در آیکون تطبیقی فقط ~۶۶٪ مرکز همیشه دیده می‌شود
SAFE_RATIO = 0.62
LEGACY_RATIO = 0.86


def load(path: Path) -> Image.Image:
    img = Image.open(path)
    img = img.convert("RGBA") if img.mode != "RGBA" else img
    # مربع کردن با برش از مرکز، بدون کشیدگی
    w, h = img.size
    if w != h:
        side = min(w, h)
        left, top = (w - side) // 2, (h - side) // 2
        img = img.crop((left, top, left + side, top + side))
    return img


def compose(logo: Image.Image, canvas: int, ratio: float,
            background: bool = True) -> Image.Image:
    inner = max(1, int(canvas * ratio))
    art = logo.resize((inner, inner), Image.LANCZOS)
    base = Image.new("RGBA", (canvas, canvas), BG if background else (0, 0, 0, 0))
    off = (canvas - inner) // 2
    base.paste(art, (off, off), art)
    return base


def round_corners(img: Image.Image, radius_ratio: float = 0.22) -> Image.Image:
    from PIL import ImageDraw
    size = img.size[0]
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, size - 1, size - 1], radius=int(size * radius_ratio), fill=255
    )
    out = img.copy()
    out.putalpha(mask)
    return out


def main():
    root = Path(__file__).resolve().parent.parent

    if len(sys.argv) > 1:
        src = Path(sys.argv[1])
    else:
        # PNG اول: شفافیت دارد و آیکون تطبیقی با آن تمیزتر درمی‌آید.
        found = [p for ext in ("png", "webp", "jpg", "jpeg")
                 for p in (root / "branding").glob(f"logo.{ext}")]
        if not found:
            sys.exit("تصویری در branding/ پیدا نشد. logo.jpg یا logo.png بگذارید.")
        src = found[0]

    if not src.is_file():
        sys.exit(f"فایل پیدا نشد: {src}")

    project = Path(sys.argv[2]) if len(sys.argv) > 2 else root
    res = project / "V2rayNG" / "app" / "src" / "main" / "res"
    if not res.is_dir():
        res = project / "app" / "src" / "main" / "res"
    if not res.is_dir():
        sys.exit(f"پوشه‌ی res پیدا نشد زیر {project}")

    logo = load(src)
    print(f"تصویر: {src.name}  ({logo.size[0]}×{logo.size[1]} بعد از مربع شدن)")

    made = 0
    for folder, size in LEGACY.items():
        out_dir = res / folder
        out_dir.mkdir(parents=True, exist_ok=True)

        square = compose(logo, size, LEGACY_RATIO)
        square.save(out_dir / "ic_launcher.png")
        round_corners(square, 0.5).save(out_dir / "ic_launcher_round.png")

        fg = compose(logo, ADAPTIVE[folder], SAFE_RATIO, background=False)
        fg.save(out_dir / "ic_launcher_foreground.png")
        made += 3

    # لایه‌ی پس‌زمینه و تعریف آیکون تطبیقی
    (res / "values").mkdir(parents=True, exist_ok=True)
    (res / "values" / "ic_launcher_background.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<resources>\n'
        f'    <color name="ic_launcher_background">#{BG[0]:02X}{BG[1]:02X}{BG[2]:02X}</color>\n'
        '</resources>\n', encoding="utf-8")

    adaptive = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_background" />\n'
        '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
        '    <monochrome android:drawable="@mipmap/ic_launcher_foreground" />\n'
        '</adaptive-icon>\n'
    )
    # فقط -v26 و بالاتر. اندروید قدیمی‌تر <adaptive-icon> را نمی‌شناسد و
    # اگر در mipmap-anydpi بدون پسوند بگذاریم، لینک منابع شکست می‌خورد.
    d = res / "mipmap-anydpi-v26"
    d.mkdir(parents=True, exist_ok=True)
    (d / "ic_launcher.xml").write_text(adaptive, encoding="utf-8")
    (d / "ic_launcher_round.xml").write_text(adaptive, encoding="utf-8")

    # اگر از اجرای قبلی مانده، پاکش کن
    stale_dir = res / "mipmap-anydpi"
    if stale_dir.is_dir():
        for f in stale_dir.glob("ic_launcher*.xml"):
            f.unlink()

    # آیکون قدیمی v2rayNG اگر مانده باشد، جای ما را می‌گیرد
    for stale in res.glob("mipmap-*/ic_launcher.webp"):
        stale.unlink()
    for stale in res.glob("mipmap-*/ic_launcher_round.webp"):
        stale.unlink()

    print(f"{made} آیکون ساخته شد در {res}")


if __name__ == "__main__":
    main()

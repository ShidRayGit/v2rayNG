#!/usr/bin/env python3
"""کلید API را به آرایه‌ی C تبدیل می‌کند تا در naran.c بگذارید.

    python3 tools/gen_secret.py <API_SECRET>
"""
import sys

MASK = [0x5A, 0xC3, 0x17, 0x8E, 0x2D, 0x71, 0xB4, 0x66]

if len(sys.argv) != 2:
    sys.exit("استفاده: gen_secret.py <API_SECRET>")

secret = sys.argv[1].encode()
masked = [b ^ MASK[i % len(MASK)] for i, b in enumerate(secret)]

print(f"/* {len(secret)} بایت */")
print("static const unsigned char SECRET_BYTES[] = {")
for i in range(0, len(masked), 8):
    row = ", ".join(f"0x{b:02X}" for b in masked[i:i + 8])
    print(f"    {row},")
print("};")

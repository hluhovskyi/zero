#!/usr/bin/env python3
"""
Generate Zero Play Store icon — 512×512px.

navy bg (#000E2F), cream "zero" text (Inter Black),
mint-green circle replacing the 'o'.
Antialiased via 4x supersampling + LANCZOS downscale.

Usage:
    python3 scripts/gen_icon.py <inter-black.ttf> <out.png>
"""
import sys
from PIL import Image, ImageDraw, ImageFont

FONT_PATH = sys.argv[1]
OUT_PATH  = sys.argv[2]

SCALE = 4
W, H  = 512, 512
BG    = (0,   14,  47)   # navy   #000E2F
TEXT  = (250, 248, 253)  # cream
DOT   = (130, 245, 193)  # mint-green

sw, sh = W * SCALE, H * SCALE
img  = Image.new("RGB", (sw, sh), BG)
draw = ImageDraw.Draw(img)

fs   = int(96 * SCALE)   # ~96px at final size
font = ImageFont.truetype(FONT_PATH, fs)

# Measure "zer"
bb     = font.getbbox("zer")
text_w = bb[2] - bb[0]
text_h = bb[3] - bb[1]

gap      = int(4  * SCALE)
margin   = int(6  * SCALE)   # circle bottom above text baseline
circle_d = int(52 / 96 * fs) # proportional to font size

total_w   = text_w + gap + circle_d
content_h = max(text_h, circle_d + margin)

cx = (sw - total_w)   // 2
cy = (sh - content_h) // 2

# "zer"
text_x = cx
text_y = cy + (content_h - text_h) - bb[1]
draw.text((text_x, text_y), "zer", font=font, fill=TEXT)

# circle (replaces 'o')
circle_x = cx + text_w + gap
circle_y = cy + content_h - circle_d - margin
draw.ellipse(
    [(circle_x, circle_y), (circle_x + circle_d, circle_y + circle_d)],
    fill=DOT,
)

out = img.resize((W, H), Image.LANCZOS)
out.save(OUT_PATH, "PNG")
print(f"Saved: {OUT_PATH}")

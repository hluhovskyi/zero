---
name: gen-release-assets
description: Use when regenerating Play Store assets — the 512×512 icon or the 1024×500 feature graphic. Invoked when the design changes or assets need to be recreated from scratch.
---

# Generate Release Assets

Regenerates Play Store graphics (icon + feature graphic) from PIL scripts using Inter Black.
Output goes to `~/Projects/zero-releases/`.

## Prerequisites

```bash
pip install pillow
# Inter font (if not already present)
curl -L https://github.com/rsms/inter/releases/download/v4.0/Inter-4.0.zip -o /tmp/inter.zip
unzip -q /tmp/inter.zip -d /tmp/inter
```

Font path used by both scripts: `/tmp/inter/extras/ttf/Inter-Black.ttf`

## Design tokens

| Token | Value | Role |
|-------|-------|------|
| BG    | `#000E2F` / `(0, 14, 47)`     | navy background |
| TEXT  | `(250, 248, 253)`              | cream "zer" text |
| DOT   | `(130, 245, 193)`              | mint-green circle (replaces 'o') |

## Generating the icon (512×512)

```bash
python3 scripts/gen_icon.py /tmp/inter/extras/ttf/Inter-Black.ttf ~/Projects/zero-releases/zero-icon-512.png
```

Layout: "zer" in Inter Black (~96px) + mint-green circle to the right, centered on the canvas.
Circle diameter is proportional to font size (`52/96` ratio). Both elements are bottom-aligned,
with the circle bottom sitting 6px above the text baseline.

## Generating the feature graphic (1024×500)

```bash
python3 scripts/gen_feature_graphic.py /tmp/inter/extras/ttf/Inter-Black.ttf ~/Projects/zero-releases/feature_graphic.png
```

Layout: same "zer●" composition scaled up (~220px text), centered on a wider canvas.
Circle diameter ratio: `120/220`. Circle bottom sits 10px above text baseline.

## Antialiasing

Both scripts render at 4× resolution then downscale with `Image.LANCZOS`. This avoids
jagged glyph edges that PIL produces at native resolution. Do not remove the SCALE=4 step.

## After regenerating

Upload the new files manually in Play Console:
- Icon → Store listing → App icon
- Feature graphic → Store listing → Feature graphic

The files are gitignored (they live in `~/Projects/zero-releases/`, outside the repo).

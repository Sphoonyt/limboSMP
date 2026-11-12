#!/usr/bin/env python3
"""
Generates the HUD art + font for the cooldown bar:  python3 tools/gen_hud.py
  assets/limbo/textures/hud/channel/<channel_id>.png   14 ability icons (your art from tools/hud_icons/), shown as drawn
  assets/limbo/textures/hud/flux_outline.png / flux_filled.png   totem silhouette, 32x32, white
  assets/limbo/textures/hud/text.png                    tiny fixed-width digits + "ready"
  assets/limbo/font/hud.json                            glyph table (keep in sync with HudGlyphs.java)
To replace an icon, just overwrite its PNG (keep it 32x32, white/grey on transparent: the plugin colours it).
"""
import json, os
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "resourcepack", "assets", "limbo")
TEX = os.path.join(ROOT, "textures", "hud")
os.makedirs(os.path.join(TEX, "channel"), exist_ok=True)
os.makedirs(os.path.join(ROOT, "font"), exist_ok=True)
W = (255, 255, 255, 255)

def canvas():
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)

def save(im, name): im.save(os.path.join(TEX, "channel", name + ".png"))

# ---------------------------------------------------------------- channel icons + flux indicator (your art, tools/hud_icons/)
# 14 channel icons: tools/hud_icons/<channel_id>.png, full colour, any square size (16x16 now). They are shown as drawn.
# flux.png is the hollow totem-ish outline; the filled version (interior flooded) is built from it so the plugin can colour it.
SRC = os.path.join(os.path.dirname(__file__), "hud_icons")
ICON_ORDER = ["enchain", "black_flash", "drought_compression", "open_furnace", "propel", "kinetic_thief", "imbalance_ring",
              "temporal_repose", "reality_shatter", "reflected_construct", "axiom_dagger", "black_hole", "evolution", "silencing_maw"]
ICONS = {}
for n in ICON_ORDER:
    ICONS[n] = Image.open(os.path.join(SRC, n + ".png")).convert("RGBA")
    ICONS[n].save(os.path.join(TEX, "channel", n + ".png"))

flux = Image.open(os.path.join(SRC, "flux.png")).convert("RGBA")
flux.save(os.path.join(TEX, "flux_outline.png"))          # shown as drawn (black outline), no tint
# interior = transparent pixels the outside cannot reach; fill them white (the plugin tints: darkness colour / black)
w, h = flux.size
solid = lambda x, y: flux.getpixel((x, y))[3] >= 128
seen, stack = set(), [(x, y) for x in range(w) for y in range(h) if (x in (0, w - 1) or y in (0, h - 1)) and not solid(x, y)]
while stack:
    x, y = stack.pop()
    if (x, y) in seen or not (0 <= x < w and 0 <= y < h) or solid(x, y): continue
    seen.add((x, y))
    stack += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
filled = flux.copy()
for x in range(w):
    for y in range(h):
        if not solid(x, y) and (x, y) not in seen: filled.putpixel((x, y), (255, 255, 255, 255))
filled.save(os.path.join(TEX, "flux_filled.png"))
print("flux interior pixels filled:", sum(1 for x in range(w) for y in range(h) if not solid(x, y) and (x, y) not in seen))

# ---------------------------------------------------------------- tiny fixed-width text: 0-9 s r e a d y
F = {
 "0": ["01110","10001","10011","10101","11001","10001","01110"], "1": ["00100","01100","00100","00100","00100","00100","01110"],
 "2": ["01110","10001","00001","00110","01000","10000","11111"], "3": ["11110","00001","00001","01110","00001","00001","11110"],
 "4": ["00010","00110","01010","10010","11111","00010","00010"], "5": ["11111","10000","11110","00001","00001","10001","01110"],
 "6": ["00110","01000","10000","11110","10001","10001","01110"], "7": ["11111","00001","00010","00100","01000","01000","01000"],
 "8": ["01110","10001","10001","01110","10001","10001","01110"], "9": ["01110","10001","10001","01111","00001","00010","01100"],
 "s": ["00000","01111","10000","01110","00001","11110","00000"], "r": ["00000","10110","11001","10000","10000","10000","00000"],
 "e": ["00000","01110","10001","11111","10000","01110","00000"], "a": ["00000","01110","00001","01111","10001","01111","00000"],
 "d": ["00001","00001","01101","10011","10001","10011","01101"], "y": ["00000","10001","10001","01111","00001","10001","01110"],
}
ORDER = "0123456789sready"   # -> U+E120 .. U+E12F
atlas = Image.new("RGBA", (8 * 16, 8), (0, 0, 0, 0))
for i, ch in enumerate(ORDER):
    for y, row in enumerate(F[ch]):
        for x, c in enumerate(row):
            if c == "1": atlas.putpixel((i * 8 + x, y), W)
    atlas.putpixel((i * 8 + 5, 7), (255, 255, 255, 1))   # invisible marker: every glyph is exactly 6 wide, so numbers never jitter
atlas.save(os.path.join(TEX, "text.png"))

# ---------------------------------------------------------------- font
providers = [{"type": "space", "advances": {"\uE130": 7, "\uE131": 6, "\uE132": 14}}]
for i, n in enumerate(ICONS):
    providers.append({"type": "bitmap", "file": f"limbo:hud/channel/{n}.png", "ascent": 12, "height": 16, "chars": [chr(0xE100 + i)]})
providers.append({"type": "bitmap", "file": "limbo:hud/flux_outline.png", "ascent": 12, "height": 16, "chars": ["\uE110"]})
providers.append({"type": "bitmap", "file": "limbo:hud/flux_filled.png", "ascent": 12, "height": 16, "chars": ["\uE111"]})
providers.append({"type": "bitmap", "file": "limbo:hud/text.png", "ascent": 7, "height": 8, "chars": [chr(0xE120 + i) for i in range(16)] and ["".join(chr(0xE120 + i) for i in range(16))]})
json.dump({"providers": providers}, open(os.path.join(ROOT, "font", "hud.json"), "w"), indent=2)
print("icon order:", ", ".join(f"U+{0xE100+i:04X} {n}" for i, n in enumerate(ICONS)))

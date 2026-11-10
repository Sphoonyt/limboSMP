#!/usr/bin/env python3
"""
Generates the HUD art + font for the cooldown bar:  python3 tools/gen_hud.py
  assets/limbo/textures/hud/channel/<channel_id>.png   14 temp ability icons, 32x32, WHITE (the plugin tints them)
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

# ---------------------------------------------------------------- 14 temporary channel icons (one per ability)
def enchain():
    im, d = canvas()
    d.rounded_rectangle([3, 9, 20, 22], 6, outline=W, width=3)
    d.rounded_rectangle([12, 10, 29, 23], 6, outline=W, width=3)
    return im
def black_flash():
    im, d = canvas()
    d.polygon([(19, 1), (7, 18), (15, 18), (11, 31), (26, 12), (17, 12), (22, 1)], fill=W)
    return im
def drought_compression():
    im, d = canvas()
    d.rectangle([11, 11, 20, 20], fill=W)
    for pts in ([(1, 16), (8, 11), (8, 21)], [(31, 16), (24, 11), (24, 21)], [(16, 1), (11, 8), (21, 8)], [(16, 31), (11, 24), (21, 24)]):
        d.polygon(pts, fill=W)
    return im
def open_furnace():
    im, d = canvas()
    d.polygon([(16, 1), (24, 12), (28, 20), (24, 28), (16, 31), (8, 28), (4, 20), (8, 13), (12, 17), (13, 9)], fill=W)
    d.polygon([(16, 14), (20, 21), (16, 27), (12, 21)], fill=(0, 0, 0, 0))
    return im
def propel():
    im, d = canvas()
    for x in (2, 14):
        d.polygon([(x, 4), (x + 12, 16), (x, 28), (x + 5, 16)], fill=W)
    d.rectangle([26, 14, 30, 18], fill=W)
    return im
def kinetic_thief():
    im, d = canvas()
    d.ellipse([12, 12, 20, 20], fill=W)
    for r in (8, 13):
        d.arc([16 - r, 16 - r, 16 + r, 16 + r], -50, 50, fill=W, width=3)
        d.arc([16 - r, 16 - r, 16 + r, 16 + r], 130, 230, fill=W, width=3)
    return im
def imbalance_ring():
    im, d = canvas()
    d.ellipse([2, 2, 29, 29], outline=W, width=3)
    d.rectangle([14, 8, 17, 23], fill=W)
    d.rectangle([8, 14, 23, 17], fill=W)
    return im
def temporal_repose():
    im, d = canvas()
    d.polygon([(6, 2), (26, 2), (26, 5), (18, 16), (26, 27), (26, 30), (6, 30), (6, 27), (14, 16), (6, 5)], outline=W, width=3)
    d.polygon([(11, 25), (21, 25), (16, 19)], fill=W)
    return im
def reality_shatter():
    im, d = canvas()
    d.ellipse([3, 3, 28, 28], outline=W, width=2)
    d.line([(6, 25), (14, 15), (11, 11), (22, 5)], fill=W, width=3)
    d.line([(14, 15), (24, 20), (27, 28)], fill=W, width=3)
    return im
def reflected_construct():
    im, d = canvas()
    d.polygon([(16, 2), (28, 16), (16, 30), (4, 16)], outline=W, width=3)
    d.rectangle([15, 2, 16, 30], fill=W)
    d.polygon([(16, 8), (23, 16), (16, 24)], fill=W)
    return im
def axiom_dagger():
    im, d = canvas()
    d.polygon([(26, 3), (29, 6), (13, 22), (10, 19)], fill=W)
    d.polygon([(7, 17), (15, 25), (12, 28), (4, 20)], fill=W)
    d.line([(8, 24), (3, 29)], fill=W, width=3)
    return im
def black_hole():
    im, d = canvas()
    d.ellipse([9, 9, 22, 22], fill=W)
    d.arc([1, 5, 30, 26], 200, 340, fill=W, width=3)
    d.arc([1, 5, 30, 26], 20, 160, fill=W, width=3)
    d.arc([4, 1, 27, 30], 290, 70, fill=W, width=2)
    return im
def evolution():
    im, d = canvas()
    for y in (3, 12, 21):
        d.polygon([(16, y), (28, y + 9), (23, y + 11), (16, y + 6), (9, y + 11), (4, y + 9)], fill=W)
    return im
def silencing_maw():
    im, d = canvas()
    for x in range(2, 30, 7):
        d.polygon([(x, 29), (x + 3, 8 + (x % 3) * 3), (x + 6, 29)], fill=W)
    d.rectangle([1, 27, 30, 30], fill=W)
    return im

ICONS = {f.__name__: f for f in (enchain, black_flash, drought_compression, open_furnace, propel, kinetic_thief,
         imbalance_ring, temporal_repose, reality_shatter, reflected_construct, axiom_dagger, black_hole, evolution, silencing_maw)}
for n, f in ICONS.items(): save(f(), n)

# ---------------------------------------------------------------- flux indicator: a totem silhouette
TOTEM = [(11, 2), (21, 2), (23, 8), (30, 10), (30, 16), (23, 17), (21, 30), (11, 30), (9, 17), (2, 16), (2, 10), (9, 8)]
for name, fill in (("flux_outline", False), ("flux_filled", True)):
    im, d = canvas()
    if fill: d.polygon(TOTEM, fill=W)
    else: d.polygon(TOTEM, outline=W, width=2)
    im.save(os.path.join(TEX, name + ".png"))

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
    providers.append({"type": "bitmap", "file": f"limbo:hud/channel/{n}.png", "ascent": 11, "height": 16, "chars": [chr(0xE100 + i)]})
providers.append({"type": "bitmap", "file": "limbo:hud/flux_outline.png", "ascent": 11, "height": 16, "chars": ["\uE110"]})
providers.append({"type": "bitmap", "file": "limbo:hud/flux_filled.png", "ascent": 11, "height": 16, "chars": ["\uE111"]})
providers.append({"type": "bitmap", "file": "limbo:hud/text.png", "ascent": 7, "height": 8, "chars": [chr(0xE120 + i) for i in range(16)] and ["".join(chr(0xE120 + i) for i in range(16))]})
json.dump({"providers": providers}, open(os.path.join(ROOT, "font", "hud.json"), "w"), indent=2)
print("icon order:", ", ".join(f"U+{0xE100+i:04X} {n}" for i, n in enumerate(ICONS)))

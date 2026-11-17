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
w, h = flux.size
solid = lambda x, y: flux.getpixel((x, y))[3] >= 128
# interior = transparent pixels the outside cannot reach
seen, stack = set(), [(x, y) for x in range(w) for y in range(h) if (x in (0, w - 1) or y in (0, h - 1)) and not solid(x, y)]
while stack:
    x, y = stack.pop()
    if (x, y) in seen or not (0 <= x < w and 0 <= y < h) or solid(x, y): continue
    seen.add((x, y))
    stack += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
interior = [(x, y) for x in range(w) for y in range(h) if not solid(x, y) and (x, y) not in seen]
BORDER = (86, 86, 86, 255)             # x the darkness colour = a dark border of that colour, like the icons' outlines
TONES = [255, 214, 172, 132]           # hard shading steps, light at the top left, like the icons
ys = [y for _, y in interior]; xs = [x for x, _ in interior]
def tone(x, y):
    t = ((x - min(xs)) / max(1, max(xs) - min(xs)) + (y - min(ys)) / max(1, max(ys) - min(ys))) / 2
    return TONES[min(3, int(t * 4))]
outline_img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
filled_img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
for x in range(w):
    for y in range(h):
        if solid(x, y):
            outline_img.putpixel((x, y), BORDER); filled_img.putpixel((x, y), BORDER)
for x, y in interior:
    v = tone(x, y); filled_img.putpixel((x, y), (v, v, v, 255))
outline_img.save(os.path.join(TEX, "flux_outline.png"))
filled_img.save(os.path.join(TEX, "flux_filled.png"))
print("flux interior pixels:", len(interior))

# ---------------------------------------------------------------- tiny fixed-width text: 0-9 s r e a d y
F = {
 "0": ["01110","10001","10011","10101","11001","10001","01110"], "1": ["00100","01100","00100","00100","00100","00100","01110"],
 "2": ["01110","10001","00001","00110","01000","10000","11111"], "3": ["11110","00001","00001","01110","00001","00001","11110"],
 "4": ["00010","00110","01010","10010","11111","00010","00010"], "5": ["11111","10000","11110","00001","00001","10001","01110"],
 "6": ["00110","01000","10000","11110","10001","10001","01110"], "7": ["11111","00001","00010","00100","01000","01000","01000"],
 "8": ["01110","10001","10001","01110","10001","10001","01110"], "9": ["01110","10001","10001","01111","00001","00010","01100"],
 # small caps (5 tall, sitting on the baseline)
 "s": ["00000","00000","01111","10000","01110","00001","11110"], "r": ["00000","00000","11110","10001","11110","10010","10001"],
 "e": ["00000","00000","11111","10000","11110","10000","11111"], "a": ["00000","00000","01110","10001","11111","10001","10001"],
 "d": ["00000","00000","11110","10001","10001","10001","11110"], "y": ["00000","00000","10001","01010","00100","00100","00100"],
 "v": ["00000","00001","00010","10100","01000","00000","00000"],   # check mark: a charge that is ready
}
ORDER = "0123456789sreadyv"   # -> U+E120 .. U+E12F, then v (the check mark) = U+E133
atlas = Image.new("RGBA", (8 * 17, 8), (0, 0, 0, 0))
for i, ch in enumerate(ORDER):
    for y, row in enumerate(F[ch]):
        for x, c in enumerate(row):
            if c == "1": atlas.putpixel((i * 8 + x, y), W)
    atlas.putpixel((i * 8 + 5, 7), (255, 255, 255, 1))   # invisible marker: every glyph is exactly 6 wide, so numbers never jitter
atlas.save(os.path.join(TEX, "text.png"))

# ---------------------------------------------------------------- font
providers = [{"type": "space", "advances": {"\uE130": 7, "\uE131": 3, "\uE132": 10}}]
for i, n in enumerate(ICONS):
    providers.append({"type": "bitmap", "file": f"limbo:hud/channel/{n}.png", "ascent": 12, "height": 16, "chars": [chr(0xE100 + i)]})
providers.append({"type": "bitmap", "file": "limbo:hud/flux_outline.png", "ascent": 12, "height": 16, "chars": ["\uE110"]})
providers.append({"type": "bitmap", "file": "limbo:hud/flux_filled.png", "ascent": 12, "height": 16, "chars": ["\uE111"]})
providers.append({"type": "bitmap", "file": "limbo:hud/text.png", "ascent": 8, "height": 8, "chars": ["".join(chr(0xE120 + i) for i in range(16)) + "\uE133"]})
json.dump({"providers": providers}, open(os.path.join(ROOT, "font", "hud.json"), "w"), indent=2)
print("icon order:", ", ".join(f"U+{0xE100+i:04X} {n}" for i, n in enumerate(ICONS)))

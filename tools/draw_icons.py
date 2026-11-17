#!/usr/bin/env python3
"""Redraws the three icons that were redone (16x16, same dark-outline + hard-shaded style and palettes as the rest):
drought_compression, open_furnace, silencing_maw  ->  tools/hud_icons/*.png.   python3 tools/draw_icons.py"""
import math, os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "hud_icons")
FIRE = [(231, 56, 1), (255, 81, 1), (255, 112, 47), (255, 139, 85), (255, 179, 144), (255, 220, 204)]
FIRE_OUT = (94, 29, 0)
GREEN = [(16, 60, 0), (31, 117, 0), (49, 186, 0), (117, 255, 67)]
GREEN_OUT = (16, 60, 0)

def blank(): return [[None] * 16 for _ in range(16)]
def put(g, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16: g[y][x] = c
def outline(g, col):
    mask = [(x, y) for y in range(16) for x in range(16) if g[y][x] is None]
    for x, y in mask:
        if any(0 <= x + dx < 16 and 0 <= y + dy < 16 and g[y + dy][x + dx] is not None and g[y + dy][x + dx] != col
               for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            g[y][x] = col
def save(g, name):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if g[y][x] is not None: im.putpixel((x, y), g[y][x] + (255,))
    im.save(os.path.join(OUT, name + ".png"))

def tri(g, pts, ramp, base_idx, hi_side=1):
    """Filled triangle shaded by distance from its lit edge (the left/top)."""
    im = Image.new("L", (16, 16), 0); ImageDraw.Draw(im).polygon(pts, fill=255)
    xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
    for y in range(16):
        for x in range(16):
            if im.getpixel((x, y)):
                t = ((x - min(xs)) / max(1, max(xs) - min(xs)) + (y - min(ys)) / max(1, max(ys) - min(ys))) / 2
                put(g, x, y, ramp[max(0, min(len(ramp) - 1, base_idx + (1 if t < 0.35 else 0) - (1 if t > 0.7 else 0)))])

# ---- drought compression: an ember orb being squeezed from all four sides
g = blank()
cx, cy, r = 7.5, 7.5, 3.6
for y in range(16):
    for x in range(16):
        d = math.hypot(x - cx, y - cy)
        if d <= r:
            lit = math.hypot(x - (cx - 1.4), y - (cy - 1.4)) / (r * 1.5)
            put(g, x, y, FIRE[max(0, min(5, int((1 - lit) * 5.4)))])
for pts in ([(5, 0), (10, 0), (7.5, 3)], [(5, 15), (10, 15), (7.5, 12)], [(0, 5), (0, 10), (3, 7.5)], [(15, 5), (15, 10), (12, 7.5)]):
    tri(g, pts, FIRE, 2)
outline(g, FIRE_OUT); save(g, "drought_compression")

# ---- open furnace: a flaming arrow flying up and to the right
g = blank()
im = Image.new("L", (16, 16), 0); d = ImageDraw.Draw(im)
d.polygon([(15, 0), (15, 7), (12, 4.5), (8, 1)], fill=255)              # arrow head (up-right)
head = [(x, y) for y in range(16) for x in range(16) if im.getpixel((x, y))]
for x, y in head:
    t = (x - y + 8) / 20.0
    put(g, x, y, FIRE[max(1, min(3, int(t * 4)))])
for i in range(0, 10):                                                              # shaft, bottom-left to the head
    x, y = 3 + i * 0.85, 12 - i * 0.85
    for dx, dy in ((0, 0), (1, 0), (0, -1)):
        put(g, round(x) + dx, round(y) + dy, FIRE[2 if i % 2 == 0 else 1])
for (x, y, c) in ((1, 14, 1), (2, 14, 2), (1, 13, 1), (2, 13, 3), (3, 14, 1), (0, 15, 0), (1, 15, 0), (2, 15, 1), (3, 13, 2), (2, 12, 2), (4, 14, 0)):  # flame tail
    put(g, x, y, FIRE[c])
put(g, 13, 2, FIRE[5]); put(g, 12, 3, FIRE[4]); put(g, 14, 1, FIRE[4])
outline(g, FIRE_OUT); save(g, "open_furnace")

# ---- silencing maw: three spikes bursting out of the ground
g = blank()
for (bx, tip, w) in ((2, 5, 4), (6, 1, 5), (11, 4, 4)):
    pts = [(bx, 13), (bx + w, 13), (bx + w / 2, tip)]
    im = Image.new("L", (16, 16), 0); ImageDraw.Draw(im).polygon(pts, fill=255)
    for y in range(16):
        for x in range(16):
            if im.getpixel((x, y)):
                left = (x - bx) / max(1.0, w)
                put(g, x, y, GREEN[3 if left < 0.25 else 2 if left < 0.55 else 1])
for x in range(0, 16):                                                              # broken ground
    put(g, x, 13, GREEN[1]); put(g, x, 14, GREEN[0] if x % 3 else GREEN[1])
for x in (3, 7, 12): put(g, x, 12, GREEN[0])
outline(g, GREEN_OUT); save(g, "silencing_maw")
print("drew 3 icons")

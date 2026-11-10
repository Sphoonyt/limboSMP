#!/usr/bin/env python3
"""
Generates assets/limbo/post_effect/<name>_<level>.json for every status overlay.

Each overlay has 3 strengths so the plugin can fade them in and out in steps with /posteffect (a post effect's
uniforms are fixed in its JSON, there is no way to animate them from the server):
    <name>_0 = full, <name>_1 = ~60%, <name>_2 = ~30%
Re-run after changing a look:  python3 tools/gen_post_effects.py
Keep the names in sync with ScreenFx.Fx in the plugin.
"""
import json, os

OUT = os.path.join(os.path.dirname(__file__), "..", "resourcepack", "assets", "limbo", "post_effect")
LEVELS = [1.0, 0.6, 0.3]

def hexrgb(h):
    h = h.lstrip("#")
    return [int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)]

# name: (colour, edge strength, clear radius, full radius, desaturate, rim darkening, whole-screen tint, fringing)
# (stronger than the first pass: the rim now starts closer to the centre and is more opaque)
LOOKS = {
    # victims
    "enchain":       ("#961428", 0.62, 0.40, 0.95, 0.25, 0.18, 0.05, 0.000),
    "silence":       ("#46be5a", 0.55, 0.38, 0.92, 0.45, 0.14, 0.08, 0.000),
    "repose":        ("#ffafcd", 0.52, 0.38, 0.92, 0.35, 0.00, 0.10, 0.005),
    "axiom":         ("#9646e6", 0.58, 0.40, 0.92, 0.22, 0.20, 0.04, 0.000),
    "black_hole":    ("#05000c", 0.80, 0.25, 0.85, 0.45, 0.35, 0.00, 0.000),
    "kinetic_stun":  ("#40e0d0", 0.60, 0.32, 0.90, 0.22, 0.10, 0.04, 0.004),
    "kinetic_slow":  ("#40e0d0", 0.48, 0.55, 1.05, 0.05, 0.00, 0.00, 0.000),   # same look, covers far less
    "spinecutter":   ("#961428", 0.58, 0.38, 0.92, 0.20, 0.16, 0.03, 0.000),
    # buffs on yourself
    "evolution":     ("#46be5a", 0.42, 0.52, 1.05, 0.00, 0.00, 0.05, 0.000),
    "black_flash":   ("#c81020", 0.50, 0.50, 1.05, 0.00, 0.20, 0.00, 0.000),
    "construct":     ("#a5cdff", 0.40, 0.52, 1.05, 0.00, 0.00, 0.07, 0.004),
}
# fatigue is its own shader: a soft black fade along the BOTTOM of the screen (assets/limbo/shaders/post/bottom_dark.fsh)
BOTTOM = (0.20, 0.78, 0.0)  # height covered, blackness at the very bottom at full strength, flip (set to 1.0 if it shows on top)

def cracks(k):  # (name kept) the fatigue overlay
    height, strength, flip = BOTTOM
    return {
        "targets": {"swap": {}},
        "passes": [
            {"vertex_shader": "minecraft:core/screenquad", "fragment_shader": "limbo:post/bottom_dark",
             "inputs": [{"sampler_name": "In", "target": "minecraft:main"}], "output": "swap",
             "uniforms": {"BottomConfig": [
                 {"name": "Params", "type": "vec4", "value": [height, round(strength * k, 4), flip, 0.0]}]}},
            {"vertex_shader": "minecraft:core/screenquad", "fragment_shader": "minecraft:post/blit",
             "inputs": [{"sampler_name": "In", "target": "swap"}], "output": "minecraft:main",
             "uniforms": {"BlitConfig": [{"name": "ColorModulate", "type": "vec4", "value": [1.0, 1.0, 1.0, 1.0]}]}},
        ],
    }

def effect(look, k):
    col, edge, clear, full, desat, dark, tint, fringe = look
    r, g, b = hexrgb(col)
    def pass_(frag, inp, out, uniforms):
        p = {"vertex_shader": "minecraft:core/screenquad", "fragment_shader": frag,
             "inputs": [{"sampler_name": "In", "target": inp}], "output": out}
        if uniforms: p["uniforms"] = uniforms
        return p
    return {
        "targets": {"swap": {}},
        "passes": [
            pass_("limbo:post/screen_tint", "minecraft:main", "swap", {"TintConfig": [
                {"name": "EdgeColor", "type": "vec4", "value": [round(r, 4), round(g, 4), round(b, 4), round(edge * k, 4)]},
                {"name": "Shape", "type": "vec4", "value": [clear, full, round(desat * k, 4), round(dark * k, 4)]},
                {"name": "Misc", "type": "vec4", "value": [round(tint * k, 4), round(fringe * k, 5), 0.0, 0.0]},
            ]}),
            pass_("minecraft:post/blit", "swap", "minecraft:main", {"BlitConfig": [
                {"name": "ColorModulate", "type": "vec4", "value": [1.0, 1.0, 1.0, 1.0]}]}),
        ],
    }

os.makedirs(OUT, exist_ok=True)
for name, look in LOOKS.items():
    for lvl, k in enumerate(LEVELS):
        with open(os.path.join(OUT, f"{name}_{lvl}.json"), "w") as f:
            json.dump(effect(look, k), f, indent=2)
            f.write("\n")
for lvl, k in enumerate(LEVELS):
    with open(os.path.join(OUT, f"fatigue_{lvl}.json"), "w") as f:
        json.dump(cracks(k), f, indent=2)
        f.write("\n")
print("wrote", (len(LOOKS) + 1) * len(LEVELS), "post effects to", os.path.normpath(OUT))

#!/usr/bin/env python3
"""In-game animated textures for NeroPower: PULSE breathing on the machine fronts and the fission
BER glow, plus a SCROLL travel on the beam ray (port of NeroTech's tools/gen_animations.py, with
the hue window moved from teal to the amber POWER ramp).

Output goes into textures/block and is picked up by the atlas via a sibling .png.mcmeta (no model
or Java change). PULSE turns a static 32x32 texture into a vertical frame strip whose accent
pixels breathe in brightness (HSV value, keeps hue -> no white-out). The accent mask is restricted
to the amber hue window, so ONLY the P_* emissives painted by gen_textures.py pulse — alloy,
greys, the T_CYAN tell, the hazard stripes and the battery charge windows stay static. The
fission alarm face widens the window down to red so R_ALARM strobes too. SCROLL shifts the
(y-tileable) base texture down by 32/frames px per frame so the beam appears to travel.

Frame 0 of every strip is the untouched original, so the pass is IDEMPOTENT: re-running crops
frame 0 back out and rebuilds the strip (resting look unchanged). Frametime 3 across the set.

Usage: python tools/gen_animations.py      (run AFTER gen_textures.py; --force is accepted and
ignored, the pass always rebuilds from frame 0)
Deps: Pillow (exits 0 with a notice without it, mirroring gen_textures.py).
"""
import colorsys
import json
import math
import os
import sys

try:
    from PIL import Image
except ModuleNotFoundError:
    print("gen_animations: Pillow not installed; skipping animation generation (pip install pillow).")
    sys.exit(0)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCK = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "neropower",
                     "textures", "block")

# Amber hue window: P_EMBER ~0.05, P_AMBER ~0.085, P_HOT ~0.093, P_GLOW ~0.116. HAZ_Y sits at
# ~0.136 and T_CYAN at ~0.51, so neither ever pulses.
AMBER = (0.03, 0.125)
# The alarm face: the same window widened down through red so R_ALARM (~0.015) strobes.
ALARM = (0.0, 0.125)

# name, frames, frametime(ticks), amplitude, hue-window
PULSE = [
    ("fission_core_front",             8, 3, 0.32, AMBER),
    ("fission_core_front_alarm",       8, 3, 0.40, ALARM),
    ("fission_core_glow",              8, 3, 0.35, AMBER),  # BER wisp: glow breathing
    ("battery_bank_controller_front",  8, 3, 0.26, AMBER),
    ("beam_transmitter_linked_front",  8, 3, 0.30, AMBER),
    ("beam_relay_linked_front",        8, 3, 0.30, AMBER),
    ("radioisotope_generator_front",   8, 3, 0.28, AMBER),
    ("stirling_generator_front",       8, 3, 0.30, AMBER),
]

# name, frames, frametime(ticks) — the base is painted y-tileable by gen_textures.py
SCROLL = [
    ("beam_ray", 8, 3),
]


def write_mcmeta(path, frametime, interpolate=False):
    anim = {"frametime": frametime}
    if interpolate:
        anim["interpolate"] = True
    with open(path + ".mcmeta", "w", encoding="utf-8") as fh:
        json.dump({"animation": anim}, fh, indent=2)
        fh.write("\n")


def accent_mask(img, hue):
    """Saturated + bright pixels (vs the texture's median value), optionally hue-windowed."""
    px = img.load()
    w, h = img.size
    vals = [max(px[x, y][:3]) / 255 for y in range(h) for x in range(w) if px[x, y][3]]
    med = sorted(vals)[len(vals) // 2] if vals else .5
    m = []
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if not a:
                continue
            hh, ss, vv = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if ss > 0.32 and vv > max(0.42, med * 1.05) and (hue is None or hue[0] <= hh <= hue[1]):
                m.append((x, y))
    return m


def litpx(px, xy, f):
    r, g, b, a = px[xy]
    hh, ss, vv = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    vv = min(1.0, vv * f)
    ss = min(1.0, ss * (1.0 + 0.12 * (f - 1)))
    r, g, b = (int(c * 255) for c in colorsys.hsv_to_rgb(hh, ss, vv))
    px[xy] = (r, g, b, a)


def pulse_strip(base, frames, amp, hue):
    w = base.width
    mask = accent_mask(base, hue)
    strip = Image.new("RGBA", (w, w * frames))
    for i in range(frames):
        fr = base.copy()
        px = fr.load()
        fac = 1.0 + amp * math.sin(2 * math.pi * i / frames)
        if abs(fac - 1.0) > 1e-9:  # fac==1.0 frames (frame 0 + mid-cycle) stay EXACTLY the base:
            for xy in mask:        # even a no-op litpx would drift pixels via HSV int-truncation,
                litpx(px, xy, fac)  # which would break the crop-frame-0 idempotency guarantee.
        strip.paste(fr, (0, i * w))
    return strip


def scroll_strip(base, frames):
    """Frame i is the base rolled DOWN by i * (w / frames) px (frame 0 untouched)."""
    w = base.width
    strip = Image.new("RGBA", (w, w * frames))
    for i in range(frames):
        dy = (i * w) // frames
        fr = Image.new("RGBA", (w, w))
        fr.paste(base.crop((0, 0, w, w - dy)), (0, dy))
        if dy:
            fr.paste(base.crop((0, w - dy, w, w)), (0, 0))
        strip.paste(fr, (0, i * w))
    return strip


def main():
    made = []
    for name, frames, ft, amp, hue in PULSE:
        p = os.path.join(BLOCK, name + ".png")
        if not os.path.exists(p):
            print("  miss", name)
            continue
        base = Image.open(p).convert("RGBA")
        base = base.crop((0, 0, base.width, base.width))  # frame 0 of any previous strip
        pulse_strip(base, frames, amp, hue).save(p)
        write_mcmeta(p, ft)
        made.append("%s (pulse %df @%d)" % (name, frames, ft))
    for name, frames, ft in SCROLL:
        p = os.path.join(BLOCK, name + ".png")
        if not os.path.exists(p):
            print("  miss", name)
            continue
        base = Image.open(p).convert("RGBA")
        base = base.crop((0, 0, base.width, base.width))
        scroll_strip(base, frames).save(p)
        write_mcmeta(p, ft)
        made.append("%s (scroll %df @%d)" % (name, frames, ft))
    print("animated %d textures:" % len(made))
    for m in made:
        print("  ", m)


if __name__ == "__main__":
    main()

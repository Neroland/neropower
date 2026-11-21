#!/usr/bin/env python3
"""In-game animated textures for NeroPower: PULSE breathing on machine fronts and renderer glows,
plus a SCROLL travel for art that moves along itself (the beam ray).

Output is picked up by the atlas through a sibling .png.mcmeta (no model or Java change). PULSE
turns a static 32x32 texture into a vertical frame strip whose accent pixels breathe in
brightness (HSV value, hue kept, so nothing whites out). The accent mask is restricted to hue
windows, so only emissive light moves:

* the AMBER window (0.03..0.125) catches the P_* power ramp;
* the VIOLET window (0.68..0.75) catches the arc-violet ramp of superconducting-tier fields;
* the ALARM window widens amber down through red so the fission alarm face strobes.

Alloy, greys, the cyan tell, hazard yellow, rad lime, trip white and red, heavy-water blue and the
charge colours sit outside both windows and never breathe. Cindersteel sits inside the amber
window but is a material, so the kit's STATIC_COLOURS are masked out by exact colour.

SCROLL shifts a y-tileable base down by 32/frames px per frame.

Each family module in tools/painters/ lists its own textures in PULSE / SCROLL tables; this script
collects them. Frame 0 of every strip is the untouched original, so the pass is IDEMPOTENT:
re-running crops frame 0 back out and rebuilds the strip. A strip whose pixels would not change
is not rewritten.

Usage: python tools/gen_animations.py [--out DIR] [--family NAME]   (run AFTER gen_textures.py;
--force is accepted and ignored, the pass always rebuilds from frame 0)
Deps: Pillow (exits 0 with a notice without it, mirroring gen_textures.py).
"""
import colorsys
import math
import os
import sys

try:
    from PIL import Image
except ModuleNotFoundError:
    print("gen_animations: Pillow not installed; skipping animation generation (pip install pillow).")
    sys.exit(0)

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import painters  # noqa: E402
from painters import kit  # noqa: E402


def _windows(hue):
    """None, one (low, high) pair or several pairs -> a list of pairs (None = any hue)."""
    if hue is None:
        return None
    if isinstance(hue[0], (int, float)):
        return [tuple(hue)]
    return [tuple(w) for w in hue]


def accent_mask(img, hue):
    """Saturated + bright pixels (vs the texture's median value), optionally hue-windowed."""
    px = img.load()
    w, h = img.size
    windows = _windows(hue)
    vals = [max(px[x, y][:3]) / 255 for y in range(h) for x in range(w) if px[x, y][3]]
    med = sorted(vals)[len(vals) // 2] if vals else .5
    m = []
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if not a or (r, g, b) in kit.STATIC_COLOURS:
                continue
            hh, ss, vv = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if ss > 0.32 and vv > max(0.42, med * 1.05) and (
                    windows is None or any(lo <= hh <= hi for lo, hi in windows)):
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


def tables(families=None):
    """(PULSE rows, SCROLL rows) from every family module (or just the named ones)."""
    pulse, scroll = [], []
    for name in (families or painters.available()):
        module = painters.load(name)
        pulse.extend(getattr(module, "PULSE", ()))
        scroll.extend(getattr(module, "SCROLL", ()))
    return pulse, scroll


def _frame0(path):
    with Image.open(path) as img:
        base = img.convert("RGBA")
    return base.crop((0, 0, base.width, base.width))  # frame 0 of any previous strip


def _put(strip, path, frametime):
    if not kit.same_pixels(strip, path):
        strip.save(path)
    kit.write_mcmeta(path, frametime)


def run(families=None, quiet=False):
    """Build every strip into the kit's active assets folder. Names may carry a folder prefix
    (``item:hot_thing``); the default folder is ``block``."""
    pulse, scroll = tables(families)
    made = []

    def target(name):
        folder, _, leaf = name.rpartition(":")
        return os.path.join(kit.texture_dir(folder or "block"), leaf + ".png")

    for name, frames, ft, amp, hue in pulse:
        p = target(name)
        if not os.path.exists(p):
            if not quiet:
                print("  miss", name)
            continue
        _put(pulse_strip(_frame0(p), frames, amp, hue), p, ft)
        made.append("%s (pulse %df @%d)" % (name, frames, ft))
    for name, frames, ft in scroll:
        p = target(name)
        if not os.path.exists(p):
            if not quiet:
                print("  miss", name)
            continue
        _put(scroll_strip(_frame0(p), frames), p, ft)
        made.append("%s (scroll %df @%d)" % (name, frames, ft))
    if not quiet:
        print("animated %d textures:" % len(made))
        for m in made:
            print("  ", m)
    return made


def main():
    families = []
    for i, a in enumerate(sys.argv):
        if a == "--out" and i + 1 < len(sys.argv):
            kit.configure(assets=sys.argv[i + 1])
        if a == "--family" and i + 1 < len(sys.argv):
            families.append(sys.argv[i + 1])
    run(families or None)


if __name__ == "__main__":
    main()

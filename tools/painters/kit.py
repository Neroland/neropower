"""The NeroPower painter kit: palette, output handling and the painters every family shares.

Family modules (``tools/painters/<family>.py``) do ``from .kit import *`` and paint with these.
Nothing here knows about a particular block. Three rules hold for everything built on the kit:

* Deterministic: seed every random choice from the texture name (``rng_for(name)``).
* 32x32 for blocks and items; animated strips are stacked 32x32 frames (``save_strip``).
* Colours come from the palette below. Emissive light is the amber POWER ramp or, for
  superconducting-tier fields only, the ARC VIOLET ramp; everything else stays static.

Output goes to the mod's assets folder unless ``configure(assets=...)`` points it elsewhere (the
drivers pass ``--out``). ``save`` is additive (an existing PNG is kept) unless force is on, and even
then a file whose pixels would not change is left untouched, so a regeneration never churns art.
"""
import colorsys
import hashlib
import json
import math
import os
import random

from PIL import Image

TOOLS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.dirname(TOOLS)
DEFAULT_ASSETS = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "neropower")

S = 32  # texture size (NeroTech's 32x recipes, 2px bevels)

# ---- Palette (RGBA) — documented in tools/README-textures.md; keep the two in lockstep ----
CLEAR = (0, 0, 0, 0)
A_DARK = (24, 30, 36, 255)                      # alloy shadow          (== NeroTech)
ALLOY = [(40, 50, 58, 255), (48, 60, 68, 255),  # alloy base ramp       (== NeroTech)
         (34, 42, 50, 255), (58, 72, 82, 255)]
A_LIGHT = (132, 156, 172, 255)                  # alloy highlight       (== NeroTech)
P_EMBER = (64, 24, 6, 255)                      # power deep ember      (replaces T_DEEP)
P_AMBER = (204, 112, 18, 255)                   # power amber           (replaces T_TEAL)
P_HOT = (255, 160, 40, 255)                     # power hot orange      (replaces T_CYAN/T_PLASMA)
P_GLOW = (255, 226, 160, 255)                   # power white-gold peak (replaces T_GLOW)
T_CYAN = (36, 208, 222, 255)                    # NeroTech's cyan — the ONE family tell per block
R_ALARM = (255, 56, 36, 255)                    # fission alarm red (alarm face only, pulses)
HAZ_Y = (255, 214, 30, 255)                     # hazard stripe — hue sits ABOVE the pulse window
HAZ_K = (24, 24, 30, 255)
CH_GREEN = (72, 214, 96, 255)                   # battery charge window: full
CH_AMBER = (255, 160, 40, 255)                  # battery charge window: mid
CH_RED = (236, 64, 40, 255)                     # battery charge window: low
CH_OFF = (14, 18, 22, 255)                      # battery charge window: unlit segment
POWER_RAMP = [P_EMBER, P_AMBER, P_HOT, P_GLOW]

# Metal hues for the item art.
SILVER = [(150, 154, 162, 255), (118, 122, 130, 255), (180, 184, 192, 255)]
URANIUM = [(106, 128, 92, 255), (78, 96, 70, 255), (150, 172, 128, 255)]
REPROC = [(148, 160, 140, 255), (116, 128, 110, 255), (188, 198, 176, 255)]
SPENT = [(70, 66, 62, 255), (52, 48, 46, 255), (96, 90, 84, 255)]

# ---- 0.4.0 additions ----
# Arc violet: superconducting-tier FIELDS only, at most 8 % of a face, never on a teal material.
V_DEEP = (28, 18, 64, 255)
V_ARC = (91, 63, 214, 255)
V_LIGHT = (155, 123, 255, 255)
V_GLOW = (224, 214, 255, 255)
VIOLET_RAMP = [V_DEEP, V_ARC, V_LIGHT, V_GLOW]
# Fluids and their marks.
STEAM_W = (232, 236, 239, 255)                  # steam white
STEAM_X = (185, 194, 200, 255)                  # exhaust grey
HW_BLUE = (46, 107, 170, 255)                   # heavy water
HW_SHADE = (28, 68, 116, 255)
HW_LIGHT = (111, 163, 216, 255)
# Contamination ONLY (Geiger HUD, contaminated-chunk particles, trace marks on cask and hot rods).
RAD_LIME = (182, 242, 58, 255)
RAD_DEEP = (44, 63, 10, 255)
# The mechanical "tripped" flag and nothing else: white on red.
TRIP_W = (242, 244, 246, 255)
TRIP_R = R_ALARM
H_CRIT = (255, 84, 56, 255)                     # overheat — renderer-only, never baked on a face
PLASMA = (130, 248, 255, 255)                   # NeroTech's light: BANNED on NeroPower art
# Materials sampled from NeroTech's painter: [base, shade, light].
CINDER = [(112, 70, 64, 255), (78, 46, 44, 255), (168, 112, 96, 255)]
SUPER = [(96, 220, 214, 255), (40, 150, 150, 255), (190, 255, 250, 255)]
GRAPHITE = [(23, 24, 27, 255), (34, 36, 42, 255), (52, 55, 63, 255)]
SHIELD = [(142, 148, 154, 255), (167, 173, 178, 255), (108, 114, 120, 255)]
CHARGE_RAMP = [CH_GREEN, CH_AMBER, CH_RED, CH_OFF]
SOCKET = (12, 16, 20, 255)                      # sunken panel floor
SOCKET_SHADOW = (6, 9, 12, 255)
# Tier trim: tiers differ by SHAPE count first (1 / 2 / 3), then by this colour.
TIER_TRIM = [A_LIGHT, CINDER[0], V_ARC]

# Hue windows (HSV hue 0..1) the PULSE animator breathes. Anything outside both never moves:
# lime, red, white, hazard yellow, cyan, heavy-water blue and the charge colours.
HUE_AMBER = (0.03, 0.125)
HUE_ALARM = (0.0, 0.125)                        # amber widened down through red (alarm faces)
HUE_VIOLET = (0.68, 0.75)
HUE_POWER_AND_ARC = (HUE_AMBER, HUE_VIOLET)     # a face carrying both lights
# Exact colours that sit inside a hue window but are materials, not light: never pulsed.
STATIC_COLOURS = frozenset(c[:3] for c in CINDER)

# Coverage ledger: every save() records its name so the driver can flag any PNG no painter owns.
PAINTED = {"block": set(), "item": set(), "particle": set()}

_out = {"assets": DEFAULT_ASSETS, "force": False, "quiet": False}


# ---------------- output ----------------

def configure(assets=None, force=None, quiet=None):
    """Point the kit at another assets folder (``--out``), or switch force / quiet."""
    if assets is not None:
        _out["assets"] = os.path.abspath(assets)
    if force is not None:
        _out["force"] = bool(force)
    if quiet is not None:
        _out["quiet"] = bool(quiet)


def assets_dir():
    return _out["assets"]


def texture_dir(folder):
    """``textures/<folder>`` under the active assets folder (``block``, ``item`` or ``particle``)."""
    return os.path.join(_out["assets"], "textures", folder)


def _say(*parts):
    if not _out["quiet"]:
        print(*parts)


def _rel(path):
    try:
        return os.path.relpath(path, ROOT)
    except ValueError:
        return path


def same_pixels(img, path):
    """True when the PNG at ``path`` already holds exactly these pixels."""
    try:
        with Image.open(path) as old:
            return old.size == img.size and old.convert("RGBA").tobytes() == img.convert("RGBA").tobytes()
    except (OSError, ValueError):
        return False


def _frame0_matches(img, path):
    """True when ``path`` is an animated strip whose first frame is ``img`` (the animator owns the rest)."""
    try:
        with Image.open(path) as old:
            w, h = old.size
            if w != img.width or h <= w or h % w:
                return False
            first = old.convert("RGBA").crop((0, 0, w, w))
            return first.tobytes() == img.convert("RGBA").tobytes()
    except (OSError, ValueError):
        return False


def save(img, folder, name):
    # ADDITIVE-ONLY: never clobber an existing asset (pass --force to override).
    d = texture_dir(folder)
    PAINTED.setdefault(folder, set()).add(name + ".png")
    path = os.path.join(d, name + ".png")
    if os.path.exists(path):
        if not _out["force"]:
            _say("skip (exists)", _rel(path))
            return
        if same_pixels(img, path) or _frame0_matches(img, path):
            _say("same", _rel(path))
            return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    _say("wrote", _rel(path))


def write_mcmeta(path, frametime, interpolate=False):
    """``<png>.mcmeta`` beside ``path``. CRLF like every other text file in the repo."""
    anim = {"frametime": frametime}
    if interpolate:
        anim["interpolate"] = True
    text = json.dumps({"animation": anim}, indent=2) + "\n"
    data = text.replace("\n", "\r\n").encode("utf-8")
    target = path + ".mcmeta"
    try:
        with open(target, "rb") as fh:
            if fh.read() == data:
                return
    except OSError:
        pass
    with open(target, "wb") as fh:
        fh.write(data)


def save_strip(frames, folder, name, frametime=3, interpolate=False):
    """Stack equal-sized frames into a vertical strip and write it with its ``.mcmeta``.

    For art that is animated by its painter (fluids, the hot rod item). Art that only breathes
    should be saved as one frame and listed in the module's ``PULSE`` table instead.
    """
    w, h = frames[0].size
    strip = Image.new("RGBA", (w, h * len(frames)), CLEAR)
    for i, fr in enumerate(frames):
        strip.paste(fr, (0, i * h))
    d = texture_dir(folder)
    PAINTED.setdefault(folder, set()).add(name + ".png")
    path = os.path.join(d, name + ".png")
    if os.path.exists(path) and not _out["force"]:
        _say("skip (exists)", _rel(path))
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if os.path.exists(path) and same_pixels(strip, path):
        _say("same", _rel(path))
    else:
        strip.save(path)
        _say("wrote", _rel(path))
    write_mcmeta(path, frametime, interpolate)


# ---------------- helpers ----------------

def rng_for(name):
    """Deterministic per-name seed — stable across runs and machines."""
    return random.Random(int(hashlib.md5(name.encode()).hexdigest(), 16) & 0xffffffff)


def new_img():
    return Image.new("RGBA", (S, S), CLEAR)


def _mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


mix = _mix


def noise_fill(img, palette, rng):
    px = img.load()
    for y in range(S):
        for x in range(S):
            px[x, y] = rng.choice(palette)
    return img


def bevel(img, light=A_LIGHT, dark=A_DARK):
    """2px bevel: highlight top/left, shadow bottom/right."""
    px = img.load()
    light2 = _mix(light, ALLOY[0], 0.45)
    dark2 = _mix(dark, ALLOY[0], 0.35)
    for i in range(S):
        px[i, 0] = light
        px[0, i] = light
        px[i, S - 1] = dark
        px[S - 1, i] = dark
    for i in range(1, S - 1):
        px[i, 1] = light2
        px[1, i] = light2
        px[i, S - 2] = dark2
        px[S - 2, i] = dark2


def rivets(img, pts=((4, 4), (27, 4), (4, 27), (27, 27))):
    """2x2 corner rivets."""
    px = img.load()
    half = _mix(A_LIGHT, A_DARK, 0.45)
    for (rx, ry) in pts:
        px[rx, ry] = A_LIGHT
        px[rx + 1, ry] = half
        px[rx, ry + 1] = half
        px[rx + 1, ry + 1] = A_DARK


def machine_base(name):
    """The shared machine-face recipe: alloy noise + 2px bevel + corner rivets."""
    img = new_img()
    noise_fill(img, ALLOY, rng_for(name))
    bevel(img)
    rivets(img)
    return img


def recess(px, x0, y0, x1, y1, fill=(12, 16, 20, 255)):
    """Sunken dark panel: fill + inner shadow (top/left) + catch-light on the lower lip."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = fill
    for x in range(x0, x1 + 1):
        px[x, y0] = (6, 9, 12, 255)
    for y in range(y0, y1 + 1):
        px[x0, y] = (6, 9, 12, 255)
    for x in range(x0 + 1, x1 + 1):
        px[x, y1] = _mix(A_LIGHT, A_DARK, 0.55)


def led(px, x, y, col=P_HOT, core=None):
    """4x4 ember socket with a 2x2 emissive core at (x, y) — P_* only, so PULSE catches it."""
    for yy in range(y - 1, y + 3):
        for xx in range(x - 1, x + 3):
            px[xx, yy] = P_EMBER
    for yy in range(y, y + 2):
        for xx in range(x, x + 2):
            px[xx, yy] = col
    if core:
        px[x, y] = core


def tell(px, x, y, vertical=False):
    """The thin T_CYAN family tell: a 1x2 (or 2x1) cyan tick on a dark socket. ONE per block."""
    if vertical:
        px[x, y - 1] = A_DARK
        px[x, y] = T_CYAN
        px[x, y + 1] = T_CYAN
        px[x, y + 2] = A_DARK
    else:
        px[x - 1, y] = A_DARK
        px[x, y] = T_CYAN
        px[x + 1, y] = T_CYAN
        px[x + 2, y] = A_DARK


def haz_band(px, y0, y1, x0=2, x1=S - 3):
    """Diagonal hazard striping — Fission Core shell edges / Fission Casing corners ONLY."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = HAZ_Y if ((x + y) // 3) % 2 == 0 else HAZ_K


def haz_band_v(px, x0, x1, y0=2, y1=S - 3):
    """Vertical hazard band (the shell-edge variant)."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = HAZ_Y if ((x + y) // 3) % 2 == 0 else HAZ_K


def ramp_at(t, ramp=POWER_RAMP):
    """Nearest ramp colour for 0..1 (0 = ember, 1 = white-gold)."""
    i = min(len(ramp) - 1, max(0, int(round(t * (len(ramp) - 1)))))
    return ramp[i]


def radial(px, fn, cx=15.5, cy=15.5):
    """Call fn(d, ang, x, y) for every pixel; paint the colour it returns (None = leave)."""
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - cx, y - cy)
            ang = math.atan2(y - cy, x - cx)
            col = fn(d, ang, x, y)
            if col is not None:
                px[x, y] = col


# ---------------- 0.4.0 shared painters ----------------
# All take the pixel access object (``px = img.load()``) and an inclusive rectangle.

def rect(px, x0, y0, x1, y1, col):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = col


def frame(px, x0, y0, x1, y1, col):
    """One-pixel outline."""
    for x in range(x0, x1 + 1):
        px[x, y0] = col
        px[x, y1] = col
    for y in range(y0, y1 + 1):
        px[x0, y] = col
        px[x1, y] = col


def line(px, x0, y0, x1, y1, col):
    """Bresenham line, clipped to the tile."""
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        if 0 <= x0 < S and 0 <= y0 < S:
            px[x0, y0] = col
        if x0 == x1 and y0 == y1:
            return
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def front_base(name):
    """``machine_base`` for a front face that carries the status socket (see ``status_socket``):
    three rivets, the upper-left one gives way to the socket."""
    img = new_img()
    noise_fill(img, ALLOY, rng_for(name))
    bevel(img)
    rivets(img, pts=((27, 4), (4, 27), (27, 27)))
    status_socket(img.load())
    return img


def status_socket(px, x=2, y=4):
    """The dark 4x4 well behind the status lamp, with a one-pixel collar. The default position is
    the model's ``status_socket`` element (model x 13..15, y 12..14 on a north-facing front, which
    is the upper left as the player faces the block). The lamp itself is drawn by the renderer:
    bake no light here."""
    collar = _mix(A_LIGHT, A_DARK, 0.5)
    frame(px, x - 1, y - 1, x + 4, y + 4, collar)
    px[x - 1, y - 1] = A_LIGHT
    px[x + 4, y + 4] = A_DARK
    rect(px, x, y, x + 3, y + 3, SOCKET)
    for i in range(4):
        px[x + i, y] = SOCKET_SHADOW
        px[x, y + i] = SOCKET_SHADOW


def louvre(px, x0, y0, x1, y1, pitch=3, glow=None):
    """Horizontal cooling slats. Each ``pitch`` rows: a lit slat edge, the slat, then a dark gap.
    ``glow`` (a P_* colour) lights the gaps from behind — heat seen through the vent."""
    slat = ALLOY[1]
    edge = _mix(A_LIGHT, ALLOY[1], 0.35)
    gap = glow if glow else SOCKET_SHADOW
    for y in range(y0, y1 + 1):
        k = (y - y0) % pitch
        col = edge if k == 0 else (gap if k == pitch - 1 else slat)
        for x in range(x0, x1 + 1):
            px[x, y] = col
    for y in range(y0, y1 + 1):            # side returns, so the vent sits IN the plate
        px[x0, y] = A_DARK
        px[x1, y] = _mix(A_LIGHT, A_DARK, 0.55)


def flange(px, x0, y0, x1, y1, bore=SOCKET, bolts=True):
    """A bolted square pipe flange with an octagonal bore: the port mark for a side face. Square on
    purpose — the family's ports never read as an orb or a set of rings. Returns the bore box."""
    plate = ALLOY[3]
    rect(px, x0, y0, x1, y1, plate)
    for x in range(x0, x1 + 1):
        px[x, y0] = A_LIGHT
        px[x, y1] = A_DARK
    for y in range(y0, y1 + 1):
        px[x0, y] = A_LIGHT
        px[x1, y] = A_DARK
    inset = max(2, (x1 - x0 + 1) // 4)
    bx0, by0, bx1, by1 = x0 + inset, y0 + inset, x1 - inset, y1 - inset
    cut = max(1, (bx1 - bx0 + 1) // 4)
    for y in range(by0, by1 + 1):
        for x in range(bx0, bx1 + 1):
            cx = min(x - bx0, bx1 - x)
            cy = min(y - by0, by1 - y)
            if cx + cy < cut:
                continue
            px[x, y] = SOCKET_SHADOW if (y == by0 or x == bx0 or cx + cy == cut) else bore
    if bolts:
        half = _mix(A_LIGHT, A_DARK, 0.45)
        for (bx, by) in ((x0 + 1, y0 + 1), (x1 - 1, y0 + 1), (x0 + 1, y1 - 1), (x1 - 1, y1 - 1)):
            px[bx, by] = half
    return bx0, by0, bx1, by1


def pipe_ring(px, y, x0, x1, col=STEAM_W, hollow=False, vertical=False):
    """A two-pixel identification band across a pipe run (steam white, exhaust grey, heavy-water
    blue). ``hollow`` leaves only the band's ends: the "starved" read of a port ring."""
    shade = _mix(col, A_DARK, 0.45)
    for i in range(x0, x1 + 1):
        if hollow and x0 + 1 < i < x1 - 1:
            continue
        if vertical:
            px[y, i] = col
            px[y + 1, i] = shade
        else:
            px[i, y] = col
            px[i, y + 1] = shade


def plate_stack(px, x0, y0, x1, y1, pitch=3, face=None, vertical=False, lit=()):
    """A stack of thin plates seen edge-on (fuel-cell plates, laminations, fins): one bright edge,
    body, one dark gap every ``pitch`` pixels. ``lit`` lists plate indexes whose gap glows amber."""
    face = face or ALLOY[3]
    edge = _mix(A_LIGHT, face, 0.4)
    span = (x1 - x0 + 1) if vertical else (y1 - y0 + 1)
    for i in range(span):
        k = i % pitch
        idx = i // pitch
        if k == 0:
            col = edge
        elif k == pitch - 1:
            col = P_AMBER if idx in lit else A_DARK
        else:
            col = face
        if vertical:
            for y in range(y0, y1 + 1):
                px[x0 + i, y] = col
        else:
            for x in range(x0, x1 + 1):
                px[x, y0 + i] = col


def can(px, x0, y0, x1, y1, body=None, band=None, lit=False):
    """An upright cylinder seen from the side (capacitor can, canister, cask): shaded across its
    width, a cap line on top, a base shadow, an optional trim ``band`` and, with ``lit``, a
    two-pixel amber charge mark under the cap."""
    body = body or ALLOY[1]
    w = x1 - x0
    for x in range(x0, x1 + 1):
        t = (x - x0) / max(1, w)
        if t < 0.18:
            col = _mix(body, A_DARK, 0.55)
        elif t < 0.36:
            col = _mix(body, A_LIGHT, 0.55)
        elif t < 0.72:
            col = body
        else:
            col = _mix(body, A_DARK, 0.35 + 0.4 * (t - 0.72) / 0.28)
        for y in range(y0, y1 + 1):
            px[x, y] = col
    for x in range(x0, x1 + 1):
        px[x, y0] = _mix(A_LIGHT, body, 0.25)
        px[x, y1] = A_DARK
    if band:
        by = y0 + max(2, (y1 - y0) // 3)
        for x in range(x0, x1 + 1):
            px[x, by] = _mix(band, A_DARK, 0.5) if x in (x0, x1) else band
    if lit:
        mx = (x0 + x1) // 2
        px[mx, y0 + 2] = P_HOT
        px[mx + 1, y0 + 2] = P_AMBER


def rib(px, x0, y0, x1, y1, count, vertical=True):
    """``count`` raised ribs spread evenly over the rectangle (lit edge, body, shadow edge).
    Rib rows are the back-end family mark; rib COUNT is also a tier tell (1 / 2 / 3)."""
    span = (x1 - x0 + 1) if vertical else (y1 - y0 + 1)
    step = span / float(count)
    for i in range(count):
        a = max(0, min(span - 3, int(round(i * step + step / 2.0 - 1.5))))
        for k, col in enumerate((A_LIGHT, ALLOY[3], A_DARK)):
            if vertical:
                for y in range(y0, y1 + 1):
                    px[x0 + a + k, y] = col
            else:
                for x in range(x0, x1 + 1):
                    px[x, y0 + a + k] = col


def frost(px, rng, x0, y0, x1, y1, density=0.6, edge="top"):
    """Dithered rime creeping in from one edge (``top``, ``bottom``, ``left``, ``right``): cryo
    lines and a storage ring near its limit. Pale blue-white, never animated."""
    ice = [(214, 232, 244, 255), (176, 206, 228, 255), (236, 244, 250, 255)]
    w, h = x1 - x0 + 1, y1 - y0 + 1
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if edge == "top":
                t = (y - y0) / max(1, h - 1)
            elif edge == "bottom":
                t = (y1 - y) / max(1, h - 1)
            elif edge == "left":
                t = (x - x0) / max(1, w - 1)
            else:
                t = (x1 - x) / max(1, w - 1)
            if rng.random() < density * (1.0 - t) ** 1.6:
                px[x, y] = rng.choice(ice)


def trace(px, points, col=P_HOT, halo=None):
    """A polyline through ``points`` [(x, y), ...]: scope traces, circuit runs and, in RAD_LIME,
    the contamination trace marks. ``halo`` paints a dimmer pixel under each lit one."""
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        if halo:
            line(px, ax, ay + 1, bx, by + 1, halo)
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        line(px, ax, ay, bx, by, col)


def chevrons(px, x0, y0, x1, y1, direction="right", col=P_HOT, pitch=5, phase=0):
    """A row of flow chevrons pointing ``left`` or ``right`` (port direction marks). ``phase``
    shifts them, so ``pitch`` phases make a travelling strip."""
    mid = (y0 + y1) / 2.0
    for y in range(y0, y1 + 1):
        off = int(abs(y - mid))
        for x in range(x0, x1 + 1):
            k = (x - x0 - phase + (off if direction == "right" else -off)) % pitch
            if k == 0:
                px[x, y] = col


def tier_marks(px, tier, x, y, vertical=False):
    """Tier tell, shape first: 1 / 2 / 3 two-pixel bars in the tier's trim colour (alloy,
    cindersteel, arc violet) at (x, y). ``tier`` is 0, 1 or 2."""
    col = TIER_TRIM[tier]
    for i in range(tier + 1):
        if vertical:
            px[x, y + i * 3] = col
            px[x, y + i * 3 + 1] = col
        else:
            px[x + i * 3, y] = col
            px[x + i * 3 + 1, y] = col


def trip_flag(px, x, y, shown=True, w=6, h=4):
    """The mechanical trip flag window: white on red when ``shown``, a dark empty window when
    not. The ONLY place TRIP_W on TRIP_R may appear."""
    frame(px, x - 1, y - 1, x + w, y + h, A_DARK)
    if not shown:
        rect(px, x, y, x + w - 1, y + h - 1, SOCKET)
        return
    rect(px, x, y, x + w - 1, y + h - 1, TRIP_R)
    for yy in range(y + 1, y + h - 1):
        for xx in range(x + 1, x + w - 1):
            px[xx, yy] = TRIP_W


def hazard_edge(px, lit=True, y0=S - 5, y1=S - 3):
    """The bottom hazard edge of a face. Lit = the static "failing" read (stripes at full value);
    unlit = the same stripes dimmed into the alloy, so the two sprites differ in grayscale."""
    for y in range(y0, y1 + 1):
        for x in range(2, S - 2):
            on = ((x + y) // 3) % 2 == 0
            if lit:
                px[x, y] = HAZ_Y if on else HAZ_K
            else:
                px[x, y] = _mix(HAZ_Y, A_DARK, 0.78) if on else HAZ_K


def family_mark(px, family):
    """The small mark every TOP face carries, one per family, at the top-left inside the bevel."""
    if family == "fission":                       # hazard corner
        for y in range(2, 8):
            for x in range(2, 8 - (y - 2)):
                px[x, y] = HAZ_Y if ((x + y) // 2) % 2 == 0 else HAZ_K
    elif family == "steam":                       # twin ring marks
        pipe_ring(px, 4, 3, 9, STEAM_W)
        pipe_ring(px, 8, 3, 9, STEAM_X)
    elif family == "backend":                     # rib rows
        rib(px, 3, 3, 11, 8, 3)
    elif family == "storage":                     # charge tick
        rect(px, 3, 3, 4, 8, CH_OFF)
        rect(px, 3, 5, 4, 8, CH_GREEN)
    elif family == "grid":                        # bus bar (beam blocks use "beam")
        rect(px, 3, 4, 12, 5, CINDER[0])
        for x in range(3, 13):
            px[x, 3] = CINDER[2]
            px[x, 6] = CINDER[1]
    elif family == "beam":                        # lens notch
        rect(px, 3, 3, 8, 4, SOCKET)
        px[5, 5] = SOCKET
        px[6, 5] = SOCKET
        px[5, 3] = P_HOT
    elif family == "radiation":                   # band
        rect(px, 2, 4, S - 3, 5, SHIELD[2])
        for x in range(2, S - 2):
            px[x, 3] = SHIELD[1]
    else:
        raise ValueError("unknown family mark: %s" % family)


def kit_sprites():
    """The three sprites the shared lamp helper draws (white, tinted by the renderer): the lamp,
    its half-lit twin for a stalled machine with animations off, and the heat shimmer."""
    lamp = Image.new("RGBA", (8, 8), (255, 255, 255, 255))
    px = lamp.load()
    for i in range(8):
        px[i, 0] = px[0, i] = px[i, 7] = px[7, i] = (206, 206, 206, 255)
    half = lamp.copy()
    hp = half.load()
    for y in range(8):
        for x in range(8):
            if x + y >= 8:                        # lower-right half dark: reads without colour
                hp[x, y] = (44, 50, 54, 255) if 0 < x < 7 and 0 < y < 7 else (30, 34, 38, 255)
    shim = Image.new("RGBA", (S, S), CLEAR)
    sp = shim.load()
    for y in range(S):                            # sparse rising wave rows, y-tileable (period 8)
        for x in range(S):
            wave = int(round(1.5 * math.sin(2.0 * math.pi * (x + (y // 8) * 5) / 16.0)))
            k = (y + wave) % 8
            if k == 0 and (x // 2 + y // 8) % 3 != 0:
                sp[x, y] = (255, 255, 255, 255)
            elif k == 1 and (x + y // 8) % 4 == 0:
                sp[x, y] = (236, 236, 236, 255)
    save(lamp, "block", "status_lamp")
    save(half, "block", "status_lamp_half")
    save(shim, "block", "heat_shimmer")


# ---------------- palette audit (the contact sheet prints this) ----------------

def audit(img, allow_lime=False, allow_charge=False):
    """Palette facts for one tile: pixel counts by hue family and a list of findings.

    Painters blend palette colours (bevels, shading), so the audit works on hue families rather
    than exact matches; the exact checks are the cyan tell and plasma.
    """
    im = img.convert("RGBA")
    px = im.load()
    w, h = im.size
    out = {"opaque": 0, "violet": 0, "cyan_tell": 0, "plasma": 0, "lime": 0, "amber": 0,
           "red": 0, "blue": 0, "green": 0, "teal": 0, "stray": 0, "findings": []}
    stray = {}
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if not a:
                continue
            out["opaque"] += 1
            if (r, g, b) == T_CYAN[:3]:
                out["cyan_tell"] += 1
                continue
            if (r, g, b) == PLASMA[:3]:
                out["plasma"] += 1
                continue
            hh, ss, vv = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            if ss < 0.30 or vv < 0.22:
                continue                           # greys, alloy, whites, deep shadow
            if hh <= 0.028 or hh >= 0.97:
                out["red"] += 1
            elif hh <= 0.15:
                out["amber"] += 1                  # power ramp, hazard yellow, cindersteel
            elif 0.17 <= hh <= 0.30:
                out["lime"] += 1
            elif 0.30 < hh <= 0.42:
                out["green"] += 1
            elif 0.45 <= hh <= 0.54:
                out["teal"] += 1
            elif 0.54 < hh <= 0.64:
                out["blue"] += 1
            elif 0.66 <= hh <= 0.78:
                out["violet"] += 1
            else:
                out["stray"] += 1
                stray[(r, g, b)] = stray.get((r, g, b), 0) + 1
    n = max(1, out["opaque"])
    if out["violet"] > 0.08 * n:
        out["findings"].append("violet %.1f%% of the tile (max 8%%)" % (100.0 * out["violet"] / n))
    if out["plasma"]:
        out["findings"].append("%d plasma pixels (banned)" % out["plasma"])
    if out["lime"] and not allow_lime:
        out["findings"].append("%d lime pixels outside radiation art" % out["lime"])
    if out["green"] and not allow_charge:
        out["findings"].append("%d green pixels outside a charge window" % out["green"])
    if out["stray"]:
        top = sorted(stray.items(), key=lambda kv: -kv[1])[:3]
        out["findings"].append("%d pixels in no palette hue family, e.g. %s"
                               % (out["stray"], ", ".join("#%02X%02X%02X" % c for c, _n in top)))
    return out
